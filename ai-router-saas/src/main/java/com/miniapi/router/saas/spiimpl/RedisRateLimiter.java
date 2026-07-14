package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 速率限制器
 * <p>
 * 实现 {@link RateLimiter} SPI 接口，基于 Redis 的计数器实现固定窗口速率限制。
 * 使用 Redis 的 INCR 命令原子递增计数器，首次请求时设置过期时间作为窗口。
 * </p>
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;  // Redis 字符串模板

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 尝试获取访问许可
     * <p>
     * 使用 Redis 计数器实现固定窗口限流：
     * 对每个限流键维护一个计数器，首次请求时设置窗口过期时间，
     * 窗口内每次请求递增计数器，超过限制则拒绝。
     * </p>
     *
     * @param key           限流键
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口时长（秒）
     * @return true 表示获取成功，false 表示被限流
     */
    @Override
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        String redisKey = "rate_limit:" + key;
        // 原子递增计数器
        Long count = redis.opsForValue().increment(redisKey);
        // 首次请求时设置过期时间（限流窗口）
        if (count != null && count == 1) {
            redis.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= limit;
    }

    /**
     * 获取当前窗口内的剩余可用请求数
     *
     * @param key           限流键
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口时长（秒）
     * @return 剩余可用请求数（最小为 0）
     */
    @Override
    public long getRemaining(String key, int limit, int windowSeconds) {
        String redisKey = "rate_limit:" + key;
        String val = redis.opsForValue().get(redisKey);
        long current = val != null ? Long.parseLong(val) : 0;
        return Math.max(0, limit - current);
    }
}
