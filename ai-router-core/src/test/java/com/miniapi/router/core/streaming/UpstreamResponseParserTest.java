package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamResponseParserTest {

    private final UpstreamResponseParser parser = new UpstreamResponseParser();

    @Test
    void parsesOpenAiResponseWithoutProxyOrTransportState() {
        ApiKeyConfig key = key("openai");

        var response = parser.parse("""
                {"id":"upstream-id","model":"real-model","choices":[{"message":{"role":"assistant","content":"hello","reasoning_content":"think"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}
                """, key, "display-model", "request-id");

        assertThat(response.getId()).isEqualTo("upstream-id");
        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getReasoningContent()).isEqualTo("think");
        assertThat(response.getTotalTokens()).isEqualTo(5);
        assertThat(response.getRaw()).containsEntry("model", "real-model");
    }

    @Test
    void parsesAnthropicContentBlocksAndMapsStopReason() {
        ApiKeyConfig key = key("anthropic");

        var response = parser.parse("""
                {"content":[{"type":"thinking","thinking":"x"},{"type":"text","text":"hello"}],"stop_reason":"tool_use","usage":{"input_tokens":4,"output_tokens":6}}
                """, key, "display-model", "request-id");

        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getContentBlocks()).hasSize(2);
        assertThat(response.getFinishReason()).isEqualTo("tool_calls");
        assertThat(response.getTotalTokens()).isEqualTo(10);
    }

    private ApiKeyConfig key(String protocol) {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setProtocol(protocol);
        return key;
    }
}
