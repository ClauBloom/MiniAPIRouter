package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 请求日志元数据对象（DO）。
 * 
 * <p>对应数据库表 request_log_meta，记录每次代理请求的元信息。
 * 包含请求来源、路由信息、Token 消耗、延迟指标、错误信息等，
 * 用于日志查询、数据分析和仪表盘统计。
 */
@Data
@TableName("request_log_meta")
public class RequestLogMetaDO {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键ID，自增
    private Long tenantId;          // 所属租户ID
    private Long userId;            // 发起请求的用户ID
    private String traceId;         // 链路追踪ID
    private String requestId;       // 请求唯一标识
    private String clientIp;        // 客户端 IP 地址
    private String protocol;        // 请求协议类型（openai/anthropic）
    private String model;           // 请求的模型名称
    private String mappedProvider;  // 实际路由到的供应商标识
    private Long apiKeyId;          // 实际使用的 API Key 配置ID
    private Long routeRuleId;       // 匹配的路由规则ID
    private String intent;          // 意图识别结果（用于智能路由）
    private Integer promptTokens;     // 提示词 Token 数
    private Integer completionTokens; // 补全 Token 数
    private Integer totalTokens;      // 总 Token 数
    private Integer latencyMs;        // 请求总延迟（毫秒）
    private Integer ttftMs;           // 首字延迟（Time To First Token，毫秒）
    private String status;          // 请求状态（success/error/timeout）
    private Integer fallbackCount;  // 故障转移次数
    private String errorCode;       // 错误码
    private String errorMessage;    // 错误消息
    private String promptStorageUrl;   // 提示词内容存储地址
    private String responseStorageUrl; // 响应内容存储地址
    private String agentId;            // Agent 身份标识
    private String agentType;          // Agent 类型
    private LocalDateTime createdAt; // 日志记录创建时间
}
