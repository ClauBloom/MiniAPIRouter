package com.miniapi.router.core.protocol.converter.anthropic;

import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Anthropic 协议的响应转换器。
 * <p>
 * 将内部统一响应 {@link UnifiedResponse} 转换为 Anthropic Messages API 格式的响应，
 * 包括内容块（content blocks）、停止原因映射和 token 用量统计。
 * Anthropic 使用 "end_turn"、"max_tokens"、"tool_use" 等停止原因。
 */
@Component
public class AnthropicResponseConverter implements ResponseConverter {

    @Override
    public Map<String, Object> convert(UnifiedResponse response, String inboundProtocol) {
        // 当上游协议与入站协议一致时，直接透传原始响应，避免字段丢失
        if (response.getRaw() != null && response.getUpstreamProtocol() != null
                && response.getUpstreamProtocol().equalsIgnoreCase(inboundProtocol)) {
            Map<String, Object> result = new LinkedHashMap<>(response.getRaw());
            if (response.getId() != null) result.put("id", response.getId());
            result.put("model", response.getModel());
            return result;
        }

        // 跨协议转换：基于解析字段构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", response.getId() != null ? response.getId() : "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        result.put("type", "message");
        result.put("role", "assistant");
        result.put("model", response.getModel());

        List<Map<String, Object>> contentBlocks;
        if (response.getContentBlocks() != null && !response.getContentBlocks().isEmpty()) {
            contentBlocks = response.getContentBlocks();
        } else {
            String text = response.getContent() != null ? response.getContent() : "";
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", text);
            contentBlocks = List.of(block);
        }
        result.put("content", contentBlocks);
        result.put("stop_reason", mapFinishReason(response.getFinishReason()));

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("input_tokens", response.getPromptTokens());
        usage.put("output_tokens", response.getCompletionTokens());
        result.put("usage", usage);
        return result;
    }

    @Override
    public Map<String, Object> convertError(String errorCode, String message, String inboundProtocol) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "error");
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", errorCode.toLowerCase());
        error.put("message", message);
        result.put("error", error);
        return result;
    }

    /**
     * 将内部统一的停止原因映射为 Anthropic 协议格式。
     * stop -> end_turn, length -> max_tokens, tool_calls -> tool_use
     */
    private String mapFinishReason(String reason) {
        if (reason == null) return "end_turn";
        return switch (reason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> reason;
        };
    }

    @Override
    public boolean supports(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol);
    }
}
