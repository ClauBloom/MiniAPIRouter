package com.miniapi.router.saas.spiimpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.miniapi.router.core.spi.CacheService;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Redis 缓存服务
 * <p>
 * 实现 {@link CacheService} SPI 接口，基于 Redis 提供通用缓存功能。
 * 对象以 JSON 格式序列化后存储到 Redis，读取时反序列化为指定类型。
 * </p>
 */
@Component
public class RedisCacheService implements CacheService {

    private static final int SCAN_BATCH_SIZE = 500;

    private final StringRedisTemplate redis;  // Redis 字符串模板

    public RedisCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 从缓存中获取指定类型的值
     *
     * @param key  缓存键
     * @param type 目标类型
     * @param <T>  泛型类型
     * @return 反序列化后的缓存值，若不存在则返回 null
     */
    @Override
    public <T> T get(String key, Class<T> type) {
        String val = redis.opsForValue().get(key);
        if (val == null) return null;
        return JsonUtils.fromJson(val, type);
    }

    /**
     * 获取或加载缓存值
     * <p>
     * 先从缓存获取，若缓存未命中则通过加载函数获取值并写入缓存。
     * </p>
     *
     * @param key    缓存键
     * @param type   目标类型
     * @param loader 缓存加载函数
     * @param ttl    缓存过期时间
     * @param <T>    泛型类型
     * @return 缓存值或加载的值
     */
    @Override
    public <T> T getOrLoad(String key, Class<T> type, Function<String, T> loader, Duration ttl) {
        T cached = get(key, type);
        if (cached != null) return cached;
        // 缓存未命中，通过加载函数获取值
        T value = loader.apply(key);
        if (value != null) put(key, value, ttl);
        return value;
    }

    /**
     * 写入缓存（带过期时间）
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        redis.opsForValue().set(key, JsonUtils.toJson(value), ttl);
    }

    /**
     * 写入缓存（不带过期时间，永久有效）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void put(String key, Object value) {
        redis.opsForValue().set(key, JsonUtils.toJson(value));
    }

    /**
     * 清除指定键的缓存
     *
     * @param key 缓存键
     */
    @Override
    public void evict(String key) {
        redis.delete(key);
    }

    /**
     * 按模式批量清除缓存
     * <p>
     * 使用增量 SCAN 匹配键，并分批通过 UNLINK 异步释放值内存，避免 KEYS 阻塞 Redis。
     * </p>
     *
     * @param pattern 键匹配模式（如 "apikey:*"）
     */
    @Override
    public void evictPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();
        try (Cursor<String> cursor = redis.scan(options)) {
            List<String> batch = new ArrayList<>(SCAN_BATCH_SIZE);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == SCAN_BATCH_SIZE) {
                    redis.unlink(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redis.unlink(batch);
            }
        }
    }
}
