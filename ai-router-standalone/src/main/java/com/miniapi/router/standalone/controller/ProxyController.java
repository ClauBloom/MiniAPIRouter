package com.miniapi.router.standalone.controller;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.standalone.service.StandaloneProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

/**
 * 代理转发控制器。
 * <p>
 * 提供 OpenAI 和 Anthropic 两种协议的代理转发接口。
 * 支持流式（SSE）和非流式响应。
 * </p>
 */
@RestController
public class ProxyController {

    private final StandaloneProxyService proxyService; // 代理转发服务

    public ProxyController(StandaloneProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * OpenAI 协议代理接口。
     * 接收 OpenAI 格式的请求体，转发到路由选中的上游 API Key。
     * 如果请求指定了 stream=true，则返回 SSE 流式响应。
     *
     * @param body     请求体
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 响应结果（流式响应返回 StreamingResponseBody，非流式返回 ResponseEntity）
     */
    @PostMapping("/v1/chat/completions")
    public Object openaiProxy(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        Object result = proxyService.proxy(new StandaloneProxyService.ProxyRequest("openai", body, extractApiKey(request), request));
        // 如果是流式响应，设置 SSE 相关响应头
        if (result instanceof StreamingResponseBody srb) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            return srb;
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Anthropic 协议代理接口。
     * 接收 Anthropic 格式的请求体，转发到路由选中的上游 API Key。
     * 如果请求指定了 stream=true，则返回 SSE 流式响应。
     *
     * @param body     请求体
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @return 响应结果（流式响应返回 StreamingResponseBody，非流式返回 ResponseEntity）
     */
    @PostMapping("/v1/messages")
    public Object anthropicProxy(@RequestBody Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
        Object result = proxyService.proxy(new StandaloneProxyService.ProxyRequest("anthropic", body, extractApiKey(request), request));
        // 如果是流式响应，设置 SSE 相关响应头
        if (result instanceof StreamingResponseBody srb) {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            return srb;
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 从请求头中提取 API Key。
     * 优先从 Authorization: Bearer xxx 头中提取，其次从 x-api-key 头中提取。
     *
     * @param request HTTP 请求对象
     * @return 提取到的 API Key，如果没有则返回 null
     */
    private String extractApiKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7); // 去掉 "Bearer " 前缀
        }
        return request.getHeader("x-api-key");
    }
}
