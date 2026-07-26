package com.miniapi.router.saas.service;

import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterRequest;
import com.miniapi.router.core.api.RouterResult;
import com.miniapi.router.core.domain.AgentIdentity;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.AgentIdentityExtractor;
import com.miniapi.router.core.spi.EventPublisher;
import com.miniapi.router.core.spi.RateLimiter;
import com.miniapi.router.core.util.TraceUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.event.LogPersistEvent;
import com.miniapi.router.saas.mapper.TenantMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SaaS 宿主适配器：只处理认证后租户能力、配额和审计；代理领域流程完全委托给 RouterCore。
 */
@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final RouterCore routerCore;
    private final TenantMapper tenantMapper;
    private final RateLimiter rateLimiter;
    private final EventPublisher eventPublisher;
    private final AgentIdentityExtractor agentIdentityExtractor;

    public ProxyService(RouterCore routerCore, TenantMapper tenantMapper, RateLimiter rateLimiter,
                        EventPublisher eventPublisher, AgentIdentityExtractor agentIdentityExtractor) {
        this.routerCore = routerCore;
        this.tenantMapper = tenantMapper;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.agentIdentityExtractor = agentIdentityExtractor;
    }

    public record ProxyRequest(
            String inboundProtocol,
            Map<String, Object> rawBody,
            String apiKey,
            HttpServletRequest httpRequest
    ) {}

    private record ProxyLogContext(
            Long tenantId, Long userId, String clientIp, String agentId, String agentType, long startedAt
    ) {
        int latencyMs() {
            return (int) (System.currentTimeMillis() - startedAt);
        }
    }

    private record ProxyLogOutcome(RouterResult result) {}

    public Object proxy(ProxyRequest request) {
        Long tenantId = TenantContext.getTenantId();
        AgentIdentity identity = agentIdentityExtractor.extract(request.httpRequest());
        checkQuota(tenantId, identity);

        String traceId = TraceUtils.newTraceId();
        String requestId = TraceUtils.newRequestId();
        TenantContext.setTraceId(traceId);
        ProxyLogContext logContext = new ProxyLogContext(
                tenantId, TenantContext.getUserId(), clientIp(request.httpRequest()),
                identity != null ? identity.getAgentId() : null,
                identity != null ? identity.getAgentType() : null,
                System.currentTimeMillis());
        RouterRequest coreRequest = new RouterRequest(
                tenantId, request.inboundProtocol(), request.rawBody(), request.apiKey(),
                logContext.clientIp(), identity, traceId, requestId);

        if (Boolean.TRUE.equals(request.rawBody().get("stream"))) {
            return (StreamingResponseBody) output -> finish(
                    routerCore.proxyStream(coreRequest, output), logContext);
        }
        RouterResult result = routerCore.proxy(coreRequest);
        finish(result, logContext);
        return result.responseBody();
    }

    private void finish(RouterResult result, ProxyLogContext context) {
        publishLog(context, new ProxyLogOutcome(result));
        UsageStats usage = result.usage();
        if (result.succeeded() && usage != null && usage.getTotalTokens() > 0) {
            deductQuota(context.tenantId(), usage.getTotalTokens());
        }
    }

    private void publishLog(ProxyLogContext context, ProxyLogOutcome outcome) {
        RouterResult result = outcome.result();
        UsageStats usage = result.usage();
        try {
            eventPublisher.publishLogEvent(new LogPersistEvent(
                    context.tenantId(), context.userId(), result.traceId(), result.requestId(), context.clientIp(),
                    result.protocol(), result.model(), result.mappedProvider(), result.apiKeyId(),
                    result.routeRuleId(), result.intent(), tokens(usage, TokenType.PROMPT),
                    tokens(usage, TokenType.COMPLETION), tokens(usage, TokenType.TOTAL),
                    context.latencyMs(), usage != null ? usage.getTtftMs() : 0, result.fallbackCount(),
                    result.status(), null, null, result.errorCode(), result.errorMessage(),
                    result.promptContent(), result.responseContent(), context.agentId(), context.agentType(),
                    LocalDateTime.now()));
        } catch (Exception exception) {
            log.warn("[LogPublish] Failed to publish log event for trace={}: {}",
                    result.traceId(), exception.getMessage());
        }
    }

    private int tokens(UsageStats usage, TokenType type) {
        if (usage == null) return 0;
        return switch (type) {
            case PROMPT -> usage.getPromptTokens();
            case COMPLETION -> usage.getCompletionTokens();
            case TOTAL -> usage.getTotalTokens();
        };
    }

    private void checkQuota(Long tenantId, AgentIdentity identity) {
        if (tenantId == null || tenantId == 0) return;
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) return;
        if (tenant.getStatus() != null && tenant.getStatus() == 0) {
            throw new RouterException("TENANT_DISABLED", "租户已禁用", 403);
        }
        if (tenant.getExpiresAt() != null && tenant.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RouterException("TENANT_EXPIRED", "租户已过期", 403);
        }
        if (tenant.getQuotaLimit() != null && tenant.getQuotaLimit() > 0
                && tenant.getQuotaUsed() != null && tenant.getQuotaUsed() >= tenant.getQuotaLimit()) {
            throw new RouterException("QUOTA_EXCEEDED",
                    "配额已耗尽，已使用 " + tenant.getQuotaUsed() + " / " + tenant.getQuotaLimit() + " Token", 403);
        }
        if (tenant.getMaxRps() != null && tenant.getMaxRps() > 0) {
            String key = "tenant:" + tenantId;
            if (identity != null && identity.hasIdentity()) key += ":agent:" + identity.toSessionKey();
            if (!rateLimiter.tryAcquire(key, tenant.getMaxRps(), 1)) {
                throw new RouterException("RATE_LIMITED",
                        "请求频率超出限制 (max_rps=" + tenant.getMaxRps() + ")", 429);
            }
        }
    }

    private void deductQuota(Long tenantId, long tokens) {
        if (tenantId == null || tenantId == 0 || tokens <= 0) return;
        try {
            tenantMapper.addQuotaUsed(tenantId, tokens);
        } catch (Exception exception) {
            log.warn("[Quota] Failed to deduct quota for tenant={}: {}", tenantId, exception.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isEmpty()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    private enum TokenType { PROMPT, COMPLETION, TOTAL }
}
