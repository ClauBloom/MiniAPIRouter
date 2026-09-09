package com.miniapi.router.core.protocol.converter.springai;

import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一流块 → Spring AI 流式 ChatResponse 的转换器（理念 A/E）。
 * <p>
 * 每个 chunk 转换为携带单个 Generation 的 {@link ChatResponse}，
 * 内容为增量文本 / 增量工具调用，供 RoutedChatModel 的 Flux 下游消费。
 * usage 由 sink 在流末尾以"空 choices + usage 元数据"的形式单独发出，
 * 与 OpenAI {@code stream_options.include_usage} 语义一致，避免聚合时重复计数。
 */
@Component
public class SpringAiStreamConverter {

    private final SpringAiResponseConverter responseConverter;

    public SpringAiStreamConverter(SpringAiResponseConverter responseConverter) {
        this.responseConverter = responseConverter;
    }

    /**
     * 转换内容 chunk（含增量工具调用）。
     *
     * @param chunk       统一流块
     * @param routingMeta 路由元信息（写入 metadata，miniapi.* 前缀）
     */
    public ChatResponse toChunkChatResponse(UnifiedStreamChunk chunk, Map<String, Object> routingMeta) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("role", "assistant");
        props.put("index", chunk.getIndex());
        /* 增量推理内容透传：对齐 DeepSeek 模块的 metadata 约定，供宿主（如 BloomHarness）渲染思考流 */
        if (chunk.getReasoningContent() != null && !chunk.getReasoningContent().isEmpty()) {
            props.put("reasoningContent", chunk.getReasoningContent());
        }
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(chunk.getDeltaContent())
                .properties(props)
                .toolCalls(toDeltaToolCalls(chunk.getToolCalls()))
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(chunk.getFinishReason() != null
                        ? responseConverter.toSpringAiFinishReason(chunk.getFinishReason()) : "")
                .build();
        Generation generation = new Generation(assistantMessage, generationMetadata);
        ChatResponseMetadata metadata = responseConverter.toMetadata(routingMeta, chunk.getId(), chunk.getModel(), null);
        return new ChatResponse(List.of(generation), metadata);
    }

    /**
     * 转换流末尾的用量块：空 choices + usage 元数据（对齐 OpenAI include_usage 语义）。
     */
    public ChatResponse toUsageChatResponse(com.miniapi.router.core.domain.UsageStats stats,
                                            Map<String, Object> routingMeta, String id, String model) {
        org.springframework.ai.chat.metadata.DefaultUsage usage = new org.springframework.ai.chat.metadata.DefaultUsage(
                stats.getPromptTokens(), stats.getCompletionTokens(), stats.getTotalTokens());
        ChatResponseMetadata metadata = responseConverter.toMetadata(routingMeta, id, model, usage);
        return new ChatResponse(List.of(), metadata);
    }

    /**
     * 增量 tool_calls（OpenAI delta 风格，含 index/id/function.name/function.arguments 片段）
     * → Spring AI ToolCall 列表。参数片段由 RoutedChatModel 的聚合器负责拼接。
     */
    public List<AssistantMessage.ToolCall> toDeltaToolCalls(List<Map<String, Object>> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return List.of();
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        for (Map<String, Object> tc : toolCalls) {
            Map<String, Object> fn = tc.get("function") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            result.add(new AssistantMessage.ToolCall(
                    tc.get("id") != null ? String.valueOf(tc.get("id")) : "",
                    tc.get("type") != null ? String.valueOf(tc.get("type")) : "function",
                    fn.get("name") != null ? String.valueOf(fn.get("name")) : "",
                    fn.get("arguments") != null ? String.valueOf(fn.get("arguments")) : ""));
        }
        return result;
    }
}
