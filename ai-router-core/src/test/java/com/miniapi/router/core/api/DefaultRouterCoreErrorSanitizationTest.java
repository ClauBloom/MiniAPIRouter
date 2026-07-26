package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIResponseConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.streaming.StreamProxy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRouterCoreErrorSanitizationTest {

    @Test
    @SuppressWarnings("unchecked")
    void masksClientErrorBodyButPreservesRawInternalMessage() {
        RoutePipeline routePipeline = mock(RoutePipeline.class);
        StreamProxy streamProxy = mock(StreamProxy.class);
        ReasoningContentCache reasoningCache = new ReasoningContentCache();
        ProtocolRegistry registry = new ProtocolRegistry(
                List.of(new OpenAIRequestConverter(reasoningCache)),
                List.of(new OpenAIResponseConverter()),
                List.of());
        ApiKeyConfig key = key();
        RouteResult route = RouteResult.builder()
                .selectedKey(key)
                .selectedModel("display")
                .fallbackChain(List.of())
                .build();
        String raw = "Upstream https://api.example.com/v1 failed for sk-exampleSecret123";

        when(routePipeline.route(any())).thenReturn(route);
        when(streamProxy.proxyNonStream(
                any(), anyString(), anyString(), anyMap(), anyString(), anyString()))
                .thenThrow(new UpstreamException(raw, 400));

        DefaultRouterCore router = new DefaultRouterCore(routePipeline, streamProxy, registry);
        RouterResult result = router.proxy(new RouterRequest(
                1L, "openai", requestBody(), null, "127.0.0.1", null, "trace", "request"));

        Map<String, Object> error = (Map<String, Object>) result.responseBody().get("error");
        assertThat(error.get("message"))
                .isEqualTo("Upstream https://***.com/*** failed for sk-***");
        assertThat(result.errorMessage()).isEqualTo(raw);
    }

    @Test
    void preservesRawStreamErrorInRouterResult() {
        RoutePipeline routePipeline = mock(RoutePipeline.class);
        StreamProxy streamProxy = mock(StreamProxy.class);
        ReasoningContentCache reasoningCache = new ReasoningContentCache();
        ProtocolRegistry registry = new ProtocolRegistry(
                List.of(new OpenAIRequestConverter(reasoningCache)),
                List.of(new OpenAIResponseConverter()),
                List.of());
        RouteResult route = RouteResult.builder()
                .selectedKey(key())
                .selectedModel("display")
                .fallbackChain(List.of())
                .build();
        String raw = "connection reset by https://api.vendor.tech/private";

        when(routePipeline.route(any())).thenReturn(route);
        when(streamProxy.proxyStream(any(), any(), any())).thenAnswer(invocation -> {
            Consumer<String> errorConsumer = invocation.getArgument(2);
            errorConsumer.accept(raw);
            return new StreamProxy.StreamContext(
                    "request", "display", null, null, "", null, 1, false);
        });

        DefaultRouterCore router = new DefaultRouterCore(routePipeline, streamProxy, registry);
        RouterResult result = router.proxyStream(new RouterRequest(
                1L, "openai", requestBody(), null, "127.0.0.1", null, "trace", "request"),
                new ByteArrayOutputStream());

        assertThat(result.errorMessage()).isEqualTo(raw);
    }

    private ApiKeyConfig key() {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(1L);
        key.setTenantId(1L);
        key.setProvider("provider-a");
        key.setProtocol("openai");
        key.setStatus(1);
        key.setModelMapping(Map.of("display", "real"));
        return key;
    }

    private Map<String, Object> requestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "display");
        body.put("messages", List.of(Map.of("role", "user", "content", "hello")));
        return body;
    }
}
