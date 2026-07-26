package com.miniapi.router.standalone.service;

import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterRequest;
import com.miniapi.router.core.api.RouterResult;
import com.miniapi.router.core.domain.AgentIdentity;
import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.spi.AgentIdentityExtractor;
import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.core.spi.LogRepository;
import com.miniapi.router.core.util.TraceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standalone 宿主适配器：提供固定租户、Servlet 元数据及日志存储，代理流程由 RouterCore 完成。
 */
@Service
public class StandaloneProxyService {

    private static final Logger log = LoggerFactory.getLogger(StandaloneProxyService.class);
    private static final Long TENANT_ID = 1L;

    private final RouterCore routerCore;
    private final LogRepository logRepository;
    private final BlobStorage blobStorage;
    private final AgentIdentityExtractor agentIdentityExtractor;

    public StandaloneProxyService(RouterCore routerCore, LogRepository logRepository,
                                  BlobStorage blobStorage,
                                  AgentIdentityExtractor agentIdentityExtractor) {
        this.routerCore = routerCore;
        this.logRepository = logRepository;
        this.blobStorage = blobStorage;
        this.agentIdentityExtractor = agentIdentityExtractor;
    }

    public record ProxyRequest(
            String inboundProtocol,
            Map<String, Object> rawBody,
            String apiKey,
            HttpServletRequest httpRequest
    ) {}

    public Object proxy(ProxyRequest request) {
        String traceId = TraceUtils.newTraceId();
        String requestId = TraceUtils.newRequestId();
        long startedAt = System.currentTimeMillis();
        AgentIdentity identity = agentIdentityExtractor.extract(request.httpRequest());
        String clientIp = clientIp(request.httpRequest());
        RouterRequest coreRequest = new RouterRequest(
                TENANT_ID, request.inboundProtocol(), request.rawBody(), request.apiKey(),
                clientIp, identity, traceId, requestId);

        if (Boolean.TRUE.equals(request.rawBody().get("stream"))) {
            return (StreamingResponseBody) output -> saveResult(
                    routerCore.proxyStream(coreRequest, output), identity, clientIp, startedAt);
        }
        RouterResult result = routerCore.proxy(coreRequest);
        saveResult(result, identity, clientIp, startedAt);
        return result.responseBody();
    }

    private void saveResult(RouterResult result, AgentIdentity identity, String clientIp, long startedAt) {
        try {
            String promptUrl = storeBlob(result.traceId(), "prompt", result.promptContent());
            String responseUrl = storeBlob(result.traceId(), "response", result.responseContent());
            UsageStats usage = result.usage();
            RequestLogMeta meta = new RequestLogMeta();
            meta.setTenantId(TENANT_ID);
            meta.setTraceId(result.traceId());
            meta.setRequestId(result.requestId());
            meta.setClientIp(clientIp);
            meta.setProtocol(result.protocol());
            meta.setModel(result.model());
            meta.setMappedProvider(result.mappedProvider() != null ? result.mappedProvider() : "unknown");
            meta.setApiKeyId(result.apiKeyId());
            meta.setRouteRuleId(result.routeRuleId());
            meta.setIntent(result.intent());
            meta.setPromptTokens(usage != null ? usage.getPromptTokens() : 0);
            meta.setCompletionTokens(usage != null ? usage.getCompletionTokens() : 0);
            meta.setTotalTokens(usage != null ? usage.getTotalTokens() : 0);
            meta.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
            meta.setTtftMs(usage != null ? usage.getTtftMs() : 0);
            meta.setStatus(result.status());
            meta.setFallbackCount(result.fallbackCount());
            meta.setErrorCode(result.errorCode());
            meta.setErrorMessage(result.errorMessage());
            meta.setPromptStorageUrl(promptUrl);
            meta.setResponseStorageUrl(responseUrl);
            meta.setAgentId(identity != null ? identity.getAgentId() : null);
            meta.setAgentType(identity != null ? identity.getAgentType() : null);
            meta.setCreatedAt(LocalDateTime.now());
            logRepository.save(meta);
        } catch (Exception exception) {
            log.error("[Log] Failed to save log for trace={}: {}", result.traceId(), exception.getMessage());
        }
    }

    private String storeBlob(String traceId, String type, String content) {
        if (content == null || content.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        String path = String.format("tenant_1/%04d/%02d/%02d/%s/%s.json",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), type, traceId);
        return blobStorage.store(path, content);
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isEmpty()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
