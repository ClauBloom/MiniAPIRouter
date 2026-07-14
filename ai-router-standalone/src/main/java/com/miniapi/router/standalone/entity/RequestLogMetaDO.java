package com.miniapi.router.standalone.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 请求日志元数据数据对象（DO）。
 * <p>
 * 对应数据库 request_log_meta 表，记录每次代理请求的元数据信息。
 * 包括追踪信息、协议、模型、Token 用量、延迟、状态等。
 * Prompt 和 Response 的完整内容存储在 BlobStorage 中，此表保存其引用 URL。
 * </p>
 */
@Data
@TableName("request_log_meta")
public class RequestLogMetaDO {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键 ID，自增
    private Long tenantId;              // 租户 ID
    private Long userId;                // 用户 ID
    private String traceId;             // 追踪 ID，用于链路追踪
    private String requestId;           // 请求 ID
    private String clientIp;            // 客户端 IP 地址
    private String protocol;            // 入站协议（openai/anthropic）
    private String model;               // 请求的模型名称
    private String mappedProvider;      // 实际路由到的供应商
    private Long apiKeyId;              // 使用的 API Key ID
    private Long routeRuleId;           // 匹配的路由规则 ID
    private String intent;              // 意图路由识别出的意图标签
    private Integer promptTokens;       // Prompt Token 数量
    private Integer completionTokens;   // Completion Token 数量
    private Integer totalTokens;        // 总 Token 数量
    private Integer latencyMs;          // 总延迟（毫秒）
    private Integer ttftMs;             // 首字延迟 Time To First Token（毫秒）
    private String status;              // 请求状态（success/failed/fallback）
    private Integer fallbackCount;      // 降级回退次数
    private String errorCode;           // 错误码
    private String errorMessage;        // 错误消息
    private String promptStorageUrl;    // Prompt 内容的存储 URL（BlobStorage 路径）
    private String responseStorageUrl;  // Response 内容的存储 URL（BlobStorage 路径）
    private String agentId;             // Agent 身份标识
    private String agentType;           // Agent 类型
    private LocalDateTime createdAt;    // 创建时间
}
