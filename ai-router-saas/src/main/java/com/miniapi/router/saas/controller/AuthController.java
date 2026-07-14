package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.request.LoginRequest;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器。
 * 
 * <p>提供用户登录认证接口，接收用户名、密码和租户编码，
 * 验证通过后返回 JWT Token，用于后续请求的身份认证。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService; // 认证服务，处理登录验证逻辑

    /**
     * 构造函数注入认证服务。
     *
     * @param authService 认证服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口。
     * <p>接收登录请求，验证用户名、密码和租户编码，
     * 验证成功后返回 JWT Token 及用户信息。
     *
     * @param req 登录请求体（包含用户名、密码、租户编码，经过参数校验）
     * @return 包含登录认证结果的统一响应
     */
    @PostMapping("/login")
    public ApiResponse<Object> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(authService.login(req.getUsername(), req.getPassword(), req.getTenantCode()));
    }
}
