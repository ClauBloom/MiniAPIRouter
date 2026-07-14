package com.miniapi.router.core.protocol.converter.openai;

import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OpenAI 协议的请求转换器。
 * <p>
 * 负责将 OpenAI Chat Completions 格式的原始请求转换为内部统一请求 {@link UnifiedRequest}，
 * 以及将统一请求反向构建为上游 OpenAI 格式的请求体。
 * 构建上游请求时会注入缓存的推理内容（reasoning_content）以保持对话一致性。
 */
@Component
public class OpenAIRequestConverter implements RequestConverter {

    private final ReasoningContentCache reasoningCache;

    public OpenAIRequestConverter(ReasoningContentCache reasoningCache) {
        this.reasoningCache = reasoningCache;
    }

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest convert(Map<String, Object> rawRequest, String apiKey) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel((String) rawRequest.get("model"));
        req.setMessages((List<Map<String, Object>>) rawRequest.get("messages"));
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
        // 将未显式映射的参数保存到 extraParams 中
        Map<String, Object> extra = new LinkedHashMap<>(rawRequest);
        extra.remove("model");
        extra.remove("messages");
        extra.remove("temperature");
        extra.remove("max_tokens");
        extra.remove("top_p");
        extra.remove("tools");
        extra.remove("stream");
        req.setExtraParams(extra);
        req.setInboundProtocol("openai");
        return req;
    }

    @Override
    public boolean supports(String protocol) {
        return "openai".equalsIgnoreCase(protocol);
    }

    @Override
    public Map<String, Object> buildUpstreamRequest(UnifiedRequest request, String upstreamProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        // 注入缓存的推理内容，确保推理链的连续性
        body.put("messages", injectReasoningContent(request.getMessages()));
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) body.put("max_tokens", request.getMaxTokens());
        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getTools() != null) body.put("tools", request.getTools());
        body.put("stream", Boolean.TRUE.equals(request.getStream()));
        // 合并额外参数
        if (request.getExtraParams() != null) body.putAll(request.getExtraParams());
        return body;
    }

    /**
     * 为对话历史中缺少 reasoning_content 的 assistant 消息注入缓存的推理内容。
     * 当模型切换时，推理内容可能丢失，通过缓存恢复以保持推理链的连续性。
     *
     * @param messages 原始消息列表
     * @return 注入推理内容后的消息列表
     */
    private List<Map<String, Object>> injectReasoningContent(List<Map<String, Object>> messages) {
        if (messages == null) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            // 只为 assistant 消息且缺少 reasoning_content 的注入
            if ("assistant".equals(msg.get("role")) && !msg.containsKey("reasoning_content")) {
                Object contentObj = msg.get("content");
                String contentKey = contentObj instanceof String s ? s : null;
                String reasoning = reasoningCache.lookup(contentKey);
                if (reasoning != null) {
                    Map<String, Object> msgCopy = new LinkedHashMap<>(msg);
                    msgCopy.put("reasoning_content", reasoning);
                    result.add(msgCopy);
                    continue;
                }
            }
            result.add(msg);
        }
        return result;
    }
}
