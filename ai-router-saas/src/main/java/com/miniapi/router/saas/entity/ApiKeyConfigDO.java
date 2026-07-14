package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key 配置数据对象（DO）。
 * 
 * <p>对应数据库表 api_key_config，存储租户配置的上游 API Key 信息。
 * 每个 API Key 配置记录包含供应商、协议、密钥（加密存储）、模型列表、
 * 路由权重、并发限制、超时设置等完整配置。
 * 
 * <p>使用 MyBatis-Plus 注解进行 ORM 映射：
 * <ul>
 *   <li>models 字段使用 JacksonTypeHandler 自动序列化/反序列化为 JSON</li>
 *   <li>createdAt 和 updatedAt 字段自动填充</li>
 *   <li>deleted 字段为逻辑删除标记</li>
 * </ul>
 */
@Data
@TableName(value = "api_key_config", autoResultMap = true)
public class ApiKeyConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键ID，自增
    private Long tenantId;          // 所属租户ID
    private String name;            // API Key 配置名称
    private String provider;        // 供应商标识（如 openai、anthropic）
    private String protocol;        // 协议类型（如 openai、anthropic）
    private String apiKeyEnc;       // 加密后的 API Key 密文
    private String baseUrl;         // 上游服务基础 URL
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> models;    // 支持的模型列表（JSON 存储）
    private Integer weight;         // 路由权重
    private Integer priority;       // 优先级
    private Integer maxConcurrent;  // 最大并发数
    private Integer qpsLimit;       // 每秒请求限制
    private Integer timeoutMs;      // 超时时间（毫秒）
    private Integer retryCount;     // 重试次数
    private Integer status;         // 启用状态（1=启用，0=禁用）
    private String healthStatus;    // 健康状态（healthy/unhealthy/unknown）
    private LocalDateTime lastHealthCheckAt; // 最近一次健康检查时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;   // 创建时间（插入时自动填充）
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;   // 更新时间（插入和更新时自动填充）
    @TableLogic
    private Integer deleted;           // 逻辑删除标记（0=未删除，1=已删除）
}
