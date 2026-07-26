package com.miniapi.router.core.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 失败追踪器：按会话追踪意图评估的失败次数。
 * 当连续失败次数达到阈值（3 次）时，触发降级回退到上次成功的路由缓存。
 * <p>
 * 基于 Caffeine 实现 TTL（30 分钟）与容量上限，过期与淘汰由缓存自动完成，
 * 无需外部定时清理，杜绝内存无限增长。
 * </p>
 */
@Component
public class FailureTracker {

    private static final Logger log = LoggerFactory.getLogger(FailureTracker.class);

    /** 触发回退的最大连续失败次数 */
    private static final int MAX_FAILURE_COUNT = 3;

    /** 会话失败记录的存活时间（30 分钟），到期自动清除 */
    private static final long ENTRY_TTL_MINUTES = 30;

    /** 会话记录容量上限，防止恶意会话洪泛 */
    private static final long MAX_ENTRIES = 10_000;

    /** 会话 Key -> 失败计数 的缓存（访问续期，容量+TTL 双重上限） */
    private final Cache<String, AtomicInteger> tracker = Caffeine.newBuilder()
            .expireAfterAccess(ENTRY_TTL_MINUTES, TimeUnit.MINUTES)
            .maximumSize(MAX_ENTRIES)
            .build();

    /** 获取指定会话的当前失败次数 */
    public int getFailureCount(String sessionKey) {
        AtomicInteger count = tracker.getIfPresent(sessionKey);
        return count != null ? count.get() : 0;
    }

    /** 将指定会话的失败次数加 1 */
    public void incrementFailure(String sessionKey) {
        AtomicInteger count = tracker.get(sessionKey, k -> new AtomicInteger(0));
        int updated = count.incrementAndGet();
        log.debug("[FailureTracker] session={} failureCount={}", sessionKey, updated);
    }

    /** 重置指定会话的失败次数为 0 */
    public void resetFailures(String sessionKey) {
        tracker.invalidate(sessionKey);
    }

    /** 判断指定会话是否应触发回退（失败次数 >= 阈值） */
    public boolean shouldFallback(String sessionKey) {
        return getFailureCount(sessionKey) >= MAX_FAILURE_COUNT;
    }

    /** 清空所有失败记录（配置变更时调用） */
    public void clearAll() {
        long size = tracker.estimatedSize();
        tracker.invalidateAll();
        log.info("[FailureTracker] Cleared ~{} entries due to config change", size);
    }
}
