package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.ApiKeyConfig;

/**
 * 上游 API Key 健康检查器接口（SPI 扩展点）。
 * <p>
 * 负责对上游 API 提供方的 Key 进行健康探测，判断其是否可用。
 * 当 Key 不可用时可将其标记为故障（down），恢复后标记为健康。
 * 路由选择时会避开故障 Key。
 * </p>
 */
public interface HealthChecker {

    /** 对指定 API Key 配置执行一次健康检查 */
    void check(ApiKeyConfig config);

    /** 获取指定 Key 的当前健康状态 */
    String getStatus(Long keyId);

    /** 将指定 Key 标记为故障状态，并记录原因 */
    void markDown(Long keyId, String reason);

    /** 将指定 Key 标记为健康状态 */
    void markHealthy(Long keyId);
}
