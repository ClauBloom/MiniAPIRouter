package com.miniapi.router.core.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.domain.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 会话路由记忆：按会话缓存最近一次意图路由成功的记录。
 * 当意图评估连续失败触发回退时，从该缓存中恢复上次成功的路由目标。
 * <p>
 * 基于 Caffeine 实现 TTL（30 分钟）与容量上限，过期与淘汰由缓存自动完成，
 * 无需外部定时清理，杜绝内存无限增长；TTL 在读取路径上真实生效
 * （过期条目不会再被 {@link #getLastSuccess(String)} 返回）。
 * </p>
 */
@Component
public class SessionRouteMemory {

    private static final Logger log = LoggerFactory.getLogger(SessionRouteMemory.class);

    /** 每个缓存条目的最大存活时间（30 分钟） */
    private static final long ENTRY_TTL_MINUTES = 30;

    /** 会话记录容量上限 */
    private static final long MAX_ENTRIES = 10_000;

    /** 会话 Key -> 成功路由记录 的缓存（访问续期，容量+TTL 双重上限） */
    private final Cache<String, CachedResult> memory = Caffeine.newBuilder()
            .expireAfterAccess(ENTRY_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_ENTRIES)
            .build();

    /** 记录一次成功的意图路由结果 */
    public void recordSuccess(String sessionKey, ApiKeyConfig key, String selectedModel, String intent, int score) {
        memory.put(sessionKey, new CachedResult(key.getId(), selectedModel, selectedModel, intent, score));
        log.debug("[SessionRouteMemory] session={} recorded key_id={} model={} intent={} score={}",
                sessionKey, key.getId(), selectedModel, intent, score);
    }

    /** 获取指定会话的最后一次成功路由缓存，不存在或已过期则返回 null */
    public CachedResult getLastSuccess(String sessionKey) {
        return memory.getIfPresent(sessionKey);
    }

    /** 清空所有缓存记录（配置变更时调用） */
    public void clearAll() {
        long size = memory.estimatedSize();
        memory.invalidateAll();
        log.info("[SessionRouteMemory] Cleared ~{} entries due to config change", size);
    }

    /** 缓存查询结果，不可变记录 */
    public record CachedResult(Long keyId, String model, String selectedModel, String intent, int score) {}
}
