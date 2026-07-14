package com.miniapi.router.core.domain;

import lombok.Data;
import lombok.Builder;

/**
 * 降级回退事件领域对象。
 * <p>
 * 当上游请求失败触发降级策略时，记录回退事件的详细信息，
 * 包括失败的上游、回退目标、回退序号等，用于日志和监控。
 * </p>
 */
@Data
@Builder
public class FallbackEvent {

    /** 事件类型，默认为 "fallback_signal" */
    @Builder.Default
    private String type = "fallback_signal";

    /** 降级原因描述 */
    private String reason;

    /** 失败的上游服务商名称 */
    private String failedProvider;

    /** 失败的 API Key ID */
    private Long failedKeyId;

    /** 回退到的上游服务商名称 */
    private String fallbackProvider;

    /** 回退到的 API Key ID */
    private Long fallbackKeyId;

    /** 当前回退索引（第几次回退） */
    private Integer fallbackIndex;

    /** 最大允许回退次数 */
    private Integer maxFallback;

    /** 失败时已返回的部分响应内容长度 */
    private Integer partialContentLength;

    /** 事件时间戳 */
    private long timestamp;
}
