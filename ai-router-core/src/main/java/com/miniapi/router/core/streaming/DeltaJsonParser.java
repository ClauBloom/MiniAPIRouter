package com.miniapi.router.core.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.util.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Delta JSON 解析器：将上游 SSE (Server-Sent Events) 流中的每一行数据解析为统一的流式块。
 * 支持 OpenAI 和 Anthropic 两种上游协议格式，将它们转换为内部统一的 {@link UnifiedStreamChunk}。
 */
public class DeltaJsonParser {

    /** 流结束标记 */
    public static final Map<String, Object> DONE = new HashMap<>();
    static { DONE.put("__done__", true); }

    /**
     * 解析单行 SSE 数据。
     * @param line SSE 行（格式通常为 "data: {...}"）
     * @param upstreamProtocol 上游协议类型（"openai" 或 "anthropic"）
     * @return UnifiedStreamChunk、DONE 标记、或 null（跳过该行）
     */
    @SuppressWarnings("unchecked")
    public static Object parseSseLine(String line, String upstreamProtocol, String defaultId, String defaultModel) {
        if (line == null || line.isEmpty()) return null;
        if (line.startsWith("event:")) return null;     // 跳过 event 行
        if (!line.startsWith("data:")) return null;

        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) return DONE;          // OpenAI 流结束标记

        try {
            JsonNode node = JsonUtils.parse(data);
            if ("openai".equalsIgnoreCase(upstreamProtocol)) {
                return parseOpenAIChunk(node, defaultId, defaultModel);
            } else {
                return parseAnthropicChunk(node, defaultId, defaultModel);
            }
        } catch (Exception e) {
            return null; // JSON 解析异常时跳过该行
        }
    }

    /**
     * 解析 OpenAI 协议的流式数据块。
     * 提取 choices[0].delta 中的 content、role、reasoning_content、
     * tool_calls 和 finish_reason。
     */
    private static UnifiedStreamChunk parseOpenAIChunk(JsonNode node, String defaultId, String defaultModel) {
        String id = node.path("id").asText(defaultId);
        String model = node.path("model").asText(defaultModel);
        JsonNode choices = node.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;

        JsonNode choice = choices.get(0);
        JsonNode delta = choice.path("delta");
        String content = delta.path("content").asText("");
        String role = delta.has("role") ? delta.get("role").asText() : null;
        String reasoningContent = delta.path("reasoning_content").asText("");
        String finishReason = choice.has("finish_reason") && !choice.path("finish_reason").isNull()
                ? choice.get("finish_reason").asText() : null;

        /* 解析工具调用增量信息 */
        List<Map<String, Object>> toolCalls = null;
        JsonNode tcNode = delta.path("tool_calls");
        if (tcNode.isArray() && tcNode.size() > 0) {
            toolCalls = new java.util.ArrayList<>();
            for (JsonNode tc : tcNode) {
                Map<String, Object> tcMap = new java.util.LinkedHashMap<>();
                tcMap.put("index", tc.path("index").asInt(0));
                tcMap.put("type", tc.path("type").asText("function"));
                if (tc.has("id") && !tc.path("id").isNull()) tcMap.put("id", tc.get("id").asText());
                Map<String, Object> fn = new java.util.LinkedHashMap<>();
                JsonNode fnNode = tc.path("function");
                if (fnNode.has("name")) fn.put("name", fnNode.get("name").asText());
                if (fnNode.has("arguments")) fn.put("arguments", fnNode.get("arguments").asText());
                if (!fn.isEmpty()) tcMap.put("function", fn);
                toolCalls.add(tcMap);
            }
        }

        /* 无有效数据时跳过 */
        if (content.isEmpty() && role == null && reasoningContent.isEmpty()
                && finishReason == null && toolCalls == null) return null;

        /* 提取上游 usage 数据（流式 OpenAI 在 stream_options.include_usage 时会返回） */
        Map<String, Integer> upstreamUsage = null;
        JsonNode usageNode = node.path("usage");
        if (!usageNode.isMissingNode() && usageNode.size() > 0) {
            upstreamUsage = new HashMap<>();
            if (usageNode.has("prompt_tokens")) upstreamUsage.put("prompt_tokens", usageNode.get("prompt_tokens").asInt());
            if (usageNode.has("completion_tokens")) upstreamUsage.put("completion_tokens", usageNode.get("completion_tokens").asInt());
            if (usageNode.has("total_tokens")) upstreamUsage.put("total_tokens", usageNode.get("total_tokens").asInt());
        }

        UnifiedStreamChunk chunk = UnifiedStreamChunk.builder()
                .id(id)
                .model(model)
                .deltaContent(content.isEmpty() ? null : content)
                .deltaRole(role)
                .reasoningContent(reasoningContent.isEmpty() ? null : reasoningContent)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .index(0)
                .upstreamUsage(upstreamUsage)
                .timestamp(System.currentTimeMillis())
                .build();
        return chunk;
    }

    /**
     * 解析 Anthropic 协议的流式 SSE 事件。
     * Anthropic 使用 type 字段区分事件类型：
     * message_start / content_block_start / content_block_delta / content_block_stop / message_delta / message_stop
     */
    private static Object parseAnthropicChunk(JsonNode node, String defaultId, String defaultModel) {
        String type = node.path("type").asText("");
        switch (type) {
            case "message_start": {
                /* 消息开始：提取消息 ID、模型，设置 assistant 角色 */
                JsonNode message = node.path("message");
                String id = message.path("id").asText(defaultId);
                String model = message.path("model").asText(defaultModel);
                return UnifiedStreamChunk.builder()
                        .id(id).model(model).deltaRole("assistant").index(0)
                        .timestamp(System.currentTimeMillis()).build();
            }
            case "content_block_start": {
                /* 内容块开始：提取块类型以支持 text/tool_use/thinking 多块输出 */
                JsonNode contentBlock = node.path("content_block");
                String blockType = contentBlock.path("type").asText("text");
                int index = node.path("index").asInt(0);
                Map<String, Object> extra = new HashMap<>();
                if (contentBlock.has("id")) extra.put("tool_use_id", contentBlock.get("id").asText());
                if (contentBlock.has("name")) extra.put("tool_name", contentBlock.get("name").asText());
                return UnifiedStreamChunk.builder()
                        .id(defaultId).model(defaultModel).contentType(blockType)
                        .index(index).extra(extra).timestamp(System.currentTimeMillis()).build();
            }
            case "content_block_delta": {
                /* 内容增量：支持 text_delta / thinking_delta / input_json_delta 多种类型 */
                JsonNode delta = node.path("delta");
                String deltaType = delta.path("type").asText("text_delta");
                int index = node.path("index").asInt(0);
                switch (deltaType) {
                    case "text_delta": {
                        String text = delta.path("text").asText("");
                        if (text.isEmpty()) return null;
                        return UnifiedStreamChunk.builder()
                                .id(defaultId).model(defaultModel).deltaContent(text).index(index)
                                .timestamp(System.currentTimeMillis()).build();
                    }
                    case "thinking_delta": {
                        String thinking = delta.path("thinking").asText("");
                        if (thinking.isEmpty()) return null;
                        return UnifiedStreamChunk.builder()
                                .id(defaultId).model(defaultModel)
                                .reasoningContent(thinking).index(index)
                                .contentType("thinking").timestamp(System.currentTimeMillis()).build();
                    }
                    case "input_json_delta": {
                        String partialJson = delta.path("partial_json").asText("");
                        if (partialJson.isEmpty()) return null;
                        Map<String, Object> extra = new HashMap<>();
                        extra.put("input_json_delta", partialJson);
                        return UnifiedStreamChunk.builder()
                                .id(defaultId).model(defaultModel).index(index)
                                .contentType("tool_use").extra(extra)
                                .timestamp(System.currentTimeMillis()).build();
                    }
                    default:
                        return null;
                }
            }
            case "content_block_stop": {
                /* 内容块结束：透传索引供下游处理 */
                int index = node.path("index").asInt(0);
                Map<String, Object> extra = new HashMap<>();
                extra.put("block_stop", true);
                return UnifiedStreamChunk.builder()
                        .id(defaultId).model(defaultModel).index(index)
                        .extra(extra).timestamp(System.currentTimeMillis()).build();
            }
            case "message_delta": {
                /* 消息结束增量：提取 stop_reason 和 usage */
                JsonNode delta = node.path("delta");
                String stopReason = delta.path("stop_reason").asText(null);
                JsonNode usageNode = node.path("usage");
                Map<String, Integer> upstreamUsage = null;
                if (!usageNode.isMissingNode() && usageNode.has("output_tokens")) {
                    upstreamUsage = new HashMap<>();
                    upstreamUsage.put("output_tokens", usageNode.get("output_tokens").asInt(0));
                }
                if (stopReason != null) {
                    String mapped = switch (stopReason) {
                        case "end_turn" -> "stop";
                        case "max_tokens" -> "length";
                        case "tool_use" -> "tool_calls";
                        default -> stopReason;
                    };
                    return UnifiedStreamChunk.builder()
                            .id(defaultId).model(defaultModel).finishReason(mapped)
                            .index(0).upstreamUsage(upstreamUsage)
                            .timestamp(System.currentTimeMillis()).build();
                }
                return null;
            }
            case "message_stop":
                return DONE;
            default:
                return null;
        }
    }
}
