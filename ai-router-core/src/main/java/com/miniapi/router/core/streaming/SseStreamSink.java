package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.StreamConverter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSE 输出 sink：保持现有 HTTP 宿主行为，把 chunk 转成 SSE 文本写到 {@link OutputStream}。
 * <p>
 * 与旧 {@code StreamProxy} 行为一一对应：
 * chunk → {@code toSseChunk}；fallback → {@code toFallbackSseChunk}（空串视为静默回退）；
 * 错误 / 用量 / [DONE] 同理。
 */
public class SseStreamSink implements StreamSink {

    private final OutputStream os;
    private final StreamConverter converter;
    private final String inboundProtocol;

    public SseStreamSink(OutputStream os, StreamConverter converter, String inboundProtocol) {
        this.os = os;
        this.converter = converter;
        this.inboundProtocol = inboundProtocol;
    }

    @Override
    public boolean onChunk(UnifiedStreamChunk chunk) {
        return write(converter.toSseChunk(chunk, inboundProtocol));
    }

    @Override
    public FallbackOutcome onFallback(FallbackEvent event) {
        String chunk = converter.toFallbackSseChunk(event);
        if (chunk == null || chunk.isEmpty()) {
            return FallbackOutcome.NO_SIGNAL;
        }
        return write(chunk) ? FallbackOutcome.SIGNALED : FallbackOutcome.DISCONNECTED;
    }

    @Override
    public void onError(String errorCode, String message, String traceId) {
        write(converter.toErrorSseChunk(errorCode, message, traceId));
    }

    @Override
    public void onUsage(UsageStats stats) {
        write(converter.toUsageSseChunk(stats));
    }

    @Override
    public void onComplete() {
        write(converter.toDoneMark(inboundProtocol));
    }

    /**
     * 写入并立即刷新；失败（客户端断开）返回 false。
     */
    private boolean write(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return true;
        }
        try {
            os.write(chunk.getBytes(StandardCharsets.UTF_8));
            os.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
