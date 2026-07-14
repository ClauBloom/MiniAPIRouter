package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话路由记忆：按会话缓存最近一次意图路由成功的记录。
 * 当意图评估连续失败触发回退时，从该缓存中恢复上次成功的路由目标。
 * 缓存条目支持 TTL（30 分钟）过期清理。
 */
@Component
public class SessionRouteMemory {

    private static final Logger log = LoggerFactory.getLogger(SessionRouteMemory.class);

    /** 每个缓存条目的最大存活时间（30 分钟） */
    private static final long ENTRY_TTL_MS = 30 * 60 * 1000;

    /** 会话 Key -> 成功路由记录 的并发安全映射表 */
    private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();

    /** 内部缓存条目：记录成功路由时的 Key、模型、意图和评分 */
    private static class Entry {
        Long selectedKeyId;       // 被选中的 API Key ID
        String selectedKeyModel;  // 被选中的 Key 的第一个模型名
        String intent;            // 意图标签
        int score;                // 意图评分
        long lastAccessTime;      // 最后访问时间，用于 TTL 判断
    }

    /** 记录一次成功的意图路由结果 */
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

    /** 获取指定会话的最后一次成功路由缓存，不存在则返回 null */
    public CachedResult getLastSuccess(String sessionKey) {
        Entry entry = memory.get(sessionKey);
        if (entry == null || entry.selectedKeyId == null) return null;
        return new CachedResult(entry.selectedKeyId, entry.selectedKeyModel,
                entry.intent, entry.score);
    }

    /** 清空所有缓存记录（配置变更时调用） */
    public void clearAll() {
        int size = memory.size();
        memory.clear();
        log.info("[SessionRouteMemory] Cleared {} entries due to config change", size);
    }

    /** 清除超过 TTL 时间的过期记录 */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        memory.entrySet().removeIf(e -> now - e.getValue().lastAccessTime > ENTRY_TTL_MS);
    }

    /** 缓存查询结果，不可变记录 */
    public record CachedResult(Long keyId, String model, String intent, int score) {}
}
