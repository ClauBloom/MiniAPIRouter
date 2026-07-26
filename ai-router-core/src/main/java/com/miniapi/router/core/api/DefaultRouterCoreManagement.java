package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.routing.FailureTracker;
import com.miniapi.router.core.routing.SessionRouteMemory;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.spi.UpstreamClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Core 管理能力的进程内默认实现。 */
@Component
public class DefaultRouterCoreManagement implements RouterCoreManagement {

    private final UpstreamClient upstreamClient;
    private final FailureTracker failureTracker;
    private final SessionRouteMemory sessionRouteMemory;
    private final UpstreamCooldownTracker cooldownTracker;

    public DefaultRouterCoreManagement(UpstreamClient upstreamClient, FailureTracker failureTracker,
                                       SessionRouteMemory sessionRouteMemory,
                                       UpstreamCooldownTracker cooldownTracker) {
        this.upstreamClient = upstreamClient;
        this.failureTracker = failureTracker;
        this.sessionRouteMemory = sessionRouteMemory;
        this.cooldownTracker = cooldownTracker;
    }

    @Override
    public HealthCheckResult checkHealth(ApiKeyConfig config) {
        Map<String, String> mapping = config.getModelMapping();
        if (mapping == null || mapping.isEmpty()) {
            return new HealthCheckResult("unhealthy", "未配置模型，无法检测");
        }
        String protocol = config.getProtocol() != null ? config.getProtocol() : "openai";
        String path = "anthropic".equalsIgnoreCase(protocol) ? "/v1/messages" : "/v1/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", mapping.values().iterator().next());
        body.put("max_tokens", 5);
        body.put("messages", List.of(Map.of("role", "user", "content", "Hi")));
        if (!"anthropic".equalsIgnoreCase(protocol)) body.put("stream", false);
        try {
            UpstreamClient.Response response = upstreamClient.call(config, path, body);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new HealthCheckResult("healthy", "HTTP " + response.statusCode());
            }
            return new HealthCheckResult("unhealthy", "HTTP " + response.statusCode() + ": "
                    + truncate(response.body(), 200));
        } catch (Exception exception) {
            return new HealthCheckResult("unhealthy", truncate(exception.getMessage(), 200));
        }
    }

    @Override
    public void invalidateRoutingState() {
        failureTracker.clearAll();
        sessionRouteMemory.clearAll();
        /* 配置变更后 Key 可能已被修复/替换，冷却状态一并清除 */
        cooldownTracker.clearAll();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
