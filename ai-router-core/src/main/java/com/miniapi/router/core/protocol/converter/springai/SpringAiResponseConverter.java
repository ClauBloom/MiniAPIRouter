package com.miniapi.router.core.protocol.converter.springai;

import com.miniapi.router.core.protocol.UnifiedResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部统一格式 → Spring AI 的出站转换器（理念 A）。
 * <p>
 * 将 {@link UnifiedResponse} 转换为 {@link ChatResponse}；
 * 路由元信息（intent / strategy / provider / apiKeyId / fallbackCount / traceId）
 * 以 {@code miniapi.*} 前缀写入 {@link ChatResponseMetadata}，
 * 让 Spring AI 应用可以观测到路由决策结果。
 */
@Component
public class SpringAiResponseConverter {

    /** 路由元信息在 ChatResponseMetadata 中的 key 前缀 */
    public static final String META_PREFIX = "miniapi.";

    /**
     * 从 RouterResult 的 OpenAI 风格响应体（{@code RouterResult.responseBody()}）
     * 构建 ChatResponse —— call() 路径使用，路由元信息取自执行结果。
     *
     * @param openAiBody OpenAI Chat Completions 格式响应体
     * @param usage      执行后的用量统计（可为 null）
     * @param routingMeta 路由元信息（miniapi.* 前缀键）
     */
    @SuppressWarnings("unchecked")
    public ChatResponse toChatResponse(Map<String, Object> openAiBody,
                                       com.miniapi.router.core.domain.UsageStats usage,
                                       Map<String, Object> routingMeta) {
        Map<String, Object> message = Map.of();
        String finishReason = "stop";
        String id = null;
        String model = null;
        if (openAiBody != null) {
            id = openAiBody.get("id") != null ? String.valueOf(openAiBody.get("id")) : null;
            model = openAiBody.get("model") != null ? String.valueOf(openAiBody.get("model")) : null;
            if (openAiBody.get("choices") instanceof List<?> choices && !choices.isEmpty()
                    && choices.get(0) instanceof Map<?, ?> choice) {
                message = choice.get("message") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
                Object fr = ((Map<String, Object>) choice).get("finish_reason");
                if (fr != null) finishReason = String.valueOf(fr);
            }
        }
        String content = message.get("content") instanceof String s ? s : null;
        List<Map<String, Object>> toolCalls = message.get("tool_calls") instanceof List<?> list
                ? (List<Map<String, Object>>) list : null;
        List<AssistantMessage.ToolCall> springAiToolCalls = toSpringAiToolCalls(toolCalls);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("role", "assistant");
        props.put("index", 0);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content)
                .properties(props)
                .toolCalls(springAiToolCalls)
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(toSpringAiFinishReason(finishReason))
                .build();
        Generation generation = new Generation(assistantMessage, generationMetadata);

        DefaultUsage springAiUsage = usage != null
                ? new DefaultUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens())
                : null;
        ChatResponseMetadata metadata = toMetadata(routingMeta, id, model, springAiUsage);
        return new ChatResponse(List.of(generation), metadata);
    }

    /**
     * 转换非流式响应。
     *
     * @param response    统一响应
     * @param routingMeta 路由元信息（可含 intent/strategy/provider/apiKeyId/fallbackCount/traceId/requestId/model）
     */
    public ChatResponse toChatResponse(UnifiedResponse response, Map<String, Object> routingMeta) {
        List<AssistantMessage.ToolCall> toolCalls = toSpringAiToolCalls(response.getToolCalls());
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("role", "assistant");
        props.put("index", 0);
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(response.getContent())
                .properties(props)
                .toolCalls(toolCalls)
                .build();

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(toSpringAiFinishReason(response.getFinishReason()))
                .build();
        Generation generation = new Generation(assistantMessage, generationMetadata);

        DefaultUsage usage = new DefaultUsage(response.getPromptTokens(), response.getCompletionTokens(),
                response.getTotalTokens());
        ChatResponseMetadata metadata = toMetadata(routingMeta,
                response.getId(), response.getModel(), usage);

        return new ChatResponse(List.of(generation), metadata);
    }

    /**
     * 构建带路由元信息的 ChatResponseMetadata。
     */
    public ChatResponseMetadata toMetadata(Map<String, Object> routingMeta, String id, String model,
                                           DefaultUsage usage) {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder()
                .id(id)
                .model(model)
                .usage(usage);
        if (routingMeta != null && !routingMeta.isEmpty()) {
            /* ChatResponseMetadata 内部使用 ConcurrentHashMap，不接受 null value；过滤掉可空的元数据项 */
            Map<String, Object> safe = new LinkedHashMap<>();
            routingMeta.forEach((key, value) -> {
                if (value != null) {
                    safe.put(key, value);
                }
            });
            if (!safe.isEmpty()) {
                builder.metadata(safe);
            }
        }
        return builder.build();
    }

    /**
     * 统一格式的 toolCalls → Spring AI ToolCall 列表。
     */
    public List<AssistantMessage.ToolCall> toSpringAiToolCalls(List<Map<String, Object>> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return List.of();
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        for (Map<String, Object> tc : toolCalls) {
            Map<String, Object> fn = tc.get("function") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            result.add(new AssistantMessage.ToolCall(
                    tc.get("id") != null ? String.valueOf(tc.get("id")) : "",
                    tc.get("type") != null ? String.valueOf(tc.get("type")) : "function",
                    fn.get("name") != null ? String.valueOf(fn.get("name")) : "",
                    fn.get("arguments") != null ? String.valueOf(fn.get("arguments")) : "{}"));
        }
        return result;
    }

    /**
     * 统一 finishReason（stop/length/tool_calls）→ Spring AI 惯用大写枚举名。
     */
    public String toSpringAiFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) return "";
        return switch (finishReason) {
            case "stop" -> "STOP";
            case "length" -> "LENGTH";
            case "tool_calls", "function_call" -> "TOOL_CALLS";
            case "content_filter" -> "CONTENT_FILTER";
            default -> finishReason.toUpperCase();
        };
    }
}
