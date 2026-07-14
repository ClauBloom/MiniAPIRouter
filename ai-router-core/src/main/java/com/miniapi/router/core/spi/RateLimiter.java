package com.miniapi.router.core.spi;

/**
 * 限流器接口（SPI 扩展点）。
 * <p>
 * 提供基于滑动窗口/固定窗口的请求限流能力，用于保护上游 API 免受超量调用。
 * 支持按任意 key（如 API Key、租户 ID、IP）进行独立限流。
 * </p>
 */
public interface RateLimiter {

    /**
     * 尝试获取一次调用许可。
     *
     * @param key           限流标识（如 API Key 或租户 ID）
     * @param limit         窗口内允许的最大调用次数
     * @param windowSeconds 时间窗口大小（秒）
     * @return true 表示允许通过，false 表示触发限流
     */
    boolean tryAcquire(String key, int limit, int windowSeconds);

    /**
     * 查询当前窗口内剩余的可用调用次数。
     *
     * @param key           限流标识
     * @param limit         窗口内允许的最大调用次数
     * @param windowSeconds 时间窗口大小（秒）
     * @return 剩余可用次数
     */
    long getRemaining(String key, int limit, int windowSeconds);
}
