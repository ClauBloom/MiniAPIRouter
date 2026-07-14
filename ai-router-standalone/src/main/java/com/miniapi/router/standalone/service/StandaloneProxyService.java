package com.miniapi.router.standalone.service;

import com.miniapi.router.core.domain.*;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.spi.AgentIdentityExtractor;
import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.core.spi.LogRepository;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.TraceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 独立版代理转发服务。
 * <p>
 * 核心代理转发逻辑，处理来自控制器的代理请求：
 * 1. 将入站请求（OpenAI/Anthropic 协议）转换为统一请求格式
 * 2. 通过路由管线（RoutePipeline）选择目标 API Key
 * 3. 构建上游请求并转发
 * 4. 支持流式（SSE）和非流式两种模式
 * 5. 记录请求日志和存储 Prompt/Response 内容
 * </p>
 */
@Service
public class StandaloneProxyService {

    private static final Logger log = LoggerFactory.getLogger(StandaloneProxyService.class);
    private static final Long TENANT_ID = 1L; // 独立版固定租户 ID

    private final RoutePipeline routePipeline;       // 路由管线，负责选择目标 Key
    private final StreamProxy streamProxy;           // 流式代理，负责实际的上游请求转发
    private final ProtocolRegistry protocolRegistry; // 协议注册表，提供请求/响应转换器
    private final LogRepository logRepository;       // 日志仓储
    private final BlobStorage blobStorage;           // Blob 存储，用于存储 Prompt/Response 内容
    private final AgentIdentityExtractor agentIdentityExtractor;  // Agent 身份提取器

    public StandaloneProxyService(RoutePipeline routePipeline, StreamProxy streamProxy,
                                  ProtocolRegistry protocolRegistry, LogRepository logRepository,
                                  BlobStorage blobStorage,
                                  AgentIdentityExtractor agentIdentityExtractor) {
        this.routePipeline = routePipeline;
        this.streamProxy = streamProxy;
        this.protocolRegistry = protocolRegistry;
        this.logRepository = logRepository;
        this.blobStorage = blobStorage;
        this.agentIdentityExtractor = agentIdentityExtractor;
    }

    /**
     * 代理请求参数记录。
     *
     * @param inboundProtocol 入站协议（openai/anthropic）
     * @param rawBody         原始请求体
     * @param apiKey          客户端 API Key
     * @param httpRequest     HTTP 请求对象
     */
    public record ProxyRequest(
            String inboundProtocol,
            Map<String, Object> rawBody,
            String apiKey,
            HttpServletRequest httpRequest
    ) {}

    /**
     * 执行代理转发。
     * 根据请求是否为流式，分别走流式或非流式处理路径。
     *
     * @param proxyRequest 代理请求参数
     * @return 响应结果（流式返回 StreamingResponseBody，非流式返回转换后的响应 Map）
     */
    public Object proxy(ProxyRequest proxyRequest) {
        String traceId = TraceUtils.newTraceId();
        String requestId = TraceUtils.newRequestId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> body = proxyRequest.rawBody();
        String model = (String) body.get("model");
        if (model == null) {
            throw new RouterException("MISSING_REQUIRED_FIELD", "model is required", 400);
        }
        String inboundProtocol = proxyRequest.inboundProtocol();
        boolean stream = Boolean.TRUE.equals(body.get("stream")); // 是否流式请求

        // 将入站请求转换为统一格式
        RequestConverter requestConverter = protocolRegistry.getRequestConverter(inboundProtocol);
        UnifiedRequest unifiedReq = requestConverter.convert(body, proxyRequest.apiKey());
        unifiedReq.setInboundProtocol(inboundProtocol);

        String clientIp = getClientIp(proxyRequest.httpRequest());

        // 提取 Agent 身份信息（用于多 Agent 路由隔离）
        AgentIdentity agentIdentity = agentIdentityExtractor.extract(proxyRequest.httpRequest());
        String agentId = agentIdentity != null ? agentIdentity.getAgentId() : null;
        String agentType = agentIdentity != null ? agentIdentity.getAgentType() : null;

        // 构建路由上下文
        RouteContext routeCtx = RouteContext.builder()
                .tenantId(TENANT_ID)
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

        if (stream) {
            // 流式请求：返回 StreamingResponseBody
            return handleStream(routeCtx, unifiedReq, inboundProtocol, model,
                    requestId, traceId, clientIp, startTime, agentId, agentType);
        } else {
            // 非流式请求：执行路由并转发
            RouteResult routeResult = routePipeline.route(routeCtx);
            logRequestSummary(traceId, model, routeResult, inboundProtocol, false);
            ApiKeyConfig selectedKey = routeResult.getSelectedKey();
            // 确定上游协议
            String upstreamProtocol = selectedKey.getProtocol() != null ? selectedKey.getProtocol() : "openai";
            unifiedReq.setUpstreamProtocol(upstreamProtocol);
            unifiedReq.setModel(routeResult.resolveUpstreamModel(model));

            // 构建上游请求
            String upstreamPath = getUpstreamPath(upstreamProtocol);
            Map<String, Object> upstreamBody = requestConverter.buildUpstreamRequest(unifiedReq, upstreamProtocol);

            return handleNonStream(routeResult, inboundProtocol, upstreamPath, upstreamBody,
                    model, requestId, traceId, clientIp, startTime, agentId, agentType);
        }
    }

    /**
     * 处理非流式代理请求。
     * 转发请求到上游，处理成功和失败两种情况，并记录日志。
     *
     * @param routeResult      路由结果
     * @param inboundProtocol  入站协议
     * @param upstreamPath     上游请求路径
     * @param upstreamBody     上游请求体
     * @param model            请求模型
     * @param requestId        请求 ID
     * @param traceId          追踪 ID
     * @param clientIp         客户端 IP
     * @param startTime        请求开始时间戳
     * @return 转换后的响应
     */
    private Object handleNonStream(RouteResult routeResult, String inboundProtocol,
                                   String upstreamPath, Map<String, Object> upstreamBody, String model,
                                   String requestId, String traceId, String clientIp, long startTime,
                                   String agentId, String agentType) {
        ResponseConverter responseConverter = protocolRegistry.getResponseConverter(inboundProtocol);
        try {
            // 执行非流式代理转发
            StreamProxy.ProxyResult result = streamProxy.proxyNonStream(routeResult, inboundProtocol,
                    upstreamPath, upstreamBody, model, requestId);
            UnifiedResponse response = result.response();

            // 存储 Prompt 和 Response 内容到 BlobStorage
            String promptStorageUrl = storeBlob(traceId, "prompt", JsonUtils.toJson(upstreamBody.get("messages")));
            String responseStorageUrl = storeBlob(traceId, "response", response.getContent());

            // 记录请求日志
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            saveLog(traceId, requestId, clientIp, inboundProtocol, model,
                    result.mappedProvider(), result.apiKeyId(), routeResult.getMatchedRule().getId(),
                    response.getPromptTokens(), response.getCompletionTokens(), response.getTotalTokens(),
                    latencyMs, 0, 0, "success", promptStorageUrl, responseStorageUrl, null, null,
                    routeResult.getIntent(), agentId, agentType);

            return responseConverter.convert(response, inboundProtocol);
        } catch (RouterException e) {
            // 路由业务异常：记录失败日志并返回错误响应
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            saveLog(traceId, requestId, clientIp, inboundProtocol, model,
                    routeResult.getSelectedKey().getProvider(), null,
                    routeResult.getMatchedRule() != null ? routeResult.getMatchedRule().getId() : null,
                    0, 0, 0, latencyMs, 0, 0, "failed", null, null, e.getErrorCode(), e.getMessage(),
                    routeResult.getIntent(), agentId, agentType);
            return responseConverter.convertError(e.getErrorCode(), e.getMessage(), inboundProtocol);
        } catch (Exception e) {
            // 其他异常：记录失败日志并返回内部错误响应
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            saveLog(traceId, requestId, clientIp, inboundProtocol, model,
                    routeResult.getSelectedKey().getProvider(), null, null,
                    0, 0, 0, latencyMs, 0, 0, "failed", null, null,
                    "INTERNAL_ERROR", e.getMessage(), routeResult.getIntent(), agentId, agentType);
            return responseConverter.convertError("INTERNAL_ERROR", e.getMessage(), inboundProtocol);
        }
    }

    /**
     * 处理流式代理请求。
     * 使用虚拟线程在后台执行路由评估，同时向客户端发送 SSE 心跳保持连接。
     * 路由完成后转发上游流式响应到客户端。
     *
     * @param routeCtx         路由上下文
     * @param unifiedReq       统一请求
     * @param inboundProtocol  入站协议
     * @param model            请求模型
     * @param requestId        请求 ID
     * @param traceId          追踪 ID
     * @param clientIp         客户端 IP
     * @param startTime        请求开始时间戳
     * @param agentId          Agent 身份标识
     * @param agentType        Agent 类型
     * @return StreamingResponseBody 流式响应体
     */
    private StreamingResponseBody handleStream(RouteContext routeCtx, UnifiedRequest unifiedReq,
                                                String inboundProtocol, String model,
                                                String requestId, String traceId, String clientIp, long startTime,
                                                String agentId, String agentType) {
        StreamingResponseBody responseBody = outputStream -> {
            // 使用原子引用在虚拟线程和主线程间传递路由结果和错误
            java.util.concurrent.atomic.AtomicReference<RouteResult> routeRef = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Throwable> errRef = new java.util.concurrent.atomic.AtomicReference<>();

            // 启动虚拟线程执行路由评估（可能涉及意图分析，耗时较长）
            Thread routeThread = Thread.ofVirtual().name("route-eval-" + traceId).start(() -> {
                try {
                    routeRef.set(routePipeline.route(routeCtx));
                } catch (Throwable e) {
                    errRef.set(e);
                }
            });

            // 等待路由完成期间，发送 SSE 心跳保持连接
            byte[] keepalive = ": \n\n".getBytes(StandardCharsets.UTF_8);
            while (routeThread.isAlive()) {
                try {
                    outputStream.write(keepalive);
                    outputStream.flush();
                } catch (IOException ignored) {
                    routeThread.interrupt(); // 客户端断开，中断路由线程
                    return;
                }
                try {
                    routeThread.join(500); // 每 500ms 检查一次路由是否完成
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // 检查路由是否失败
            Throwable routeError = errRef.get();
            if (routeError != null || routeRef.get() == null) {
                String errMsg = routeError != null ? routeError.getMessage() : "routing failed";
                log.error("[Stream] Route failed: {}", errMsg);
                // 向客户端发送错误事件
                try {
                    outputStream.write(("data: {\"error\":{\"message\":\"" + errMsg + "\"}}\n\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (IOException ignored) {}
                return;
            }

            // 路由成功，开始转发流式响应
            RouteResult routeResult = routeRef.get();
            logRequestSummary(traceId, model, routeResult, inboundProtocol, true);
            ApiKeyConfig selectedKey = routeResult.getSelectedKey();
            // 确定上游协议
            String upstreamProtocol = selectedKey.getProtocol() != null ? selectedKey.getProtocol() : "openai";
            unifiedReq.setUpstreamProtocol(upstreamProtocol);
            unifiedReq.setModel(routeResult.resolveUpstreamModel(model));

            // 构建上游请求
            String upstreamPath = getUpstreamPath(upstreamProtocol);
            Map<String, Object> upstreamBody = protocolRegistry.getRequestConverter(inboundProtocol)
                    .buildUpstreamRequest(unifiedReq, upstreamProtocol);

            // 执行流式代理转发
            StreamProxy.StreamProxyContext ctx = new StreamProxy.StreamProxyContext(
                    routeResult, inboundProtocol, upstreamPath, upstreamBody, model, requestId, traceId, null);
            StreamProxy.StreamContext result = streamProxy.proxyStream(ctx, outputStream);

            // 记录请求日志
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            UsageStats stats = result.stats();
            String promptStorageUrl = storeBlob(traceId, "prompt", JsonUtils.toJson(upstreamBody.get("messages")));
            String responseStorageUrl = storeBlob(traceId, "response", result.content());

            // 根据降级次数确定状态
            String status = stats != null && stats.getFallbackCount() > 0 ? "fallback" : "success";
            saveLog(traceId, requestId, clientIp, inboundProtocol, model,
                    result.mappedProvider(), result.apiKeyId(),
                    routeResult.getMatchedRule().getId(),
                    stats != null ? stats.getPromptTokens() : 0,
                    stats != null ? stats.getCompletionTokens() : 0,
                    stats != null ? stats.getTotalTokens() : 0,
                    latencyMs, stats != null ? stats.getTtftMs() : 0,
                    stats != null ? stats.getFallbackCount() : 0,
                    status, promptStorageUrl, responseStorageUrl, null, null,
                    routeResult.getIntent(), agentId, agentType);
        };
        return responseBody;
    }

    /**
     * 记录请求摘要日志。
     *
     * @param traceId      追踪 ID
     * @param model        请求模型
     * @param routeResult  路由结果
     * @param protocol     入站协议
     * @param stream       是否流式
     */
    private void logRequestSummary(String traceId, String model, RouteResult routeResult,
                                    String protocol, boolean stream) {
        ApiKeyConfig selected = routeResult.getSelectedKey();
        log.info("[Request] trace={} model={} protocol={} stream={} intent={} → key_id={} name={} provider={} strategy={}",
                traceId, model, protocol, stream,
                routeResult.getIntent() != null ? routeResult.getIntent() : "-",
                selected.getId(), selected.getName(), selected.getProvider(), routeResult.getStrategy());
    }

    /**
     * 根据协议获取上游请求路径。
     *
     * @param protocol 协议名称
     * @return 上游请求路径
     */
    private String getUpstreamPath(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol) ? "/v1/messages" : "/v1/chat/completions";
    }

    /**
     * 将内容存储到 BlobStorage。
     * 按日期目录组织存储路径：tenant_1/YYYY/MM/DD/{type}/{traceId}.json
     *
     * @param traceId 追踪 ID
     * @param type    内容类型（prompt/response）
     * @param content 内容字符串
     * @return 存储 URL（相对路径），内容为空时返回 null
     */
    private String storeBlob(String traceId, String type, String content) {
        if (content == null || content.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        String path = String.format("tenant_1/%04d/%02d/%02d/%s/%s.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), type, traceId);
        return blobStorage.store(path, content);
    }

    /**
     * 保存请求日志到数据库。
     *
     * @param traceId          追踪 ID
     * @param requestId        请求 ID
     * @param clientIp         客户端 IP
     * @param protocol         入站协议
     * @param model            请求模型
     * @param mappedProvider   实际路由到的供应商
     * @param apiKeyId         使用的 API Key ID
     * @param routeRuleId      匹配的路由规则 ID
     * @param promptTokens     Prompt Token 数
     * @param completionTokens Completion Token 数
     * @param totalTokens      总 Token 数
     * @param latencyMs        总延迟（毫秒）
     * @param ttftMs           首字延迟（毫秒）
     * @param fallbackCount    降级次数
     * @param status           请求状态
     * @param promptUrl        Prompt 存储 URL
     * @param responseUrl      Response 存储 URL
     * @param errorCode        错误码
     * @param errorMessage     错误消息
     * @param intent           识别出的意图
     * @param agentId          Agent 身份标识
     * @param agentType        Agent 类型
     */
    private void saveLog(String traceId, String requestId, String clientIp,
                         String protocol, String model, String mappedProvider, Long apiKeyId, Long routeRuleId,
                         int promptTokens, int completionTokens, int totalTokens, int latencyMs, int ttftMs,
                         int fallbackCount, String status, String promptUrl, String responseUrl,
                         String errorCode, String errorMessage, String intent,
                         String agentId, String agentType) {
        try {
            RequestLogMeta meta = new RequestLogMeta();
            meta.setTenantId(TENANT_ID);
            meta.setTraceId(traceId);
            meta.setRequestId(requestId);
            meta.setClientIp(clientIp);
            meta.setProtocol(protocol);
            meta.setModel(model);
            meta.setMappedProvider(mappedProvider != null ? mappedProvider : "unknown");
            meta.setApiKeyId(apiKeyId);
            meta.setRouteRuleId(routeRuleId);
            meta.setIntent(intent);
            meta.setPromptTokens(promptTokens);
            meta.setCompletionTokens(completionTokens);
            meta.setTotalTokens(totalTokens);
            meta.setLatencyMs(latencyMs);
            meta.setTtftMs(ttftMs);
            meta.setStatus(status);
            meta.setFallbackCount(fallbackCount);
            meta.setErrorCode(errorCode);
            meta.setErrorMessage(errorMessage);
            meta.setPromptStorageUrl(promptUrl);
            meta.setResponseStorageUrl(responseUrl);
            meta.setAgentId(agentId);
            meta.setAgentType(agentType);
            meta.setCreatedAt(LocalDateTime.now());
            logRepository.save(meta);
        } catch (Exception e) {
            log.error("[Log] Failed to save log for trace={}: {}", traceId, e.getMessage());
        }
    }

    /**
     * 获取客户端真实 IP 地址。
     * 优先从 X-Forwarded-For 头获取（代理场景），其次使用 RemoteAddr。
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim(); // 取第一个 IP
        return request.getRemoteAddr();
    }
}
