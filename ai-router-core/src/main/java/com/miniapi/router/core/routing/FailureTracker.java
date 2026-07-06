package com.miniapi.router.core.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class FailureTracker {

    private static final Logger log = LoggerFactory.getLogger(FailureTracker.class);
    private static final int MAX_FAILURE_COUNT = 3;
    private static final long ENTRY_TTL_MS = 30 * 60 * 1000;

    private final ConcurrentHashMap<String, Entry> tracker = new ConcurrentHashMap<>();

    private static class Entry {
        int failureCount;
        long lastAccessTime;
    }

    public int getFailureCount(String sessionKey) {
        Entry entry = tracker.get(sessionKey);
        return entry != null ? entry.failureCount : 0;
    }

    public void incrementFailure(String sessionKey) {
        Entry entry = tracker.computeIfAbsent(sessionKey, k -> new Entry());
        entry.failureCount++;
        entry.lastAccessTime = System.currentTimeMillis();
        log.debug("[FailureTracker] session={} failureCount={}", sessionKey, entry.failureCount);
    }

    public void resetFailures(String sessionKey) {
        Entry entry = tracker.computeIfAbsent(sessionKey, k -> new Entry());
        entry.failureCount = 0;
        entry.lastAccessTime = System.currentTimeMillis();
    }

    public boolean shouldFallback(String sessionKey) {
        return getFailureCount(sessionKey) >= MAX_FAILURE_COUNT;
    }

    public void clearAll() {
        int size = tracker.size();
        tracker.clear();
        log.info("[FailureTracker] Cleared {} entries due to config change", size);
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        tracker.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > ENTRY_TTL_MS);
    }
}
