package com.miniapi.router.core.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** 将上游协议响应解析为统一响应，不包含网络、重试或缓存职责。 */
@Component
public class UpstreamResponseParser {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();

    public UnifiedResponse parse(String body, ApiKeyConfig key, String defaultModel, String requestId) {
        JsonNode node = JsonUtils.parse(body);
        UnifiedResponse response = baseResponse(node, key, defaultModel, requestId);
        if ("openai".equalsIgnoreCase(key.getProtocol())) {
            parseOpenAi(node, response);
        } else {
            parseAnthropic(node, response);
        }
        parseUsage(node, response);
        return response;
    }

    private UnifiedResponse baseResponse(JsonNode node, ApiKeyConfig key, String defaultModel, String requestId) {
        UnifiedResponse response = new UnifiedResponse();
        response.setUpstreamProtocol(key.getProtocol());
        response.setModel(node.path("model").asText(defaultModel));
        response.setId(node.path("id").asText(requestId));
        response.setRaw(toMap(node));
        return response;
    }

    private void parseOpenAi(JsonNode node, UnifiedResponse response) {
        JsonNode choices = node.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return;
        JsonNode choice = choices.get(0);
        JsonNode message = choice.path("message");
        response.setContent(message.path("content").asText(""));
        response.setRole(message.path("role").asText("assistant"));
        response.setFinishReason(choice.path("finish_reason").asText("stop"));
        if (message.has("reasoning_content") && !message.path("reasoning_content").isNull()) {
            response.setReasoningContent(message.path("reasoning_content").asText(""));
        }
        response.setToolCalls(parseOpenAiToolCalls(message.path("tool_calls")));
    }

    /**
     * 解析 OpenAI 风格 tool_calls 为统一格式（原样保留字段，arguments 保持 JSON 字符串）。
     */
    private List<Map<String, Object>> parseOpenAiToolCalls(JsonNode toolCalls) {
        if (!toolCalls.isArray() || toolCalls.isEmpty()) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode tc : toolCalls) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", tc.path("id").asText(""));
            item.put("type", tc.path("type").asText("function"));
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tc.path("function").path("name").asText(""));
            JsonNode args = tc.path("function").path("arguments");
            fn.put("arguments", args.isMissingNode() ? "{}" : args.asText("{}"));
            item.put("function", fn);
            result.add(item);
        }
        return result;
    }

    private void parseAnthropic(JsonNode node, UnifiedResponse response) {
        JsonNode content = node.path("content");
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> blocks = new ArrayList<>();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                blocks.add(toMap(block));
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                } else if ("tool_use".equals(block.path("type").asText())) {
                    /* Anthropic tool_use 块归一化为 OpenAI 风格，供跨协议输出与 Spring AI 使用 */
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", block.path("id").asText(""));
                    item.put("type", "function");
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", block.path("name").asText(""));
                    fn.put("arguments", JsonUtils.toJson(toMap(block.path("input"))));
                    item.put("function", fn);
                    toolCalls.add(item);
                }
            }
        }
        response.setContent(text.toString());
        response.setContentBlocks(blocks.isEmpty() ? null : blocks);
        response.setToolCalls(toolCalls.isEmpty() ? null : toolCalls);
        response.setRole("assistant");
        response.setFinishReason(mapAnthropicStop(node.path("stop_reason").asText("end_turn")));
    }

    private void parseUsage(JsonNode node, UnifiedResponse response) {
        JsonNode usage = node.path("usage");
        if (usage.isMissingNode()) return;
        response.setPromptTokens(usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0)));
        response.setCompletionTokens(usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0)));
        response.setTotalTokens(usage.path("total_tokens")
                .asInt(response.getPromptTokens() + response.getCompletionTokens()));
    }

    private String mapAnthropicStop(String reason) {
        return switch (reason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> reason;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return RAW_MAPPER.convertValue(node, Map.class);
    }
}
