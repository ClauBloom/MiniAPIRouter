package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIResponseConverter;
import com.miniapi.router.core.protocol.converter.openai.OpenAIStreamConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.streaming.StreamSink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultRouterCorePlanExecuteTest {

    private final RoutePipeline routePipeline = mock(RoutePipeline.class);
    private final StreamProxy streamProxy = mock(StreamProxy.class);
    private final ProtocolRegistry registry = new ProtocolRegistry(
            List.of(new OpenAIRequestConverter(new ReasoningContentCache())),
            List.of(new OpenAIResponseConverter()),
            List.of(new OpenAIStreamConverter()));
    private final DefaultRouterCore router = new DefaultRouterCore(routePipeline, streamProxy, registry);

    @Test
    void planRoutesWithoutTouchingUpstream() {
        when(routePipeline.route(any())).thenReturn(routeResult());

        RoutePlan plan = router.plan(request(Map.of("model", "glm-5.2", "stream", false)));

        assertThat(plan.requestedModel()).isEqualTo("glm-5.2");
        assertThat(plan.upstreamProtocol()).isEqualTo("openai");
        assertThat(plan.upstreamPath()).isEqualTo("/v1/chat/completions");
        assertThat(plan.upstreamBody()).containsEntry("model", "glm-5.2");
        assertThat(plan.unified().getModel()).isEqualTo("glm-5.2");
        assertThat(plan.routeResult().getIntent()).isEqualTo("casual_chat");
        verifyNoInteractions(streamProxy);
    }

    @Test
    void planRequiresModel() {
        assertThatThrownBy(() -> router.plan(request(Map.of("messages", List.of()))))
                .isInstanceOf(RouterException.class)
                .hasMessageContaining("model is required");
    }

    @Test
    void planResolvesUpstreamModelFromRoute() {
        RouteResult route = routeResult();
        route.setSelectedModel("routed-display");
        when(routePipeline.route(any())).thenReturn(route);

        RoutePlan plan = router.plan(request(Map.of("model", "glm-5.2")));

        assertThat(plan.unified().getModel()).isEqualTo("routed-display");
        assertThat(plan.upstreamBody()).containsEntry("model", "routed-display");
    }

    @Test
    void executeDelegatesToStreamProxyAndConvertsResponse() {
        when(routePipeline.route(any())).thenReturn(routeResult());
        RoutePlan plan = router.plan(request(Map.of("model", "glm-5.2")));
        UnifiedResponse unified = new UnifiedResponse();
        unified.setId("chatcmpl-9");
        unified.setModel("glm-5.2");
        unified.setContent("ok");
        unified.setPromptTokens(3);
        unified.setCompletionTokens(2);
        unified.setTotalTokens(5);
        unified.setUpstreamProtocol("openai");
        when(streamProxy.proxyNonStream(any(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new StreamProxy.ProxyResult(unified, "zhipu", 7L, 1));

        RouterResult result = router.execute(plan);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo("fallback");
        assertThat(result.fallbackCount()).isEqualTo(1);
        assertThat(result.responseBody()).containsEntry("id", "chatcmpl-9");
        assertThat(result.usage().getTotalTokens()).isEqualTo(5);
        verify(streamProxy).proxyNonStream(eq(plan.routeResult()), eq("openai"),
                eq("/v1/chat/completions"), eq(plan.upstreamBody()), eq("glm-5.2"), eq("request"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeStreamSinkReceivesTypedChunks() {
        when(routePipeline.route(any())).thenReturn(routeResult());
        RoutePlan plan = router.plan(request(Map.of("model", "glm-5.2")));
        when(streamProxy.proxyStreamSink(any(), any(), any())).thenAnswer(invocation -> {
            StreamSink sink = invocation.getArgument(1);
            sink.onChunk(UnifiedStreamChunk.builder().id("request").model("glm-5.2")
                    .deltaContent("hello").timestamp(1L).build());
            sink.onUsage(UsageStats.builder().promptTokens(1).completionTokens(1).totalTokens(2).build());
            sink.onComplete();
            return new StreamProxy.StreamContext("request", "glm-5.2", "zhipu", 7L, "hello",
                    UsageStats.builder().build(), 0, false);
        });

        RecordingSink sink = new RecordingSink();
        RouterResult result = router.executeStream(plan, sink);

        assertThat(sink.chunks).hasSize(1);
        assertThat(sink.chunks.get(0).getDeltaContent()).isEqualTo("hello");
        assertThat(sink.completed).isTrue();
        assertThat(result.status()).isEqualTo("success");
        ArgumentCaptor<StreamSink> captor = ArgumentCaptor.forClass(StreamSink.class);
        verify(streamProxy, times(1)).proxyStreamSink(any(), captor.capture(), any());
        assertThat(captor.getValue()).isSameAs(sink);
    }

    @Test
    void sseOutputStreamProducesSseBytes() {
        when(routePipeline.route(any())).thenReturn(routeResult());
        RoutePlan plan = router.plan(request(Map.of("model", "glm-5.2", "stream", true)));
        when(streamProxy.proxyStream(any(), any(ByteArrayOutputStream.class), any()))
                .thenAnswer(invocation -> new StreamProxy.StreamContext(
                        "request", "glm-5.2", "zhipu", 7L, "hello",
                        UsageStats.builder().build(), 0, false));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RouterResult result = router.executeStream(plan, out);

        assertThat(result.status()).isEqualTo("success");
        /* SseStreamSink 由 proxyStream 内部创建并转发，这里只验证委托发生 */
        verify(streamProxy).proxyStream(any(StreamProxy.StreamProxyContext.class),
                eq(out), any());
    }

    private static RouterRequest request(Map<String, Object> body) {
        return new RouterRequest(1L, "openai", body, "sk-test", "127.0.0.1", null, "trace", "request");
    }

    private static RouteResult routeResult() {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(7L);
        key.setProvider("zhipu");
        key.setProtocol("openai");
        RouteRule rule = new RouteRule();
        rule.setId(3L);
        rule.setRuleName("auto");
        return RouteResult.builder()
                .selectedKey(key)
                .selectedModel("glm-5.2")
                .matchedRule(rule)
                .fallbackChain(List.of())
                .strategy("direct")
                .intent("casual_chat")
                .build();
    }

    /** 记录回调的 sink 桩 */
    private static class RecordingSink implements StreamSink {
        final List<UnifiedStreamChunk> chunks = new java.util.ArrayList<>();
        boolean completed = false;

        @Override
        public boolean onChunk(UnifiedStreamChunk chunk) {
            chunks.add(chunk);
            return true;
        }

        @Override
        public FallbackOutcome onFallback(com.miniapi.router.core.domain.FallbackEvent event) {
            return FallbackOutcome.NO_SIGNAL;
        }

        @Override
        public void onError(String errorCode, String message, String traceId) {}

        @Override
        public void onUsage(UsageStats stats) {}

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
