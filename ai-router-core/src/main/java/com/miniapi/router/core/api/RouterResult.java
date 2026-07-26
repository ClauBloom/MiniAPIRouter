package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.UsageStats;

import java.util.Map;

/** Core 完整执行后的宿主可观察结果，用于响应、计费和审计。 */
public record RouterResult(
        Map<String, Object> responseBody,
        String traceId,
        String requestId,
        String protocol,
        String model,
        String mappedProvider,
        Long apiKeyId,
        Long routeRuleId,
        String intent,
        UsageStats usage,
        int fallbackCount,
        String status,
        String promptContent,
        String responseContent,
        String errorCode,
        String errorMessage
) {
    public boolean succeeded() {
        return apiKeyId != null && errorCode == null;
    }
}
