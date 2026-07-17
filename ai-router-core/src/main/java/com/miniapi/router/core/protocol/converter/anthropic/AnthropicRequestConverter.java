package com.miniapi.router.core.protocol.converter.anthropic;

import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic 协议的请求转换器。
 * <p>
 * 负责将 Anthropic 格式的原始请求转换为内部统一请求 {@link UnifiedRequest}，
 * 以及将统一请求反向构建为上游 Anthropic 格式的请求体。
 * Anthropic 协议特有的 system 字段被映射为统一请求的 systemPrompt。
 */
@Component
public class AnthropicRequestConverter implements RequestConverter {

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest convert(Map<String, Object> rawRequest, String apiKey) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel((String) rawRequest.get("model"));
        req.setMessages((List<Map<String, Object>>) rawRequest.get("messages"));
        // Anthropic 协议使用独立的 system 字段，映射为统一请求的 systemPrompt
        Object system = rawRequest.get("system");
        if (system instanceof String s) {
            req.setSystemPrompt(s);
        }
        // 当 system 为非 String 类型（如 Anthropic 结构化 system prompt 数组）时，
        // 不存入 systemPrompt，也不从 extra 中移除，使其通过 extraParams 透传
        if (rawRequest.get("temperature") != null) {
            req.setTemperature(((Number) rawRequest.get("temperature")).doubleValue());
        }
        if (rawRequest.get("max_tokens") != null) {
            req.setMaxTokens(((Number) rawRequest.get("max_tokens")).intValue());
        }
        if (rawRequest.get("top_p") != null) {
            req.setTopP(((Number) rawRequest.get("top_p")).doubleValue());
        }
        req.setTools((List<Map<String, Object>>) rawRequest.get("tools"));
        req.setStream(Boolean.TRUE.equals(rawRequest.get("stream")));
        // 将未映射的参数保存到 extraParams 中
        Map<String, Object> extra = new LinkedHashMap<>(rawRequest);
        extra.remove("model");
        extra.remove("messages");
        if (system instanceof String) {
            extra.remove("system");  // 已捕获为 systemPrompt，从 extra 移除
        }
        extra.remove("temperature");
        extra.remove("max_tokens");
        extra.remove("top_p");
        extra.remove("tools");
        extra.remove("stream");
        req.setExtraParams(extra);
        req.setInboundProtocol("anthropic");
        return req;
    }

    @Override
    public boolean supports(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol);
    }

    @Override
    public Map<String, Object> buildUpstreamRequest(UnifiedRequest request, String upstreamProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", request.getMessages());
        // system prompt 作为独立字段发送（Anthropic 协议特有）
        if (request.getSystemPrompt() != null) body.put("system", request.getSystemPrompt());
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getTools() != null) body.put("tools", request.getTools());
        body.put("stream", Boolean.TRUE.equals(request.getStream()));
        // 合并额外参数
        if (request.getExtraParams() != null) body.putAll(request.getExtraParams());
        return body;
    }
}
