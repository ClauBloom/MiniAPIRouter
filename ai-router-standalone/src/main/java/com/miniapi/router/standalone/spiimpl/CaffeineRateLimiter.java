package com.miniapi.router.standalone.spiimpl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.spi.RateLimiter;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的本地限流器实现。
 * <p>
 * 实现 RateLimiter SPI 接口，使用 Caffeine 缓存作为计数器存储。
 * 采用固定窗口算法，按时间窗口统计请求次数进行限流。
 * 计数器在写入后 60 秒自动过期。
 * </p>
 */
@Component
public class CaffeineRateLimiter implements RateLimiter {

    private final Cache<String, long[]> counters; // 限流计数器缓存，值为 long[1] 数组（可变引用）

    public CaffeineRateLimiter() {
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS) // 计数器 60 秒后过期
                .maximumSize(10000) // 最大计数器数量
                .build();
    }

    /**
     * 尝试获取限流令牌。
     * 使用固定窗口算法，按 key 和时间窗口统计请求数。
     *
     * @param key           限流键
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return true 表示未超限可以放行，false 表示已被限流
     */
    @Override
    public synchronized boolean tryAcquire(String key, int limit, int windowSeconds) {
        // 构建缓存键：限流键 + 时间窗口编号
        String cacheKey = key + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
        long[] count = counters.getIfPresent(cacheKey);
        if (count == null) {
            count = new long[]{0};
            counters.put(cacheKey, count);
        }
        count[0]++;
        return count[0] <= limit;
    }

    /**
     * 获取当前窗口内的剩余可用请求数。
     *
     * @param key           限流键
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return 剩余可用请求数
     */
    @Override
    public long getRemaining(String key, int limit, int windowSeconds) {
        String cacheKey = key + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
        long[] count = counters.getIfPresent(cacheKey);
        long current = count != null ? count[0] : 0;
        return Math.max(0, limit - current);
    }
}
