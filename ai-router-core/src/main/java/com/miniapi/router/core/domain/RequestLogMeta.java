package com.miniapi.router.core.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 请求日志元数据领域对象。
 * <p>
 * 记录每次路由请求的完整元信息，包括请求标识、上游映射结果、
 * Token 用量、延迟耗时、错误信息等，用于审计和统计分析。
 * </p>
 */
@Data
public class RequestLogMeta {

    /** 主键 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 最终用户 ID */
    private Long userId;

    /** 分布式链路追踪 ID */
    private String traceId;

    /** 单次请求唯一标识 ID */
    private String requestId;

    /** 客户端 IP 地址 */
    private String clientIp;

    /** 请求协议类型 */
    private String protocol;

    /** 请求的模型名称 */
    private String model;

    /** 映射到的上游服务商 */
    private String mappedProvider;

    /** 实际使用的 API Key ID */
    private Long apiKeyId;

    /** 匹配的路由规则 ID */
    private Long routeRuleId;

    /** 匹配到的意图标签 */
    private String intent;

    /** Agent 身份标识（用于多 Agent 路由追踪） */
    private String agentId;

    /** Agent 类型 */
    private String agentType;

    /** 提示词 Token 消耗数 */
    private Integer promptTokens;

    /** 补全 Token 消耗数 */
    private Integer completionTokens;

    /** 总 Token 消耗数 */
    private Integer totalTokens;

    /** 请求总延迟（毫秒） */
    private Integer latencyMs;

    /** 首 Token 到达时间（毫秒，TTFT） */
    private Integer ttftMs;

    /** 请求状态：success、error、partial 等 */
    private String status;

    /** 降级回退次数 */
    private Integer fallbackCount;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 请求体存储路径（URL） */
    private String promptStorageUrl;

    /** 响应体存储路径（URL） */
    private String responseStorageUrl;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
