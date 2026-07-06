package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRouteMemory {

    private static final Logger log = LoggerFactory.getLogger(SessionRouteMemory.class);
    private static final long ENTRY_TTL_MS = 30 * 60 * 1000;

    private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();

    private static class Entry {
        Long selectedKeyId;
        String selectedKeyModel;
        String intent;
        int score;
        long lastAccessTime;
    }

    public void recordSuccess(String sessionKey, ApiKeyConfig key, String intent, int score) {
        Entry entry = memory.computeIfAbsent(sessionKey, k -> new Entry());
        entry.selectedKeyId = key.getId();
        entry.selectedKeyModel = key.getModels() != null && !key.getModels().isEmpty()
                ? key.getModels().get(0) : null;
        entry.intent = intent;
        entry.score = score;
        entry.lastAccessTime = System.currentTimeMillis();
        log.debug("[SessionRouteMemory] session={} recorded key_id={} intent={} score={}",
                sessionKey, key.getId(), intent, score);
    }

    public CachedResult getLastSuccess(String sessionKey) {
        Entry entry = memory.get(sessionKey);
        if (entry == null || entry.selectedKeyId == null) return null;
        return new CachedResult(entry.selectedKeyId, entry.selectedKeyModel,
                entry.intent, entry.score);
    }

    public void clearAll() {
        int size = memory.size();
        memory.clear();
        log.info("[SessionRouteMemory] Cleared {} entries due to config change", size);
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        memory.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > ENTRY_TTL_MS);
    }

    public record CachedResult(Long keyId, String model, String intent, int score) {}
}
