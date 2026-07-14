package com.miniapi.router.core.protocol.converter;

import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.streaming.SseEventEmitter;

/**
 * 流式转换器接口，定义流式 SSE 事件转换的核心约定。
 * <p>
 * 实现类负责将内部统一流块 {@link UnifiedStreamChunk} 转换为
 * 特定协议（如 OpenAI、Anthropic）的 SSE 事件格式，
 * 以及流量统计、降级事件和错误消息的 SSE 输出。
 */
public interface StreamConverter {
    /**
     * 将统一流块转换为目标协议的 SSE 数据块。
     *
     * @param chunk           统一流块
     * @param inboundProtocol 入站协议名称
     * @return SSE 格式的字符串
     */
    String toSseChunk(UnifiedStreamChunk chunk, String inboundProtocol);

    /**
     * 生成流结束标记（如 OpenAI 的 "[DONE]"）。
     *
     * @param inboundProtocol 入站协议名称
     * @return 结束标记的 SSE 字符串
     */
    String toDoneMark(String inboundProtocol);

    /**
     * 判断当前转换器是否支持指定的协议。
     *
     * @param protocol 协议名称
     * @return 是否支持该协议
     */
    boolean supports(String protocol);

    /**
     * 生成包含用量统计的 SSE 块（默认使用 SseEventEmitter）。
     */
    default String toUsageSseChunk(UsageStats stats) {
        return SseEventEmitter.usageStats(stats);
    }

    /**
     * 生成降级事件信号的 SSE 块（默认使用 SseEventEmitter）。
     */
    default String toFallbackSseChunk(FallbackEvent event) {
        return SseEventEmitter.fallbackSignal(event);
    }

    /**
     * 生成流式错误消息的 SSE 块（默认使用 SseEventEmitter）。
     */
    default String toErrorSseChunk(String errorCode, String message, String traceId) {
        return SseEventEmitter.streamError(errorCode, message, traceId);
    }
}
