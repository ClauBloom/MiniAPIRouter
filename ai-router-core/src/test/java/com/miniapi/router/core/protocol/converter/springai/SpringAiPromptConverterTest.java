package com.miniapi.router.core.protocol.converter.springai;

import com.miniapi.router.core.springai.RouterChatOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiPromptConverterTest {

    private final SpringAiPromptConverter converter = new SpringAiPromptConverter(
            new com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter(
                    new com.miniapi.router.core.protocol.ReasoningContentCache()));

    @Test
    void convertsAllMessageTypesToOpenAiWire() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("calling tool")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getWeather", "{\"city\":\"Hangzhou\"}")))
                .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "getWeather", "sunny")))
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("be terse"),
                new UserMessage("what weather?"),
                assistant,
                toolResponse));

        Map<String, Object> body = converter.toOpenAiBody(prompt, null, null, false);

        assertThat(body.get("model")).isEqualTo("auto");
        List<Map<String, Object>> messages = cast(body.get("messages"));
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).containsEntry("role", "system").containsEntry("content", "be terse");
        assertThat(messages.get(1)).containsEntry("role", "user").containsEntry("content", "what weather?");
        assertThat(messages.get(2)).containsEntry("role", "assistant");
        List<Map<String, Object>> toolCalls = cast(messages.get(2).get("tool_calls"));
        Map<String, Object> firstCall = toolCalls.get(0);
        assertThat(firstCall).containsEntry("id", "call-1").containsEntry("type", "function");
        Map<String, Object> fn = cast(firstCall.get("function"));
        assertThat(fn).containsEntry("name", "getWeather").containsEntry("arguments", "{\"city\":\"Hangzhou\"}");
        assertThat(messages.get(3))
                .containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call-1")
                .containsEntry("content", "sunny");
        assertThat(body.get("stream")).isEqualTo(false);
    }

    @Test
    void mapsOptionsToOpenAiParameters() {
        RouterChatOptions options = RouterChatOptions.builder()
                .model("glm-5.2").temperature(0.7).maxTokens(2048).topP(0.9)
                .stopSequences(List.of("END"))
                .extraBody(Map.of("thinking", Map.of("type", "enabled")))
                .build();
        Prompt prompt = new Prompt("hi");

        Map<String, Object> body = converter.toOpenAiBody(prompt, options, null, false);

        assertThat(body.get("model")).isEqualTo("glm-5.2");
        assertThat(body.get("temperature")).isEqualTo(0.7);
        assertThat(body.get("max_tokens")).isEqualTo(2048);
        assertThat(body.get("top_p")).isEqualTo(0.9);
        List<String> stop = cast(body.get("stop"));
        assertThat(stop).containsExactly("END");
        Map<String, Object> thinking = cast(body.get("thinking"));
        assertThat(thinking).containsEntry("type", "enabled");
    }

    @Test
    void convertsToolDefinitionsToOpenAiTools() {
        ToolDefinition definition = ToolDefinition.builder()
                .name("getWeather")
                .description("Get weather")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
                .build();

        Map<String, Object> body = converter.toOpenAiBody(new Prompt("hi"), null, List.of(definition), false);

        List<Map<String, Object>> tools = cast(body.get("tools"));
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).containsEntry("type", "function");
        Map<String, Object> fn = cast(tools.get(0).get("function"));
        assertThat(fn).containsEntry("name", "getWeather").containsEntry("description", "Get weather");
        Map<String, Object> parameters = cast(fn.get("parameters"));
        assertThat(parameters).containsKey("properties");
    }

    @Test
    void multimodalUserMessageBecomesContentParts() {
        UserMessage user = UserMessage.builder()
                .text("what is this?")
                .media(List.of(org.springframework.ai.content.Media.builder()
                        .mimeType(org.springframework.util.MimeTypeUtils.parseMimeType("image/png"))
                        .data(new byte[]{1, 2, 3})
                        .build()))
                .build();
        Prompt prompt = new Prompt(List.of(user));

        Map<String, Object> body = converter.toOpenAiBody(prompt, null, null, false);
        List<Map<String, Object>> messages = cast(body.get("messages"));
        List<Map<String, Object>> content = cast(messages.get(0).get("content"));
        assertThat(content).hasSize(2);
        assertThat(content.get(0)).containsEntry("type", "text");
        assertThat(content.get(1)).containsEntry("type", "image_url");
        Map<String, Object> imageUrl = cast(content.get(1).get("image_url"));
        assertThat(imageUrl.get("url")).asString().startsWith("data:image/png;base64,");
    }

    @Test
    void streamFlagPropagates() {
        Map<String, Object> body = converter.toOpenAiBody(new Prompt("hi"), null, null, true);
        assertThat(body.get("stream")).isEqualTo(true);
    }

    @Test
    void wireBodyLiftsToUnifiedRequest() {
        Map<String, Object> body = converter.toOpenAiBody(new Prompt("hi"),
                RouterChatOptions.builder().model("glm").temperature(0.5).build(), null, false);
        var unified = converter.toUnifiedRequest(body);
        assertThat(unified.getModel()).isEqualTo("glm");
        assertThat(unified.getTemperature()).isEqualTo(0.5);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
