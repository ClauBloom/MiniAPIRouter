package com.miniapi.router.saas.dto.request;

import lombok.Data;

/**
 * 租户创建请求 DTO。
 * 
 * <p>封装创建新租户时提交的配置信息，包括租户编码、名称、套餐、配额和限流等设置。
 */
@Data
public class TenantCreateRequest {
    private String tenantCode;          // 租户编码，唯一标识一个租户
    private String tenantName;          // 租户名称，用于显示
    private String plan = "free";       // 套餐类型，默认为免费版
    private Long quotaLimit = 1000000L; // Token 配额上限，默认 100 万
    private Integer maxRps = 10;        // 每秒最大请求数限制，默认 10
    private String expiresAt;           // 租户过期时间（ISO 格式字符串，可选）
}
