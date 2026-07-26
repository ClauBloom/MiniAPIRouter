package com.miniapi.router.saas.streaming;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.RouteTarget;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.streaming.UpstreamResponseParser;
import com.miniapi.router.core.spi.UpstreamClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamProxyFallbackTest {

    @Test
    void nonStreamResultReportsSuccessfulFallbackCount() {
        UpstreamClient upstream = mock(UpstreamClient.class);
        ApiKeyConfig primary = key(1L, "first");
        ApiKeyConfig fallback = key(2L, "second");
        when(upstream.call(eq(primary), eq("/v1/chat/completions"), anyMap()))
                .thenReturn(new UpstreamClient.Response(500, "{}", Map.of()));
        when(upstream.call(eq(fallback), eq("/v1/chat/completions"), anyMap()))
                .thenReturn(new UpstreamClient.Response(200, """
                        {"id":"response","model":"real","choices":[{"message":{"role":"assistant","content":"ok"}}],
                         "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                        """, Map.of()));
        StreamProxy proxy = new StreamProxy(
                upstream, mock(ProtocolRegistry.class), mock(ReasoningContentCache.class),
                new UpstreamResponseParser(), new UpstreamCooldownTracker());
        RouteResult route = RouteResult.builder()
                .selectedKey(primary)
                .selectedModel("display")
                .fallbackChain(List.of(new RouteTarget(fallback, "display", "real")))
                .build();

        StreamProxy.ProxyResult result = proxy.proxyNonStream(
                route, "openai", "/v1/chat/completions", requestBody(), "display", "request");

        assertThat(result.apiKeyId()).isEqualTo(2L);
        assertThat(result.fallbackCount()).isEqualTo(1);
    }

    private Map<String, Object> requestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "display");
        body.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        return body;
    }

    private ApiKeyConfig key(Long id, String provider) {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(id);
        key.setProvider(provider);
        key.setProtocol("openai");
        key.setStatus(1);
        key.setModelMapping(Map.of("display", "real"));
        return key;
    }
}
