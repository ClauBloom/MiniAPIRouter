package com.miniapi.router.core.spi;

import java.time.Duration;
import java.util.function.Function;

/**
 * 缓存服务接口（SPI 扩展点）。
 * <p>
 * 提供通用的缓存读写能力，支持 TTL（过期时间）、懒加载和模糊清除。
 * 典型用于缓存路由规则、API Key 配置等热数据，减少数据库访问频率。
 * </p>
 */
public interface CacheService {

    /** 根据 key 获取缓存值，返回指定类型的对象 */
    <T> T get(String key, Class<T> type);

    /**
     * 获取缓存值，若不存在则通过 loader 回源加载并写入缓存。
     *
     * @param key   缓存键
     * @param type  返回值类型
     * @param loader 缓存未命中时的回源加载函数
     * @param ttl   写入缓存的过期时间
     * @param <T>   泛型返回值类型
     * @return 缓存中的对象或回源加载的结果
     */
    <T> T getOrLoad(String key, Class<T> type, Function<String, T> loader, Duration ttl);

    /** 将值写入缓存，并设置过期时间 */
    void put(String key, Object value, Duration ttl);

    /** 将值写入缓存，使用默认过期时间 */
    void put(String key, Object value);

    /** 清除指定 key 的缓存项 */
    void evict(String key);

    /** 按通配符模式批量清除缓存项 */
    void evictPattern(String pattern);
}
