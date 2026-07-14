package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.util.JsonUtils;

/**
 * SSE 事件发射器工具类：将各类事件格式化为符合 SSE（Server-Sent Events）规范的输出字符串。
 * 提供纯数据行、带事件类型行、回退信号、用量统计、流式错误等多种输出格式。
 */
public class SseEventEmitter {

    private SseEventEmitter() {}

    /** 构造单纯的 data 行 */
    public static String data(String json) {
        return "data: " + json + "\n\n";
    }

    /** 构造带事件类型的 data 行 */
    public static String event(String eventName, String json) {
        return "event: " + eventName + "\ndata: " + json + "\n\n";
    }

    /** 发送回退信号事件 */
    public static String fallbackSignal(FallbackEvent event) {
        return event("fallback_signal", JsonUtils.toJson(event));
    }

    /** 发送用量统计事件 */
    public static String usageStats(UsageStats stats) {
        return event("usage_stats", JsonUtils.toJson(stats));
    }

    /** 发送流式错误事件，包含错误码、消息和追踪 ID */
    public static String streamError(String errorCode, String message, String traceId) {
        return event("error", JsonUtils.toJson(java.util.Map.of(
                "type", "error",
                "error_code", errorCode,
                "message", message,
                "trace_id", traceId != null ? traceId : ""
        )));
    }
}
