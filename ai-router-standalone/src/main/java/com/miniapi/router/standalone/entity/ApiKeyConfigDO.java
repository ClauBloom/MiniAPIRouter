package com.miniapi.router.standalone.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 配置数据对象（DO）。
 * <p>
 * 对应数据库 api_key_config 表，存储上游 AI 服务的 API Key 配置信息。
 * 包括供应商、协议、密钥（加密存储）、基础 URL、支持的模型列表、
 * 权重、优先级、并发限制、超时设置、重试次数、状态和健康状态等。
 * </p>
 */
@Data
@TableName(value = "api_key_config", autoResultMap = true)
public class ApiKeyConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键 ID，自增
    private Long tenantId;              // 租户 ID
    private String name;                // 配置名称
    private String provider;            // AI 供应商（deepseek/openai/anthropic 等）
    private String protocol;            // 通信协议（openai/anthropic）
    private String apiKeyEnc;           // 加密后的 API Key
    private String baseUrl;             // 上游服务基础 URL
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> models;        // 支持的模型列表，以 JSON 存储
    private Integer weight;             // 权重（用于加权路由策略）
    private Integer priority;           // 优先级（数字越小优先级越高）
    private Integer maxConcurrent;      // 最大并发数
    private Integer qpsLimit;           // QPS 限制
    private Integer timeoutMs;          // 超时时间（毫秒）
    private Integer retryCount;         // 重试次数
    private Integer status;             // 状态（1=启用, 0=禁用）
    private String healthStatus;        // 健康状态（healthy/degraded/down/unknown）
    private LocalDateTime lastHealthCheckAt; // 最后健康检查时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;    // 创建时间，插入时自动填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;    // 更新时间，插入和更新时自动填充
    @TableLogic
    private Integer deleted;            // 逻辑删除标志（0=未删除, 1=已删除）
}
