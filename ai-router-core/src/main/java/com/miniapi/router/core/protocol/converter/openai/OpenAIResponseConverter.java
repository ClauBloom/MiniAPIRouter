package com.miniapi.router.core.protocol.converter.openai;

import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OpenAI 协议的响应转换器。
 * <p>
 * 将内部统一响应 {@link UnifiedResponse} 转换为 OpenAI Chat Completions API 格式的响应，
 * 包含 choices 数组、message 对象和 usage 统计信息。
 * 支持推理内容（reasoning_content）的输出。
 */
@Component
public class OpenAIResponseConverter implements ResponseConverter {

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
        result.put("id", response.getId() != null ? response.getId() : "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        result.put("object", "chat.completion");
        result.put("created", System.currentTimeMillis() / 1000);
        result.put("model", response.getModel());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", response.getContent() != null ? response.getContent() : "");
        if (response.getReasoningContent() != null) {
            message.put("reasoning_content", response.getReasoningContent());
        }
        if (response.getContentBlocks() != null && !response.getContentBlocks().isEmpty()) {
            message.put("content", response.getContentBlocks());
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", response.getFinishReason() != null ? response.getFinishReason() : "stop");
        result.put("choices", List.of(choice));

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", response.getPromptTokens());
        usage.put("completion_tokens", response.getCompletionTokens());
        usage.put("total_tokens", response.getTotalTokens());
        result.put("usage", usage);
        return result;
    }

    @Override
    public Map<String, Object> convertError(String errorCode, String message, String inboundProtocol) {
        Map<String, Object> error = new LinkedHashMap<>();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("message", message);
        detail.put("type", errorCode.toLowerCase());
        detail.put("code", errorCode);
        error.put("error", detail);
        return error;
    }

    @Override
    public boolean supports(String protocol) {
        return "openai".equalsIgnoreCase(protocol);
    }
}
