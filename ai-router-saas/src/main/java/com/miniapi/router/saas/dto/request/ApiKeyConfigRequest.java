package com.miniapi.router.saas.dto.request;

import lombok.Data;
import java.util.List;

/**
 * API Key 配置请求 DTO。
 * 
 * <p>用于创建或更新上游 API Key 配置时的请求参数封装。
 * 包含 API Key 的基本信息、路由权重、并发限制、超时设置等配置项。
 */
@Data
public class ApiKeyConfigRequest {
    private String name;          // API Key 配置名称，用于标识和区分不同的 Key
    private String provider;      // 供应商标识（如 openai、anthropic、azure 等）
    private String protocol;      // 协议类型（如 openai、anthropic）
    private String apiKey;        // 上游 API Key 明文，入库前会进行加密存储
    private String baseUrl;       // 上游服务的基础 URL
    private List<String> models;  // 该 Key 支持的模型列表
    private Integer weight = 1;       // 权重，用于加权路由策略中分配请求比例
    private Integer priority = 0;     // 优先级，数值越大优先被选择
    private Integer maxConcurrent = 10; // 最大并发请求数限制
    private Integer qpsLimit = 0;     // 每秒请求限制（0表示不限制）
    private Integer timeoutMs = 30000; // 请求超时时间（毫秒）
    private Integer retryCount = 1;   // 失败重试次数
}
