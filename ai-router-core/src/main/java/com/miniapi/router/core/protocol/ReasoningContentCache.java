package com.miniapi.router.core.protocol;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 推理内容缓存，用于在模型切换或 fallback 场景下保持推理链的连续性。
 * <p>
 * 当上游模型返回带有 reasoning_content 的响应时，将 content 与 reasoning_content
 * 建立映射关系并缓存。后续请求中如果历史消息缺少 reasoning_content，
 * 可通过 content 查找并恢复对应的推理内容。
 * 使用 ConcurrentHashMap 实现，带 TTL 过期机制。
 */
@Component
public class ReasoningContentCache {

    /** 缓存存活时间（5分钟） */
    private static final long TTL_MS = 300_000L;
    /** 底层缓存存储，键为消息内容，值为缓存条目 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 缓存条目记录，包含推理内容和写入时间戳 */
    private record CacheEntry(String reasoningContent, long timestamp) {}

    /**
     * 存储消息内容到推理内容的映射。
     *
     * @param content          消息文本内容（作为键）
     * @param reasoningContent 对应的推理内容（作为值）
     */
    public void store(String content, String reasoningContent) {
        if (content == null || content.isEmpty() || reasoningContent == null || reasoningContent.isEmpty()) {
            return;
        }
        cache.put(content, new CacheEntry(reasoningContent, System.currentTimeMillis()));
    }

    /**
     * 根据消息内容查找对应的推理内容。
     * 如果缓存条目已超过 TTL，则自动移除并返回 null。
     *
     * @param content 消息文本内容
     * @return 对应的推理内容，未找到或已过期则返回 null
     */
    public String lookup(String content) {
        if (content == null || content.isEmpty()) return null;
        CacheEntry entry = cache.get(content);
        if (entry == null) return null;
        // 检查是否已过期
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            cache.remove(content);
            return null;
        }
        return entry.reasoningContent;
    }
}
