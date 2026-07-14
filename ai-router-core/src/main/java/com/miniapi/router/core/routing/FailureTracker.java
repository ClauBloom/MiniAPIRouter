package com.miniapi.router.core.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 失败追踪器：按会话追踪意图评估的失败次数。
 * 当连续失败次数达到阈值（3 次）时，触发降级回退到上次成功的路由缓存。
 * 支持 TTL 过期清理，避免内存无限增长。
 */
@Component
public class FailureTracker {

    private static final Logger log = LoggerFactory.getLogger(FailureTracker.class);

    /** 触发回退的最大连续失败次数 */
    private static final int MAX_FAILURE_COUNT = 3;

    /** 会话失败记录的存活时间（30 分钟），超时自动清除 */
    private static final long ENTRY_TTL_MS = 30 * 60 * 1000;

    /** 会话 Key -> 失败记录 的并发安全映射表 */
    private final ConcurrentHashMap<String, Entry> tracker = new ConcurrentHashMap<>();

    /** 内部失败记录条目 */
    private static class Entry {
        int failureCount;      // 当前连续失败次数
        long lastAccessTime;   // 最后更新时间，用于 TTL 判断
    }

    /** 获取指定会话的当前失败次数 */
    public int getFailureCount(String sessionKey) {
        Entry entry = tracker.get(sessionKey);
        return entry != null ? entry.failureCount : 0;
    }

    /** 将指定会话的失败次数加 1 并更新访问时间 */
    public void incrementFailure(String sessionKey) {
        Entry entry = tracker.computeIfAbsent(sessionKey, k -> new Entry());
        entry.failureCount++;
        entry.lastAccessTime = System.currentTimeMillis();
        log.debug("[FailureTracker] session={} failureCount={}", sessionKey, entry.failureCount);
    }

    /** 重置指定会话的失败次数为 0 */
    public void resetFailures(String sessionKey) {
        Entry entry = tracker.computeIfAbsent(sessionKey, k -> new Entry());
        entry.failureCount = 0;
        entry.lastAccessTime = System.currentTimeMillis();
    }

    /** 判断指定会话是否应触发回退（失败次数 >= 阈值） */
    public boolean shouldFallback(String sessionKey) {
        return getFailureCount(sessionKey) >= MAX_FAILURE_COUNT;
    }

    /** 清空所有失败记录（配置变更时调用） */
    public void clearAll() {
        int size = tracker.size();
        tracker.clear();
        log.info("[FailureTracker] Cleared {} entries due to config change", size);
    }

    /** 清除超过 TTL 时间的过期记录 */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        tracker.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > ENTRY_TTL_MS);
    }
}
