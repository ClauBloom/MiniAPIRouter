package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;

/**
 * 流式输出端口：将流式代理的逐块产出抽象为类型化回调，
 * 而不是直接写 SSE 文本。
 * <p>
 * 两种典型实现：
 * <ul>
 *   <li>{@link SseStreamSink} —— 保持现有 HTTP 宿主行为，把 chunk 转成 SSE 字节写出</li>
 *   <li>Spring AI 侧的 {@code ChatResponseStreamSink} —— 把 chunk 转成
 *       {@code Flux<ChatResponse>} 元素</li>
 * </ul>
 * 所有方法在 {@link StreamProxy} 的同一个（虚拟）线程上按序调用，实现不需要做并发同步。
 */
public interface StreamSink {

    /**
     * 收到一个内容 chunk。
     *
     * @param chunk 统一流块（已回填请求级 id / model）
     * @return true 继续消费；false 表示下游已断开，立即停止上游消费
     */
    boolean onChunk(UnifiedStreamChunk chunk);

    /**
     * 上游失败后触发回退。
     *
     * @param event 回退事件
     * @return {@link FallbackOutcome#SIGNALED} 已发送显式回退信号；
     *         {@link FallbackOutcome#NO_SIGNAL} 无显式信号（代理层在尚未发送内容时做静默回退）；
     *         {@link FallbackOutcome#DISCONNECTED} sink 写出失败，下游已断开
     */
    FallbackOutcome onFallback(FallbackEvent event);

    /**
     * 显式回退信号的发送结果。
     */
    enum FallbackOutcome { SIGNALED, NO_SIGNAL, DISCONNECTED }

    /**
     * 发生不可恢复错误（错误信息已脱敏）。
     */
    void onError(String errorCode, String message, String traceId);

    /**
     * 流正常结束时携带最终用量统计。
     */
    void onUsage(UsageStats stats);

    /**
     * 流结束（无论成功失败，只要 sink 未断开都会调用）。
     */
    void onComplete();
}
