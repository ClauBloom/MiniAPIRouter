package com.miniapi.router.core.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 上游 API Key 配置领域对象。
 * <p>
 * 记录一个上游 AI 服务提供商的 API 密钥及其路由相关的配置，
 * 包括服务商名称、协议类型、支持的模型列表、权重、优先级、限流等参数。
 * </p>
 */
@Data
public class ApiKeyConfig {

    /** 主键 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** Key 名称 */
    private String name;

    /** 服务商名称，如 openai、anthropic 等 */
    private String provider;

    /** 协议类型，如 openai、anthropic 等 */
    private String protocol;

    /** API 密钥明文（仅从配置输入时暂存） */
    private String apiKey;

    /** API 密钥密文（持久化存储形式） */
    private String apiKeyEnc;

    /** 上游服务基础 URL */
    private String baseUrl;

    /** 该 Key 支持的模型列表 */
    private List<String> models;

    /** 路由权重，数值越大被选中概率越高 */
    private Integer weight;

    /** 路由优先级，数值越小优先级越高 */
    private Integer priority;

    /** 最大并发请求数 */
    private Integer maxConcurrent;

    /** 每秒请求数 (QPS) 上限 */
    private Integer qpsLimit;

    /** 请求超时时间（毫秒） */
    private Integer timeoutMs;

    /** 失败重试次数 */
    private Integer retryCount;

    /** 状态：0=禁用, 1=启用 */
    private Integer status;

    /** 健康状态描述 */
    private String healthStatus;

    /** 最近一次健康检查时间 */
    private LocalDateTime lastHealthCheckAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 判断当前 Key 是否处于启用状态。
     *
     * @return 当 status 为 1 时返回 true，否则返回 false
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
