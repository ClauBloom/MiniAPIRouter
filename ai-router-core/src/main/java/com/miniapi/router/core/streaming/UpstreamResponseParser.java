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
    }

    private void parseAnthropic(JsonNode node, UnifiedResponse response) {
        JsonNode content = node.path("content");
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode block : content) {
                blocks.add(toMap(block));
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                }
            }
        }
        response.setContent(text.toString());
        response.setContentBlocks(blocks.isEmpty() ? null : blocks);
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
