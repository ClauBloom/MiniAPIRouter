package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.ApiKeyConfig;

/** Core 管理门面，避免宿主操作路由缓存或上游传输内部实现。 */
public interface RouterCoreManagement {

    HealthCheckResult checkHealth(ApiKeyConfig config);

    void invalidateRoutingState();

    record HealthCheckResult(String status, String detail) {}
}
