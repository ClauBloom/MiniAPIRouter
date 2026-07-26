package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.domain.RouteTarget;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.spi.UpstreamClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StreamProxy 错误分类回退行为测试：
 * 请求级确定性错误快速失败并透传，临时性错误才走回退链并上报冷却反馈。
 */
class StreamProxyFailoverClassificationTest {

    private UpstreamClient upstream;
    private StreamConverter converter;
    private UpstreamCooldownTracker cooldownTracker;
    private StreamProxy proxy;
    private ApiKeyConfig primary;
    private ApiKeyConfig fallback;

    @BeforeEach
    void setUp() {
        upstream = mock(UpstreamClient.class);
        converter = mock(StreamConverter.class);
        ProtocolRegistry registry = mock(ProtocolRegistry.class);
        when(registry.getStreamConverter(anyString())).thenReturn(converter);
        when(converter.toSseChunk(any(), anyString()))
                .thenAnswer(inv -> "data: " + ((com.miniapi.router.core.protocol.UnifiedStreamChunk) inv.getArgument(0)).getDeltaContent() + "\n\n");
        when(converter.toUsageSseChunk(any())).thenReturn("data: usage\n\n");
        when(converter.toDoneMark(anyString())).thenReturn("data: [DONE]\n\n");
        when(converter.toErrorSseChunk(anyString(), anyString(), any()))
                .thenAnswer(inv -> "data: error:" + inv.getArgument(0) + ":" + inv.getArgument(1) + "\n\n");
        when(converter.toFallbackSseChunk(any())).thenReturn("");   // 静默回退协议

        cooldownTracker = new UpstreamCooldownTracker();
        proxy = new StreamProxy(upstream, registry, new ReasoningContentCache(),
                new UpstreamResponseParser(), cooldownTracker);
        primary = key(1L, "provider-a");
        fallback = key(2L, "provider-b");
    }

    /* ────────────────────── 非流式 ────────────────────── */

    @Test
    void nonStreamFailsFastOnDeterministic400WithoutTryingFallback() {
        when(upstream.call(eq(primary), anyString(), anyMap()))
                .thenReturn(new UpstreamClient.Response(400, "{\"error\":\"bad request\"}", Map.of()));

        assertThatThrownBy(() -> proxy.proxyNonStream(
                routeWithFallback(), "openai", "/v1/chat/completions", body(), "display", "req-1"))
                .isInstanceOfSatisfying(UpstreamException.class, error -> {
                    assertThat(error.getUpstreamStatus()).isEqualTo(400);
                    assertThat(error.getHttpStatus()).isEqualTo(400);
                    assertThat(error.getMessage()).contains("bad request");
                });

        // 关键断言：确定性错误不应再尝试回退 Key
        verify(upstream, never()).call(eq(fallback), anyString(), anyMap());
        // 请求级错误不计入冷却
        assertThat(cooldownTracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void nonStreamFailsOverOnTransient500AndReportsCooldownFeedback() {
        when(upstream.call(eq(primary), anyString(), anyMap()))
                .thenReturn(new UpstreamClient.Response(500, "{}", Map.of()));
        when(upstream.call(eq(fallback), anyString(), anyMap()))
                .thenReturn(new UpstreamClient.Response(200, """
                        {"id":"r","model":"real","choices":[{"message":{"role":"assistant","content":"ok"}}],
                         "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                        """, Map.of()));

        // 连续三次请求：主 Key 每次 500 → 累计 3 次失败进入冷却；回退 Key 每次成功
        for (int i = 0; i < 3; i++) {
            StreamProxy.ProxyResult result = proxy.proxyNonStream(
                    routeWithFallback(), "openai", "/v1/chat/completions", body(), "display", "req-" + i);
            assertThat(result.apiKeyId()).isEqualTo(2L);
            assertThat(result.fallbackCount()).isEqualTo(1);
        }

        assertThat(cooldownTracker.isCoolingDown(1L)).isTrue();
        assertThat(cooldownTracker.isCoolingDown(2L)).isFalse();
    }

    /* ────────────────────── 流式 ────────────────────── */

    @Test
    void streamFailsFastOnDeterministic400AndEmitsRealError() {
        when(upstream.stream(eq(primary), anyString(), anyMap()))
                .thenThrow(new UpstreamException("Upstream returned 400: invalid role", 400));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        StreamProxy.StreamContext result = proxy.proxyStream(streamCtx(routeWithFallback()), out);

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("error:UPSTREAM_ERROR");
        assertThat(result.apiKeyId()).isNull();
        assertThat(result.clientDisconnected()).isFalse();
        // 不尝试回退 Key，不计入冷却
        verify(upstream, never()).stream(eq(fallback), anyString(), anyMap());
        assertThat(cooldownTracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void streamMasksSensitiveDetailsAndRetainsRawFinalError() {
        String raw = "Upstream https://api.example.com/v1 from 10.0.0.5 using sk-exampleSecret123";
        when(upstream.stream(eq(primary), anyString(), anyMap()))
                .thenThrow(new UpstreamException(raw, 503));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicReference<String> terminalError = new AtomicReference<>();

        proxy.proxyStream(streamCtx(routeNoFallback()), out, terminalError::set);

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .contains("https://***.com/***")
                .contains("***.***.***.***")
                .contains("sk-***")
                .doesNotContain("api.example.com", "10.0.0.5", "exampleSecret123");
        assertThat(terminalError.get()).isEqualTo(raw);
    }

    @Test
    void streamMasksSensitiveDetailsAndRetainsRawMidStreamInterruption() {
        String raw = "connection reset by https://api.vendor.tech/private using Bearer x";
        CountingReader failingReader = new CountingReader(
                sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"a\"}}]}") + "\n") {
            @Override
            public String readLine() throws IOException {
                if (linesRead >= 1) {
                    throw new IOException(raw);
                }
                return super.readLine();
            }
        };
        when(upstream.stream(eq(primary), anyString(), anyMap())).thenReturn(failingReader);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicReference<String> terminalError = new AtomicReference<>();

        proxy.proxyStream(streamCtx(routeWithFallback()), out, terminalError::set);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("error:UPSTREAM_INTERRUPTED")
                .contains("https://***.tech/***")
                .contains("Bearer ***")
                .doesNotContain("api.vendor.tech", "Bearer x");
        assertThat(terminalError.get()).isEqualTo(raw);
        verify(upstream, never()).stream(eq(fallback), anyString(), anyMap());
    }

    @Test
    void streamSilentlyFailsOverOnTransientErrorBeforeFirstChunk() {
        when(upstream.stream(eq(primary), anyString(), anyMap()))
                .thenThrow(new UpstreamException("Upstream returned 503: overloaded", 503));
        when(upstream.stream(eq(fallback), anyString(), anyMap()))
                .thenReturn(reader(
                        sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"a\"}}]}"),
                        sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"b\"}}]}"),
                        "data: [DONE]"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AtomicReference<String> terminalError = new AtomicReference<>();

        StreamProxy.StreamContext result = proxy.proxyStream(
                streamCtx(routeWithFallback()), out, terminalError::set);

        assertThat(result.apiKeyId()).isEqualTo(2L);
        assertThat(result.fallbackCount()).isEqualTo(1);
        assertThat(result.content()).isEqualTo("ab");
        assertThat(terminalError.get()).isNull();
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("data: a").contains("data: b").contains("[DONE]");
    }

    @Test
    void streamAbortsUpstreamConsumptionWhenClientDisconnects() {
        CountingReader countingReader = new CountingReader(
                sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"a\"}}]}") + "\n"
                        + sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"b\"}}]}") + "\n"
                        + sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"content\":\"c\"}}]}") + "\n"
                        + "data: [DONE]\n");
        when(upstream.stream(eq(primary), anyString(), anyMap())).thenReturn(countingReader);
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        StreamProxy.StreamContext result = proxy.proxyStream(streamCtx(routeNoFallback()), broken);

        assertThat(result.clientDisconnected()).isTrue();
        // 第一块写出失败后立即终止：不应读完整个上游流
        assertThat(countingReader.linesRead).isLessThanOrEqualTo(2);
        assertThat(countingReader.closed).isTrue();
        // 上游本身是健康的，不计入冷却
        assertThat(cooldownTracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void streamClosesReaderEvenOnMidStreamFailure() {
        CountingReader failingReader = new CountingReader(
                sse("{\"id\":\"1\",\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"a\"}}]}") + "\n") {
            @Override
            public String readLine() throws IOException {
                if (linesRead >= 1) {
                    throw new IOException("connection reset");
                }
                return super.readLine();
            }
        };
        when(upstream.stream(eq(primary), anyString(), anyMap())).thenReturn(failingReader);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        proxy.proxyStream(streamCtx(routeNoFallback()), out);

        assertThat(failingReader.closed).isTrue();
    }

    /* ────────────────────── helpers ────────────────────── */

    private static class CountingReader extends BufferedReader {
        int linesRead;
        boolean closed;

        CountingReader(String content) {
            super(new StringReader(content));
        }

        @Override
        public String readLine() throws IOException {
            String line = super.readLine();
            if (line != null) {
                linesRead++;
            }
            return line;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static String sse(String json) {
        return "data: " + json;
    }

    private static BufferedReader reader(String... lines) {
        return new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
    }

    private StreamProxy.StreamProxyContext streamCtx(RouteResult route) {
        return new StreamProxy.StreamProxyContext(
                route, "openai", "/v1/chat/completions", body(), "display", "req-s", "trace-s", null);
    }

    private RouteResult routeWithFallback() {
        return RouteResult.builder()
                .selectedKey(primary)
                .selectedModel("display")
                .matchedRule(rule())
                .fallbackChain(List.of(new RouteTarget(fallback, "display", "real")))
                .build();
    }

    private RouteResult routeNoFallback() {
        return RouteResult.builder()
                .selectedKey(primary)
                .selectedModel("display")
                .matchedRule(rule())
                .fallbackChain(List.of())
                .build();
    }

    private RouteRule rule() {
        RouteRule rule = new RouteRule();
        rule.setId(1L);
        rule.setMaxFallback(2);
        return rule;
    }

    private Map<String, Object> body() {
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
