package com.miniapi.router.saas.controller;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.util.TraceUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.security.ApiKeyAuthService;
import com.miniapi.router.saas.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * OpenAI API 代理控制器。
 * 
 * <p>负责接收符合 OpenAI Chat Completions API 格式的请求（POST /v1/chat/completions），
 * 通过 API Key 认证后，将请求转发给 {@link ProxyService} 进行路由和代理。
 * 支持流式（SSE）和非流式两种响应模式。
 */
@RestController
public class OpenAIProxyController {

    private final ProxyService proxyService;        // 代理服务，负责请求路由与转发
    private final ApiKeyAuthService apiKeyAuthService; // API Key 认证服务

    /**
     * 构造函数注入代理服务和 API Key 认证服务。
     *
     * @param proxyService      代理服务
     * @param apiKeyAuthService API Key 认证服务
     */
    public OpenAIProxyController(ProxyService proxyService, ApiKeyAuthService apiKeyAuthService) {
        this.proxyService = proxyService;
        this.apiKeyAuthService = apiKeyAuthService;
    }

    /**
     * 处理 OpenAI Chat Completions API 代理请求。
     * <p>先进行 API Key 认证，然后将请求委托给代理服务处理。
     * 如果返回结果是流式响应体，则设置 SSE 相关响应头。
     *
     * @param body     请求体（OpenAI Chat Completions 格式）
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 代理结果，可能是流式响应体或普通响应实体
     */
    @PostMapping("/v1/chat/completions")
    public Object proxy(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        // 执行 API Key 认证，设置租户上下文
        authenticate(body, request);
        // 构造代理请求并执行，协议类型为 openai
        Object result = proxyService.proxy(new ProxyService.ProxyRequest("openai", body, apiKeyAuthService.extractApiKey(request), request));
        // 如果是流式响应，设置 SSE 相关的响应头
        if (result instanceof StreamingResponseBody srb) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE); // 设置为 Server-Sent Events
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");           // 禁用缓存
            response.setHeader("Connection", "keep-alive");            // 保持长连接
            return srb;
        }
        // 非流式响应，直接返回 JSON 结果
        return ResponseEntity.ok(result);
    }

    /**
     * 执行 API Key 认证。
     * <p>从请求中提取 API Key，调用认证服务验证有效性。
     * 认证成功后将租户ID和追踪ID写入租户上下文。
     * 认证失败时抛出相应异常（401 未授权或 403 租户级别错误）。
     *
     * @param body    请求体
     * @param request HTTP 请求对象
     */
    private void authenticate(Map<String, Object> body, HttpServletRequest request) {
        String apiKey = apiKeyAuthService.extractApiKey(request);
        if (apiKey == null) {
            // 未提供 API Key，抛出未授权异常
            throw new RouterException("UNAUTHORIZED", "Missing API key", 401);
        }
        // 调用认证服务验证 API Key
        ApiKeyAuthService.AuthResult authResult = apiKeyAuthService.authenticate(apiKey);
        if (!authResult.success()) {
            // 认证失败：租户相关错误返回 403，其他错误返回 401
            throw new RouterException(authResult.errorCode(), authResult.errorMessage(),
                    authResult.errorCode().startsWith("TENANT") ? 403 : 401);
        }
        // 将认证结果写入租户上下文，供后续请求处理使用
        TenantContext.setTenantId(authResult.tenantId());
        TenantContext.setTraceId(TraceUtils.newTraceId());
    }
}
