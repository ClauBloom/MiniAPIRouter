package com.miniapi.router.core.domain;

import lombok.Data;
import lombok.Builder;

/**
 * 使用量统计事件领域对象。
 * <p>
 * 记录一次请求的 Token 用量和性能指标，用于流式响应结束后的用量上报。
 * </p>
 */
@Data
@Builder
public class UsageStats {

    /** 事件类型，默认为 "usage_stats" */
    @Builder.Default
    private String type = "usage_stats";

    /** 提示词 Token 消耗数 */
    private int promptTokens;

    /** 补全 Token 消耗数 */
    private int completionTokens;

    /** 总 Token 消耗数 */
    private int totalTokens;

    /** Token 数是否为估算值 */
    private boolean estimated;

    /** 请求总延迟（毫秒） */
    private int latencyMs;

    /** 首 Token 到达时间（毫秒） */
    private int ttftMs;

    /** 使用的模型 */
    private String model;

    /** 上游服务商 */
    private String provider;

    /** 降级回退次数 */
    private int fallbackCount;

    /** 时间戳 */
    private long timestamp;
}
