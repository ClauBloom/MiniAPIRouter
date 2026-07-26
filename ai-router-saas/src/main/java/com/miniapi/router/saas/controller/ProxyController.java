package com.miniapi.router.saas.controller;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.security.ApiKeyAuthService;
import com.miniapi.router.saas.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * OpenAI 与 Anthropic 兼容代理端点，共享 API Key 认证和响应头处理。
 */
@RestController
public class ProxyController {

    private final ProxyService proxyService;
    private final ApiKeyAuthService apiKeyAuthService;

    public ProxyController(ProxyService proxyService, ApiKeyAuthService apiKeyAuthService) {
        this.proxyService = proxyService;
        this.apiKeyAuthService = apiKeyAuthService;
    }

    @PostMapping("/v1/chat/completions")
    public Object proxyOpenAi(@RequestBody Map<String, Object> body,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        return proxy("openai", body, request, response);
    }

    @PostMapping("/v1/messages")
    public Object proxyAnthropic(@RequestBody Map<String, Object> body,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        return proxy("anthropic", body, request, response);
    }

    private Object proxy(String protocol, Map<String, Object> body,
                         HttpServletRequest request, HttpServletResponse response) {
        String apiKey = authenticate(request);
        Object result = proxyService.proxy(new ProxyService.ProxyRequest(protocol, body, apiKey, request));
        if (result instanceof StreamingResponseBody streamingResponse) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            return streamingResponse;
        }
        return ResponseEntity.ok(result);
    }

    private String authenticate(HttpServletRequest request) {
        String apiKey = apiKeyAuthService.extractApiKey(request);
        if (apiKey == null) {
            throw new RouterException("UNAUTHORIZED", "Missing API key", 401);
        }
        ApiKeyAuthService.AuthResult authResult = apiKeyAuthService.authenticate(apiKey);
        if (!authResult.success()) {
            throw new RouterException(authResult.errorCode(), authResult.errorMessage(),
                    authResult.errorCode().startsWith("TENANT") ? 403 : 401);
        }
        TenantContext.setTenantId(authResult.tenantId());
        return apiKey;
    }
}
