package com.miniapi.router.core.protocol.converter.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.domain.FallbackEvent;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 协议的流式 SSE 转换器。
 * <p>
 * 将内部统一流块 {@link UnifiedStreamChunk} 转换为 OpenAI Chat Completions SSE 格式。
 * 每个 chunk 包含 choices 数组，choices[0].delta 包含增量内容、推理内容和工具调用。
 * 使用 Jackson 序列化，确保 null 值也被包含（ALWAYS 模式），
 * 以兼容某些严格校验的 OpenAI 客户端。
 */
@Component
public class OpenAIStreamConverter implements StreamConverter {

    /** SSE 专用 ObjectMapper，始终序列化 null 值以兼容严格客户端 */
    private static final ObjectMapper SSE_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    @Override
    public String toSseChunk(UnifiedStreamChunk chunk, String inboundProtocol) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", chunk.getId());
        data.put("object", "chat.completion.chunk");
        data.put("created", chunk.getTimestamp() / 1000);
        data.put("model", chunk.getModel());
        data.put("system_fingerprint", "_miniapi_");

        // 构建增量 delta 内容
        Map<String, Object> delta = new LinkedHashMap<>();
        if (chunk.getDeltaRole() != null) {
            delta.put("role", chunk.getDeltaRole());
        }
        delta.put("content", chunk.getDeltaContent());
        if (chunk.getReasoningContent() != null) {
            delta.put("reasoning_content", chunk.getReasoningContent());
        }
        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
            delta.put("tool_calls", chunk.getToolCalls());
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", chunk.getIndex());
        choice.put("delta", delta);
        choice.put("logprobs", null);
        choice.put("finish_reason", chunk.getFinishReason());
        data.put("choices", List.of(choice));

        return "data: " + sseJson(data) + "\n\n";
    }

    @Override
    public String toDoneMark(String inboundProtocol) {
        return "data: [DONE]\n\n";
    }

    /**
     * 生成包含用量统计的 SSE 块。
     * 在流式响应的末尾发送 token 使用量信息。
     */
    @Override
    public String toUsageSseChunk(UsageStats stats) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "chatcmpl-" + Long.toHexString(stats.getTimestamp()));
        data.put("object", "chat.completion.chunk");
        data.put("created", stats.getTimestamp() / 1000);
        data.put("model", stats.getModel());
        data.put("system_fingerprint", "_miniapi_");
        data.put("choices", List.of());
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", stats.getPromptTokens());
        usage.put("completion_tokens", stats.getCompletionTokens());
        usage.put("total_tokens", stats.getTotalTokens());
        data.put("usage", usage);
        return "data: " + sseJson(data) + "\n\n";
    }

    @Override
    public String toFallbackSseChunk(FallbackEvent event) {
        return "";
    }

    @Override
    public String toErrorSseChunk(String errorCode, String message, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", errorCode.toLowerCase());
        error.put("code", errorCode);
        data.put("error", error);
        return "data: " + sseJson(data) + "\n\n";
    }

    @Override
    public boolean supports(String protocol) {
        return "openai".equalsIgnoreCase(protocol);
    }

    /**
     * 将对象序列化为 SSE 数据行的 JSON 字符串。
     * 优先使用包含 null 值的 SSE_MAPPER，失败时回退到通用 JsonUtils。
     */
    private static String sseJson(Object obj) {
        try {
            return SSE_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return JsonUtils.toJson(obj);
        }
    }
}
