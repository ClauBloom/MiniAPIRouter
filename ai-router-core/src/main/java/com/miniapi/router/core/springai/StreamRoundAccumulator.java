package com.miniapi.router.core.springai;

import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.springai.SpringAiResponseConverter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 流式聚合器：在把增量 {@code ChatResponse} 发给下游的同时，
 * 直接在 {@link UnifiedStreamChunk} 层按轮次聚合出"到目前为止"的完整响应
 * （content + tool_calls + finishReason），供 tool-calling 循环在流结束时判断与执行。
 * <p>
 * 在 chunk 层聚合的原因：OpenAI delta 的 tool_calls 携带 {@code index}
 * （同名工具的并行调用与 arguments 分片都依赖它），而 Spring AI 的
 * {@code AssistantMessage.ToolCall} 不携带 index，跨层聚合会丢失合并依据。
 */
class StreamRoundAccumulator {

    /** 工具调用增量：index -> 聚合中的调用 */
    private static final class ToolCallAcc {
        String id;
        String type;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final TreeMap<Integer, ToolCallAcc> toolCalls = new TreeMap<>();
    private String finishReason = "";
    private final SpringAiResponseConverter responseConverter;

    StreamRoundAccumulator(SpringAiResponseConverter responseConverter) {
        this.responseConverter = responseConverter;
    }

    /**
     * 累积一个原始统一流块（在 sink 转换为 ChatResponse 之前调用）。
     */
    void accumulate(UnifiedStreamChunk chunk) {
        if (chunk == null) return;
        if (chunk.getDeltaContent() != null) {
            content.append(chunk.getDeltaContent());
        }
        if (chunk.getReasoningContent() != null) {
            reasoning.append(chunk.getReasoningContent());
        }
        if (chunk.getToolCalls() != null) {
            for (Map<String, Object> tc : chunk.getToolCalls()) {
                mergeToolCall(tc);
            }
        }
        if (chunk.getFinishReason() != null && !chunk.getFinishReason().isEmpty()) {
            finishReason = responseConverter.toSpringAiFinishReason(chunk.getFinishReason());
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeToolCall(Map<String, Object> tc) {
        Object indexObj = tc.get("index");
        int index = indexObj instanceof Number n ? n.intValue() : toolCalls.size();
        ToolCallAcc acc = toolCalls.computeIfAbsent(index, k -> new ToolCallAcc());
        if (tc.get("id") != null && !String.valueOf(tc.get("id")).isEmpty()) acc.id = String.valueOf(tc.get("id"));
        if (tc.get("type") != null && !String.valueOf(tc.get("type")).isEmpty()) acc.type = String.valueOf(tc.get("type"));
        if (tc.get("function") instanceof Map<?, ?> fn) {
            Object name = ((Map<String, Object>) fn).get("name");
            if (name != null && !String.valueOf(name).isEmpty()) acc.name = String.valueOf(name);
            Object args = ((Map<String, Object>) fn).get("arguments");
            if (args != null) acc.arguments.append(args);
        }
    }

    /** 是否存在工具调用（流结束后判断） */
    boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 构建聚合后的 ChatResponse（用于 tool-calling 循环判断与执行）。
     */
    ChatResponse toChatResponse(Map<String, Object> routingMeta, String id, String model) {
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (ToolCallAcc acc : toolCalls.values()) {
            calls.add(new AssistantMessage.ToolCall(
                    acc.id != null ? acc.id : "",
                    acc.type != null ? acc.type : "function",
                    acc.name != null ? acc.name : "",
                    acc.arguments.toString()));
        }
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("role", "assistant");
        props.put("index", 0);
        /* 聚合推理内容透传（此前 reasoning 字段已累积但从未输出） */
        String aggregatedReasoning = reasoning.toString();
        if (!aggregatedReasoning.isEmpty()) {
            props.put("reasoningContent", aggregatedReasoning);
        }
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content(content.toString())
                .properties(props)
                .toolCalls(calls)
                .build();
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        Generation generation = new Generation(assistantMessage, generationMetadata);
        ChatResponseMetadata metadata = responseConverter.toMetadata(routingMeta, id, model, null);
        return new ChatResponse(List.of(generation), metadata);
    }
}
