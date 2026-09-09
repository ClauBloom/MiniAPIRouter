package com.miniapi.router.core.springai;

import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.springai.SpringAiStreamConverter;
import org.springframework.ai.chat.model.ChatResponse;
import com.miniapi.router.core.streaming.StreamSink;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Spring AI 流式 sink（理念 E）：把统一流块转换为流式 {@code ChatResponse}
 * 推入 {@link FluxSink}，供 {@code RoutedChatModel#stream} 的 Flux 下游消费。
 * <p>
 * 断开检测基于 {@link FluxSink#isCancelled()} —— 返回 false 使 StreamProxy
 * 立即停止消费上游，避免浪费 token。回退不做显式信号（返回 NO_SIGNAL），
 * 未发送内容时由 StreamProxy 静默切换上游。错误后标记终止，忽略后续 usage/complete。
 */
public class ChatResponseStreamSink implements StreamSink {

    private final FluxSink<ChatResponse> fluxSink;
    private final SpringAiStreamConverter streamConverter;
    private final Map<String, Object> routingMeta;
    private final String requestId;
    private final String model;
    /** 原始 chunk 监听器（供 tool-calling 聚合器在 chunk 层累积），可为 null */
    private final Consumer<UnifiedStreamChunk> chunkListener;
    private volatile boolean terminated = false;

    public ChatResponseStreamSink(FluxSink<ChatResponse> fluxSink,
                                  SpringAiStreamConverter streamConverter,
                                  Map<String, Object> routingMeta,
                                  String requestId, String model) {
        this(fluxSink, streamConverter, routingMeta, requestId, model, null);
    }

    public ChatResponseStreamSink(FluxSink<ChatResponse> fluxSink,
                                  SpringAiStreamConverter streamConverter,
                                  Map<String, Object> routingMeta,
                                  String requestId, String model,
                                  Consumer<UnifiedStreamChunk> chunkListener) {
        this.fluxSink = fluxSink;
        this.streamConverter = streamConverter;
        this.routingMeta = routingMeta;
        this.requestId = requestId;
        this.model = model;
        this.chunkListener = chunkListener;
    }

    @Override
    public boolean onChunk(UnifiedStreamChunk chunk) {
        if (terminated) return false;
        if (chunkListener != null) {
            chunkListener.accept(chunk);
        }
        fluxSink.next(streamConverter.toChunkChatResponse(chunk, routingMeta));
        return !fluxSink.isCancelled();
    }

    @Override
    public FallbackOutcome onFallback(FallbackEvent event) {
        return FallbackOutcome.NO_SIGNAL;
    }

    @Override
    public void onError(String errorCode, String message, String traceId) {
        if (terminated) return;
        terminated = true;
        /* 抛出携带错误码的 RouterException（而非 IllegalStateException），便于宿主按 errorCode 映射业务错误 */
        fluxSink.error(new com.miniapi.router.core.exception.RouterException(errorCode, message, 502));
    }

    @Override
    public void onUsage(UsageStats stats) {
        if (terminated) return;
        /* 空 choices + usage 元数据，对齐 OpenAI stream_options.include_usage 语义 */
        fluxSink.next(streamConverter.toUsageChatResponse(stats, routingMeta, requestId, model));
    }

    @Override
    public void onComplete() {
        if (terminated) return;
        terminated = true;
        fluxSink.complete();
    }
}
