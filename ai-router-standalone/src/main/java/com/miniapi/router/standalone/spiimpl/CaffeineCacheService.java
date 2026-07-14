package com.miniapi.router.standalone.spiimpl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.spi.CacheService;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Function;

/**
 * 基于 Caffeine 的本地缓存服务实现。
 * <p>
 * 实现 CacheService SPI 接口，使用 Caffeine 作为本地内存缓存。
 * 缓存值以 JSON 字符串形式存储，读取时反序列化为目标类型。
 * 默认写入后 10 分钟过期，最大缓存 10000 条记录。
 * </p>
 */
@Component
public class CaffeineCacheService implements CacheService {

    private final Cache<String, String> cache; // Caffeine 缓存实例，值为 JSON 字符串

    public CaffeineCacheService() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10)) // 写入后 10 分钟过期
                .maximumSize(10000) // 最大缓存条目数
                .build();
    }

    /**
     * 从缓存中获取指定键的值并反序列化为目标类型。
     *
     * @param key  缓存键
     * @param type 目标类型
     * @return 缓存值，不存在则返回 null
     */
    @Override
    public <T> T get(String key, Class<T> type) {
        String json = cache.getIfPresent(key);
        if (json == null) return null;
        return JsonUtils.fromJson(json, type);
    }

    /**
     * 获取或加载缓存值。
     * 如果缓存中不存在，则通过加载器函数加载，并放入缓存。
     *
     * @param key    缓存键
     * @param type   目标类型
     * @param loader 加载器函数
     * @param ttl    过期时间（此实现使用全局默认 TTL，参数被忽略）
     * @return 缓存或加载的值
     */
    @Override
    public <T> T getOrLoad(String key, Class<T> type, Function<String, T> loader, Duration ttl) {
        T cached = get(key, type);
        if (cached != null) return cached;
        T value = loader.apply(key);
        if (value != null) put(key, value);
        return value;
    }

    /**
     * 将值序列化为 JSON 并放入缓存。
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间（此实现使用全局默认 TTL，参数被忽略）
     */
    @Override
    public void put(String key, Object value, Duration ttl) {
        if (value == null) return;
        cache.put(key, JsonUtils.toJson(value));
    }

    /**
     * 将值放入缓存，使用默认 10 分钟过期时间。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void put(String key, Object value) {
        put(key, value, Duration.ofMinutes(10));
    }

    /**
     * 从缓存中移除指定键。
     *
     * @param key 缓存键
     */
    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    /**
     * 按模式批量移除缓存。
     * 将通配符模式转换为正则表达式，匹配并移除所有符合的缓存键。
     *
     * @param pattern 通配符模式（如 "user:*"）
     */
    @Override
    public void evictPattern(String pattern) {
        String regex = pattern.replace("*", ".*"); // 通配符转正则
        cache.asMap().keySet().removeIf(k -> k.matches(regex));
    }
}
