package com.miniapi.router.core.springai;

import com.miniapi.router.core.api.RoutePlan;
import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterRequest;
import com.miniapi.router.core.api.RouterResult;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.protocol.converter.springai.SpringAiPromptConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiResponseConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiStreamConverter;
import com.miniapi.router.core.streaming.TokenCounter;
import com.miniapi.router.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绑定路由决策的 Spring AI {@link ChatModel}（理念 C：预测返回 ChatModel）。
 * <p>
 * 由 {@link ChatModelRouter#route(Prompt)} 创建，内部持有一份不可变的
 * {@link RoutePlan}：对任何 Prompt 的 call/stream 都<b>不再重新路由</b>，
 * 而是复用 Plan 中的路由决策（选中的 Key + 模型 + fallback 链），
 * 只根据当前 Prompt 重建上游请求体 —— 这保证 Spring AI tool-calling 循环的
 * 每一轮都落在同一上游模型上。
 * <p>
 * 与 Spring AI 1.x 其他 ChatModel 行为一致：内部接入 {@link ToolCallingManager}
 * 执行工具调用循环；{@code internalToolExecutionEnabled=false} 时透传 tool_calls。
 * 路由元信息（intent/strategy/provider 等）以 {@code miniapi.*} 前缀写入响应 metadata。
 */
public class RoutedChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(RoutedChatModel.class);

    private final RouterCore routerCore;
    private final SpringAiPromptConverter promptConverter;
    private final SpringAiResponseConverter responseConverter;
    private final SpringAiStreamConverter streamConverter;
    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;
    private final RoutePlan pinnedPlan;
    private final RouterChatOptions defaultOptions;
    private final com.miniapi.router.core.protocol.ProtocolRegistry protocolRegistry;

    RoutedChatModel(RouterCore routerCore,
                    SpringAiPromptConverter promptConverter,
                    SpringAiResponseConverter responseConverter,
                    SpringAiStreamConverter streamConverter,
                    ToolCallingManager toolCallingManager,
                    ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate,
                    RoutePlan pinnedPlan,
                    RouterChatOptions defaultOptions,
                    com.miniapi.router.core.protocol.ProtocolRegistry protocolRegistry) {
        this.routerCore = routerCore;
        this.promptConverter = promptConverter;
        this.responseConverter = responseConverter;
        this.streamConverter = streamConverter;
        this.toolCallingManager = toolCallingManager;
        this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
        this.pinnedPlan = pinnedPlan;
        this.defaultOptions = defaultOptions;
        this.protocolRegistry = protocolRegistry;
    }

    /** 路由决策快照：可读出 intent / selectedModel / provider / fallbackChain 等 */
    public RoutePlan getPlan() {
        return pinnedPlan;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions != null ? defaultOptions : new RouterChatOptions();
    }

    /* ---------- 非流式 ---------- */

    @Override
    public ChatResponse call(Prompt prompt) {
        return internalCall(prompt, null);
    }

    ChatResponse internalCall(Prompt prompt, ChatResponse previousChatResponse) {
        RouterChatOptions options = mergeOptions(prompt);
        RoutePlan plan = planFor(prompt, options, false);
        RouterResult result = routerCore.execute(plan);
        ChatResponse response = responseConverter.toChatResponse(result.responseBody(),
                result.usage(), routingMeta(result, plan));

        if (shouldExecuteTools(options, response)) {
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
            if (toolExecutionResult.returnDirect()) {
                return ChatResponse.builder()
                        .from(response)
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .build();
            }
            /* 工具结果回传模型，进入下一轮（仍使用同一 Plan 的路由决策） */
            return internalCall(new Prompt(toolExecutionResult.conversationHistory(), options), response);
        }
        return response;
    }

    /* ---------- 流式 ---------- */

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return internalStream(prompt);
    }

    private Flux<ChatResponse> internalStream(Prompt prompt) {
        RouterChatOptions options = mergeOptions(prompt);
        RoutePlan plan = planFor(prompt, options, true);
        StreamRoundAccumulator accumulator = new StreamRoundAccumulator(responseConverter);
        Map<String, Object> meta = routingMeta(plan);

        Flux<ChatResponse> first = Flux.<ChatResponse>create(emitter -> {
            ChatResponseStreamSink sink = new ChatResponseStreamSink(emitter, streamConverter, meta,
                    plan.requestId(), plan.unified().getModel(), accumulator::accumulate);
            try {
                routerCore.executeStream(plan, sink);
            } catch (RuntimeException exception) {
                emitter.error(exception);
            }
        }).doOnNext(ignored -> {
            /* 聚合在 sink 的 chunkListener 中完成；此 tap 保证订阅链感知 */
        }).subscribeOn(Schedulers.boundedElastic());

        return first.concatWith(Flux.defer(() -> {
            if (!shouldExecuteTools(options, accumulator.toChatResponse(meta, plan.requestId(), plan.unified().getModel()))) {
                return Flux.empty();
            }
            ChatResponse aggregated = accumulator.toChatResponse(meta, plan.requestId(), plan.unified().getModel());
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, aggregated);
            if (toolExecutionResult.returnDirect()) {
                return Flux.just(ChatResponse.builder()
                        .generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
                        .build());
            }
            /* 下一轮工具循环：流式继续（同一 Plan 的路由决策） */
            return internalStream(new Prompt(toolExecutionResult.conversationHistory(), options));
        }));
    }

    /* ---------- 内部辅助 ---------- */

    /**
     * 基于固定路由决策（pinned Plan）为当前 Prompt 构建 Plan：
     * 复用 RouteResult / 租户 / trace，仅重建上游请求体。
     */
    private RoutePlan planFor(Prompt prompt, RouterChatOptions options, boolean stream) {
        RouteResult routeResult = pinnedPlan.routeResult();
        String selectedModel = routeResult.getSelectedModel();
        List<ToolDefinition> toolDefinitions = resolveToolDefinitions(options);
        Map<String, Object> body = promptConverter.toOpenAiBody(prompt, options, toolDefinitions, stream);
        /* 对外模型名固定为路由选中的模型（与 HTTP 路径语义一致） */
        if (selectedModel != null && !selectedModel.isEmpty()) {
            body.put("model", selectedModel);
        }
        var unified = promptConverter.toUnifiedRequest(body);
        unified.setUpstreamProtocol(pinnedPlan.upstreamProtocol());
        unified.setInboundProtocol(SpringAiPromptConverter.PROTOCOL);
        /* 按上游协议构建真实请求体（spring-ai/openai 入站 → anthropic 等上游的协议转换在此发生），
         * 不能直接把 OpenAI wire body 发给非 OpenAI 上游（tools/messages/system 字段结构不同） */
        Map<String, Object> upstreamBody = protocolRegistry
                .getRequestConverter(pinnedPlan.upstreamProtocol())
                .buildUpstreamRequest(unified, pinnedPlan.upstreamProtocol());
        if (selectedModel != null && !selectedModel.isEmpty()) {
            upstreamBody.put("model", selectedModel);
        }
        return new RoutePlan(routeResult, unified, pinnedPlan.upstreamProtocol(), pinnedPlan.upstreamPath(),
                upstreamBody, SpringAiPromptConverter.PROTOCOL,
                pinnedPlan.requestedModel(), options.getTenantId() != null ? options.getTenantId() : pinnedPlan.tenantId(),
                options.getClientApiKey() != null ? options.getClientApiKey() : pinnedPlan.clientApiKey(),
                pinnedPlan.clientIp(), pinnedPlan.traceId(), pinnedPlan.requestId(), stream,
                TokenCounter.estimate(JsonUtils.toJson(body.get("messages"))));
    }

    private RouterChatOptions mergeOptions(Prompt prompt) {
        ChatOptions runtime = prompt.getOptions();
        ToolCallingChatOptions runtimeToolOptions = runtime instanceof ToolCallingChatOptions tcco ? tcco : null;
        return RouterChatOptions.merge(runtimeToolOptions,
                defaultOptions);
    }

    private List<ToolDefinition> resolveToolDefinitions(RouterChatOptions options) {
        if (options == null || options.getToolCallbacks().isEmpty()) {
            return List.of();
        }
        return toolCallingManager.resolveToolDefinitions(options);
    }

    private boolean shouldExecuteTools(RouterChatOptions options, ChatResponse response) {
        if (response == null || !response.hasToolCalls()) {
            return false;
        }
        return toolExecutionEligibilityPredicate.test(options, response);
    }

    /**
     * 构建路由元信息（写入 ChatResponseMetadata，miniapi.* 前缀）。
     */
    private Map<String, Object> routingMeta(RouterResult result, RoutePlan plan) {
        Map<String, Object> meta = routingMeta(plan);
        if (result != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "fallbackCount", result.fallbackCount());
            meta.put(SpringAiResponseConverter.META_PREFIX + "status", result.status());
            if (result.apiKeyId() != null) {
                meta.put(SpringAiResponseConverter.META_PREFIX + "apiKeyId", result.apiKeyId());
            }
        }
        return meta;
    }

    private Map<String, Object> routingMeta(RoutePlan plan) {
        Map<String, Object> meta = new LinkedHashMap<>();
        RouteResult route = plan.routeResult();
        if (route.getIntent() != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "intent", route.getIntent());
        }
        if (route.getStrategy() != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "strategy", route.getStrategy());
        }
        if (route.getSelectedModel() != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "model", route.getSelectedModel());
        }
        if (route.getSelectedKey() != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "provider", route.getSelectedKey().getProvider());
            meta.put(SpringAiResponseConverter.META_PREFIX + "apiKeyId", route.getSelectedKey().getId());
        }
        if (route.getMatchedRule() != null) {
            meta.put(SpringAiResponseConverter.META_PREFIX + "routeRule", route.getMatchedRule().getRuleName());
        }
        meta.put(SpringAiResponseConverter.META_PREFIX + "traceId", plan.traceId());
        meta.put(SpringAiResponseConverter.META_PREFIX + "requestId", plan.requestId());
        return meta;
    }
}
