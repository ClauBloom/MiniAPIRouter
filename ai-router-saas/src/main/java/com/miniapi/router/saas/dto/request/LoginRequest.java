package com.miniapi.router.saas.dto.request;

import lombok.Data;

/**
 * 登录请求 DTO。
 * 
 * <p>封装用户登录时提交的认证信息，包括用户名、密码和租户编码。
 */
@Data
public class LoginRequest {
    private String username;    // 用户名
    private String password;    // 密码（明文传输，由 HTTPS 保证安全性）
    private String tenantCode;  // 租户编码，用于识别用户所属租户（超级管理员可留空）
}
