package com.miniapi.router.core.springai;

import com.miniapi.router.core.api.RoutePlan;
import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterResult;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.springai.SpringAiPromptConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiResponseConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiStreamConverter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutedChatModelTest {

    private final RouterCore routerCore = mock(RouterCore.class);
    private final SpringAiPromptConverter promptConverter = new SpringAiPromptConverter(
            new com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter(
                    new com.miniapi.router.core.protocol.ReasoningContentCache()));
    private final SpringAiResponseConverter responseConverter = new SpringAiResponseConverter();
    private final SpringAiStreamConverter streamConverter = new SpringAiStreamConverter(responseConverter);

    @Test
    void callReturnsChatResponseWithRoutingMetadata() {
        RoutePlan plan = pinnedPlan();
        Map<String, Object> responseBody = Map.of(
                "id", "chatcmpl-1",
                "model", "glm-5.2",
                "choices", List.of(Map.of(
                        "index", 0,
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", "hello"))));
        UsageStats usage = UsageStats.builder().promptTokens(10).completionTokens(5).totalTokens(15).build();
        when(routerCore.execute(any())).thenReturn(
                new RouterResult(responseBody, "trace", "request", "spring-ai", "glm-5.2",
                        "zhipu", 7L, 3L, "casual_chat", usage, 0, "success", null, "hello", null, null));

        RoutedChatModel model = newModel(plan);
        ChatResponse response = model.call(new Prompt("hi"));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("hello");
        assertThat(response.getMetadata().getId()).isEqualTo("chatcmpl-1");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(15);
        assertThat((Object) response.getMetadata().get(SpringAiResponseConverter.META_PREFIX + "intent")).isEqualTo("casual_chat");
        assertThat((Object) response.getMetadata().get(SpringAiResponseConverter.META_PREFIX + "provider")).isEqualTo("zhipu");
        assertThat((Object) response.getMetadata().get(SpringAiResponseConverter.META_PREFIX + "fallbackCount")).isEqualTo(0);
    }

    @Test
    void callExecutesToolLoopAndKeepsPinnedRoute() {
        RoutePlan plan = pinnedPlan();
        /* 第一轮：模型请求调用工具 */
        Map<String, Object> toolCallBody = Map.of(
                "id", "chatcmpl-t1",
                "model", "glm-5.2",
                "choices", List.of(Map.of(
                        "index", 0,
                        "finish_reason", "tool_calls",
                        "message", Map.of(
                                "role", "assistant",
                                "content", "",
                                "tool_calls", List.of(Map.of(
                                        "id", "call-1", "type", "function",
                                        "function", Map.of("name", "getWeather", "arguments", "{\"city\":\"Hangzhou\"}")))))));
        /* 第二轮：拿到工具结果后的最终回答 */
        Map<String, Object> finalBody = Map.of(
                "id", "chatcmpl-t2",
                "model", "glm-5.2",
                "choices", List.of(Map.of(
                        "index", 0,
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", "sunny today"))));
        when(routerCore.execute(any()))
                .thenReturn(result(toolCallBody))
                .thenReturn(result(finalBody));

        ToolCallingManagerStub toolManager = new ToolCallingManagerStub();
        RoutedChatModel model = newModel(plan, toolManager);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("getWeather").description("weather").inputSchema("{}").build());

        ChatResponse response = model.call(new Prompt("weather?", RouterChatOptions.builder()
                .model("glm-5.2")
                .toolCallbacks(List.of(callback))
                .build()));

        assertThat(response.getResult().getOutput().getText()).isEqualTo("sunny today");
        /* 工具循环执行了一次 */
        assertThat(toolManager.executeCount).isEqualTo(1);
        /* 两轮都使用同一个固定 Plan（未重新路由），第二轮请求体应包含 tool 结果消息 */
        org.mockito.ArgumentCaptor<com.miniapi.router.core.api.RoutePlan> captor =
                org.mockito.ArgumentCaptor.forClass(com.miniapi.router.core.api.RoutePlan.class);
        verify(routerCore, org.mockito.Mockito.times(2)).execute(captor.capture());
        List<com.miniapi.router.core.api.RoutePlan> plans = captor.getAllValues();
        assertThat(plans.get(0).routeResult().getSelectedKey().getId()).isEqualTo(7L);
        assertThat(plans.get(1).routeResult().getSelectedKey().getId()).isEqualTo(7L);
        assertThat(plans.get(1).upstreamBody().get("model")).isEqualTo("glm-5.2");
        List<?> messages = (List<?>) plans.get(1).upstreamBody().get("messages");
        assertThat(messages).hasSize(3); /* user + assistant(tool_calls) + tool */
    }

    @Test
    void streamEmitsDeltasAndUsageTail() {
        RoutePlan plan = pinnedPlan();
        when(routerCore.executeStream(any(com.miniapi.router.core.api.RoutePlan.class),
                any(com.miniapi.router.core.streaming.StreamSink.class)))
                .thenAnswer(invocation -> {
                    com.miniapi.router.core.streaming.StreamSink sink = invocation.getArgument(1);
                    sink.onChunk(com.miniapi.router.core.protocol.UnifiedStreamChunk.builder()
                            .id("req").model("glm-5.2").deltaContent("he").timestamp(1L).build());
                    sink.onChunk(com.miniapi.router.core.protocol.UnifiedStreamChunk.builder()
                            .id("req").model("glm-5.2").deltaContent("llo").finishReason("stop").timestamp(2L).build());
                    sink.onUsage(UsageStats.builder().promptTokens(3).completionTokens(2).totalTokens(5).build());
                    sink.onComplete();
                    return new com.miniapi.router.core.streaming.StreamProxy.StreamContext(
                            "req", "glm-5.2", "zhipu", 7L, "hello",
                            UsageStats.builder().build(), 0, false);
                });

        RoutedChatModel model = newModel(plan);
        List<ChatResponse> responses = model.stream(new Prompt("hi")).collectList().block();

        assertThat(responses).isNotNull();
        String aggregated = responses.stream()
                .flatMap(r -> r.getResults().stream())
                .map(g -> g.getOutput().getText())
                .reduce("", (a, b) -> a + (b == null ? "" : b));
        assertThat(aggregated).isEqualTo("hello");
        /* 最后一个元素是 usage-only 尾块 */
        ChatResponse last = responses.get(responses.size() - 1);
        assertThat(last.getResults()).isEmpty();
        assertThat(last.getMetadata().getUsage().getTotalTokens()).isEqualTo(5);
    }

    /* ---------- 辅助 ---------- */

    private RoutedChatModel newModel(RoutePlan plan) {
        return newModel(plan, DefaultToolCallingManager.builder().build());
    }

    private RoutedChatModel newModel(RoutePlan plan, org.springframework.ai.model.tool.ToolCallingManager manager) {
        return new RoutedChatModel(routerCore, promptConverter, responseConverter, streamConverter,
                manager, new org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate(),
                plan, null);
    }

    private static RouterResult result(Map<String, Object> body) {
        return new RouterResult(body, "trace", "request", "spring-ai", "glm-5.2",
                "zhipu", 7L, 3L, "casual_chat", null, 0, "success", null, null, null, null);
    }

    private static RoutePlan pinnedPlan() {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(7L);
        key.setProvider("zhipu");
        key.setProtocol("openai");
        RouteRule rule = new RouteRule();
        rule.setId(3L);
        rule.setRuleName("auto");
        RouteResult routeResult = RouteResult.builder()
                .selectedKey(key)
                .selectedModel("glm-5.2")
                .matchedRule(rule)
                .fallbackChain(List.of())
                .strategy("direct")
                .intent("casual_chat")
                .build();
        return new RoutePlan(routeResult, new UnifiedRequest(), "openai", "/v1/chat/completions",
                Map.of("model", "glm-5.2", "messages", List.of()), "spring-ai", "glm-5.2",
                1L, null, null, "trace", "request", false, 0);
    }

    /** 记录 executeToolCalls 调用次数的桩 */
    private static class ToolCallingManagerStub implements org.springframework.ai.model.tool.ToolCallingManager {
        int executeCount = 0;

        @Override
        public List<ToolDefinition> resolveToolDefinitions(org.springframework.ai.model.tool.ToolCallingChatOptions chatOptions) {
            return chatOptions.getToolCallbacks().stream().map(cb -> cb.getToolDefinition()).toList();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            executeCount++;
            /* 构造带 ToolResponseMessage 的历史，模拟工具已执行 */
            AssistantMessage assistant = chatResponse.getResult().getOutput();
            ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            assistant.getToolCalls().get(0).id(),
                            assistant.getToolCalls().get(0).name(),
                            "sunny")))
                    .build();
            List<org.springframework.ai.chat.messages.Message> history = new java.util.ArrayList<>(prompt.getInstructions());
            history.add(assistant);
            history.add(toolResponse);
            return ToolExecutionResult.builder().conversationHistory(history).build();
        }
    }
}
