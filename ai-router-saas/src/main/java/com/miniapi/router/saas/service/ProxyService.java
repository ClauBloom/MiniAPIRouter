package com.miniapi.router.saas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniapi.router.core.domain.*;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.spi.*;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.streaming.SseEventEmitter;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.TraceUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.event.LogPersistEvent;
import com.miniapi.router.saas.mapper.TenantMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 代理服务
 * <p>
 * 核心代理服务，负责将客户端的 AI 请求路由到上游提供商并返回响应。
 * 支持流式和非流式两种请求模式，以及多种 AI 协议（OpenAI、Anthropic 等）。
 * </p>
 * <p>
 * 主要流程：
 * <ol>
 *   <li>生成追踪ID，设置租户上下文</li>
 *   <li>检查租户配额和速率限制</li>
 *   <li>将入站请求转换为统一格式</li>
 *   <li>通过路由管道选择最佳上游 API Key</li>
 *   <li>将请求转发到上游提供商</li>
 *   <li>记录请求日志和扣减配额</li>
 * </ol>
 * </p>
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final RoutePipeline routePipeline;        // 路由管道，负责选择上游 API Key
    private final StreamProxy streamProxy;            // 流式代理，负责与上游通信
    private final ProtocolRegistry protocolRegistry;  // 协议注册表，管理协议转换器
    private final ApiKeyConfigRepository keyRepository;  // API Key 配置仓库
    private final TenantMapper tenantMapper;          // 租户 Mapper，用于配额管理
    private final RateLimiter rateLimiter;            // 速率限制器
    private final EventPublisher eventPublisher;      // 事件发布器，用于发布日志事件
    private final AgentIdentityExtractor agentIdentityExtractor;  // Agent 身份提取器

    public ProxyService(RoutePipeline routePipeline, StreamProxy streamProxy,
                        ProtocolRegistry protocolRegistry,
                        ApiKeyConfigRepository keyRepository,
                        TenantMapper tenantMapper, RateLimiter rateLimiter,
                        EventPublisher eventPublisher,
                        AgentIdentityExtractor agentIdentityExtractor) {
        this.routePipeline = routePipeline;
        this.streamProxy = streamProxy;
        this.protocolRegistry = protocolRegistry;
        this.keyRepository = keyRepository;
        this.tenantMapper = tenantMapper;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.agentIdentityExtractor = agentIdentityExtractor;
    }

    /**
     * 代理请求记录
     * <p>
     * 封装代理请求的入站信息。
     * </p>
     *
     * @param inboundProtocol 入站协议（如 openai、anthropic）
     * @param rawBody         原始请求体
     * @param apiKey          API Key
     * @param httpRequest     HTTP 请求对象
     */
    public record ProxyRequest(
            String inboundProtocol,
            Map<String, Object> rawBody,
            String apiKey,
            HttpServletRequest httpRequest
    ) {}

    /**
     * 执行代理请求
     * <p>
     * 核心代理方法，处理客户端请求并返回响应。根据请求中的 stream 参数决定走流式或非流式处理路径。
     * </p>
     *
     * @param proxyRequest 代理请求
     * @return 响应对象（流式时返回 StreamingResponseBody，非流式时返回转换后的响应体）
     * @throws RouterException 当缺少 model 字段、配额不足、租户禁用/过期或速率超限时抛出
     */
    public Object proxy(ProxyRequest proxyRequest) {
        // 生成追踪ID并设置到租户上下文
        String traceId = TraceUtils.newTraceId();
        TenantContext.setTraceId(traceId);
        String requestId = TraceUtils.newRequestId();
        long startTime = System.currentTimeMillis();

        // 解析请求体，提取模型名称
        Map<String, Object> body = proxyRequest.rawBody();
        String model = (String) body.get("model");
        if (model == null) {
            throw new RouterException("MISSING_REQUIRED_FIELD", "model is required", 400);
        }
        String inboundProtocol = proxyRequest.inboundProtocol();
        boolean stream = Boolean.TRUE.equals(body.get("stream"));

        // 将入站请求转换为统一格式
        RequestConverter requestConverter = protocolRegistry.getRequestConverter(inboundProtocol);
        UnifiedRequest unifiedReq = requestConverter.convert(body, proxyRequest.apiKey());
        unifiedReq.setInboundProtocol(inboundProtocol);

        Long tenantId = TenantContext.getTenantId();
        String clientIp = getClientIp(proxyRequest.httpRequest());

        // 提取 Agent 身份信息（用于多 Agent 路由隔离）
        AgentIdentity agentIdentity = agentIdentityExtractor.extract(proxyRequest.httpRequest());
        String agentId = agentIdentity != null ? agentIdentity.getAgentId() : null;
        String agentType = agentIdentity != null ? agentIdentity.getAgentType() : null;

        // 检查租户配额、状态和速率限制（含 Agent 级别限流）
        checkQuota(tenantId, agentIdentity);

        // 构建路由上下文
        RouteContext routeCtx = RouteContext.builder()
                .tenantId(tenantId)
                .traceId(traceId)
                .requestId(requestId)
                .clientIp(clientIp)
                .inboundProtocol(inboundProtocol)
                .model(model)
                .messages(unifiedReq.getMessages())
                .systemPrompt(unifiedReq.getSystemPrompt())
                .parameters(unifiedReq.getExtraParams())
                .stream(stream)
                .agentIdentity(agentIdentity)
                .build();

        // 执行路由，选择最佳上游 API Key
        RouteResult routeResult = routePipeline.route(routeCtx);
        ApiKeyConfig selectedKey = routeResult.getSelectedKey();
        // 确定上游协议和模型
        String upstreamProtocol = selectedKey.getProtocol() != null ? selectedKey.getProtocol() : "openai";
        unifiedReq.setUpstreamProtocol(upstreamProtocol);
        String upstreamModel = routeResult.getSelectedModel() != null
                ? routeResult.getSelectedModel() : routeResult.resolveUpstreamModel(model);
        unifiedReq.setModel(upstreamModel);

        // 构建上游请求路径和请求体
        String upstreamPath = getUpstreamPath(upstreamProtocol);
        Map<String, Object> upstreamBody = requestConverter.buildUpstreamRequest(unifiedReq, upstreamProtocol);

        // 根据是否流式选择不同的处理路径
        if (stream) {
            return handleStream(proxyRequest, routeResult, inboundProtocol, upstreamPath, upstreamBody,
                    model, requestId, traceId, tenantId, clientIp, startTime, agentId, agentType);
        } else {
            return handleNonStream(proxyRequest, routeResult, inboundProtocol, upstreamPath, upstreamBody,
                    model, requestId, traceId, tenantId, clientIp, startTime, agentId, agentType);
        }
    }

    /**
     * 处理非流式请求
     * <p>
     * 将请求同步转发到上游提供商，等待完整响应后返回。
     * 同时记录请求日志和扣减配额。
     * </p>
     *
     * @param proxyRequest     代理请求
     * @param routeResult      路由结果
     * @param inboundProtocol  入站协议
     * @param upstreamPath     上游请求路径
     * @param upstreamBody     上游请求体
     * @param model            模型名称
     * @param requestId        请求ID
     * @param traceId          追踪ID
     * @param tenantId         租户ID
     * @param clientIp         客户端IP
     * @param startTime        请求开始时间戳
     * @return 转换后的响应对象
     */
    private Object handleNonStream(ProxyRequest proxyRequest, RouteResult routeResult, String inboundProtocol,
                                   String upstreamPath, Map<String, Object> upstreamBody, String model,
                                   String requestId, String traceId, Long tenantId, String clientIp, long startTime,
                                   String agentId, String agentType) {
        ResponseConverter responseConverter = protocolRegistry.getResponseConverter(inboundProtocol);
        try {
            // 执行非流式代理请求
            StreamProxy.ProxyResult result = streamProxy.proxyNonStream(routeResult, inboundProtocol,
                    upstreamPath, upstreamBody, model, requestId);
            UnifiedResponse response = result.response();

            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            int totalTokens = response.getTotalTokens();
            String promptContent = JsonUtils.toJson(upstreamBody.get("messages"));
            String responseContent = response.getContent();

            // 发布成功日志事件
            publishLog(tenantId, traceId, requestId, clientIp, inboundProtocol, model,
                    result.mappedProvider(), result.apiKeyId(), routeResult.getMatchedRule().getId(),
                    response.getPromptTokens(), response.getCompletionTokens(), totalTokens,
                    latencyMs, 0, 0, "success", promptContent, responseContent, null, null,
                    routeResult.getIntent(), agentId, agentType);

            // 扣减配额
            deductQuota(tenantId, totalTokens);

            return responseConverter.convert(response, inboundProtocol);
        } catch (RouterException e) {
            // 业务异常：记录失败日志并返回错误响应
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            publishLog(tenantId, traceId, requestId, clientIp, inboundProtocol, model,
                    routeResult.getSelectedKey().getProvider(), null,
                    routeResult.getMatchedRule() != null ? routeResult.getMatchedRule().getId() : null,
                    0, 0, 0, latencyMs, 0, 0, "failed", null, null, e.getErrorCode(), e.getMessage(),
                    routeResult.getIntent(), agentId, agentType);
            return responseConverter.convertError(e.getErrorCode(), e.getMessage(), inboundProtocol);
        } catch (Exception e) {
            // 未知异常：记录失败日志并返回内部错误响应
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            publishLog(tenantId, traceId, requestId, clientIp, inboundProtocol, model,
                    routeResult.getSelectedKey().getProvider(), null, null,
                    0, 0, 0, latencyMs, 0, 0, "failed", null, null,
                    "INTERNAL_ERROR", e.getMessage(), routeResult.getIntent(), agentId, agentType);
            return responseConverter.convertError("INTERNAL_ERROR", e.getMessage(), inboundProtocol);
        }
    }

    /**
     * 处理流式请求
     * <p>
     * 将请求以流式方式转发到上游提供商，通过 StreamingResponseBody 实现流式响应。
     * 在流式传输完成后记录请求日志和扣减配额。
     * </p>
     *
     * @param proxyRequest     代理请求
     * @param routeResult      路由结果
     * @param inboundProtocol  入站协议
     * @param upstreamPath     上游请求路径
     * @param upstreamBody     上游请求体
     * @param model            模型名称
     * @param requestId        请求ID
     * @param traceId          追踪ID
     * @param tenantId         租户ID
     * @param clientIp         客户端IP
     * @param startTime        请求开始时间戳
     * @param agentId          Agent 身份标识
     * @param agentType        Agent 类型
     * @return StreamingResponseBody 流式响应体
     */
    private StreamingResponseBody handleStream(ProxyRequest proxyRequest, RouteResult routeResult, String inboundProtocol,
                                                String upstreamPath, Map<String, Object> upstreamBody, String model,
                                                String requestId, String traceId, Long tenantId, String clientIp,
                                                long startTime, String agentId, String agentType) {
        StreamingResponseBody responseBody = outputStream -> {
            // 执行流式代理请求
            StreamProxy.StreamProxyContext ctx = new StreamProxy.StreamProxyContext(
                    routeResult, inboundProtocol, upstreamPath, upstreamBody, model, requestId, traceId, null);
            StreamProxy.StreamContext result = streamProxy.proxyStream(ctx, outputStream);

            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            UsageStats stats = result.stats();
            String promptContent = JsonUtils.toJson(upstreamBody.get("messages"));
            String responseContent = result.content();

            // 根据是否有故障转移确定状态
            String status = stats != null && stats.getFallbackCount() > 0 ? "fallback" : "success";
            publishLog(tenantId, traceId, requestId, clientIp, inboundProtocol, model,
                    result.mappedProvider(), result.apiKeyId(),
                    routeResult.getMatchedRule().getId(),
                    stats != null ? stats.getPromptTokens() : 0,
                    stats != null ? stats.getCompletionTokens() : 0,
                    stats != null ? stats.getTotalTokens() : 0,
                    latencyMs, stats != null ? stats.getTtftMs() : 0,
                    stats != null ? stats.getFallbackCount() : 0,
                    status, promptContent, responseContent, null, null,
                    routeResult.getIntent(), agentId, agentType);

            // 仅在成功状态下扣减配额
            if (stats != null && stats.getTotalTokens() > 0 && "success".equals(status)) {
                deductQuota(tenantId, stats.getTotalTokens());
            }
        };
        return responseBody;
    }

    /**
     * 根据协议获取上游请求路径
     *
     * @param protocol 协议类型
     * @return 上游 API 路径
     */
    private String getUpstreamPath(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol) ? "/v1/messages" : "/v1/chat/completions";
    }

    /**
     * 发布日志事件
     * <p>
     * 构建日志持久化事件并通过事件发布器异步发布。
     * 发布失败时仅记录警告日志，不影响主流程。
     * </p>
     *
     * @param tenantId         租户ID
     * @param traceId          追踪ID
     * @param requestId        请求ID
     * @param clientIp         客户端IP
     * @param protocol         协议
     * @param model            模型
     * @param mappedProvider   映射的提供商
     * @param apiKeyId         API Key ID
     * @param routeRuleId      路由规则ID
     * @param promptTokens     Prompt Token 数
     * @param completionTokens Completion Token 数
     * @param totalTokens      总 Token 数
     * @param latencyMs        延迟（毫秒）
     * @param ttftMs           首字延迟（毫秒）
     * @param fallbackCount    故障转移次数
     * @param status           状态
     * @param promptContent    Prompt 内容
     * @param responseContent  Response 内容
     * @param errorCode        错误码
     * @param errorMessage     错误信息
     * @param intent           意图
     */
    private void publishLog(Long tenantId, String traceId, String requestId, String clientIp,
                            String protocol, String model, String mappedProvider, Long apiKeyId, Long routeRuleId,
                            int promptTokens, int completionTokens, int totalTokens, int latencyMs, int ttftMs,
                            int fallbackCount, String status, String promptContent, String responseContent,
                            String errorCode, String errorMessage, String intent,
                            String agentId, String agentType) {
        try {
            LogPersistEvent event = new LogPersistEvent(
                    tenantId, TenantContext.getUserId(), traceId, requestId, clientIp,
                    protocol, model, mappedProvider, apiKeyId, routeRuleId, intent,
                    promptTokens, completionTokens, totalTokens, latencyMs, ttftMs,
                    fallbackCount, status, null, null, errorCode, errorMessage,
                    promptContent, responseContent, agentId, agentType, LocalDateTime.now()
            );
            eventPublisher.publishLogEvent(event);
        } catch (Exception e) {
            // 日志发布失败不影响主流程
            log.warn("[LogPublish] Failed to publish log event for trace={}: {}", traceId, e.getMessage());
        }
    }

    /**
     * 检查租户配额和速率限制
     * <p>
     * 依次检查：租户状态（是否启用）、租户过期时间、配额使用量、每秒请求数限制。
     * 当存在 Agent 身份时，速率限制键包含 Agent ID，实现 Agent 级别独立限流。
     * 任一检查不通过则抛出对应的异常。
     * </p>
     *
     * @param tenantId      租户ID
     * @param agentIdentity Agent 身份信息（可为 null）
     * @throws RouterException 当租户禁用、过期、配额耗尽或速率超限时抛出
     */
    private void checkQuota(Long tenantId, AgentIdentity agentIdentity) {
        if (tenantId == null || tenantId == 0) return;
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) return;
        // 检查租户是否被禁用
        if (tenant.getStatus() != null && tenant.getStatus() == 0) {
            throw new RouterException("TENANT_DISABLED", "租户已禁用", 403);
        }
        // 检查租户是否已过期
        if (tenant.getExpiresAt() != null && tenant.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RouterException("TENANT_EXPIRED", "租户已过期", 403);
        }
        // 检查配额是否已耗尽
        if (tenant.getQuotaLimit() != null && tenant.getQuotaLimit() > 0
                && tenant.getQuotaUsed() != null && tenant.getQuotaUsed() >= tenant.getQuotaLimit()) {
            throw new RouterException("QUOTA_EXCEEDED",
                    "配额已耗尽，已使用 " + tenant.getQuotaUsed() + " / " + tenant.getQuotaLimit() + " Token", 403);
        }
        // 检查每秒请求数限制（Agent 级别独立限流）
        if (tenant.getMaxRps() != null && tenant.getMaxRps() > 0) {
            String rateKey = "tenant:" + tenantId;
            if (agentIdentity != null && agentIdentity.hasIdentity()) {
                rateKey += ":agent:" + agentIdentity.toSessionKey();
            }
            if (!rateLimiter.tryAcquire(rateKey, tenant.getMaxRps(), 1)) {
                throw new RouterException("RATE_LIMITED",
                        "请求频率超出限制 (max_rps=" + tenant.getMaxRps() + ")", 429);
            }
        }
    }

    /**
     * 扣减租户配额
     * <p>
     * 原子性地增加租户的已使用 Token 数量。
     * 扣减失败时仅记录警告日志，不影响主流程。
     * </p>
     *
     * @param tenantId    租户ID
     * @param totalTokens 要扣减的 Token 数量
     */
    private void deductQuota(Long tenantId, long totalTokens) {
        if (tenantId == null || tenantId == 0 || totalTokens <= 0) return;
        try {
            tenantMapper.addQuotaUsed(tenantId, totalTokens);
        } catch (Exception e) {
            // 配额扣减失败不影响主流程
            log.warn("[Quota] Failed to deduct quota for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    /**
     * 获取客户端真实IP地址
     * <p>
     * 优先从 X-Forwarded-For 请求头获取（支持代理转发场景），取第一个IP；
     * 若不存在则使用请求的远程地址。
     * </p>
     *
     * @param request HTTP 请求对象
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        // 优先从 X-Forwarded-For 获取（经过代理时由代理设置）
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
