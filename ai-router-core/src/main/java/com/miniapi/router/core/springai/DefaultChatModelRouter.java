package com.miniapi.router.core.springai;

import com.miniapi.router.core.api.RoutePlan;
import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterRequest;
import com.miniapi.router.core.protocol.converter.springai.SpringAiPromptConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiResponseConverter;
import com.miniapi.router.core.protocol.converter.springai.SpringAiStreamConverter;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * {@link ChatModelRouter} 默认实现。
 * <p>
 * 负责把 Prompt 转为 canonical OpenAI request body，调用 {@link RouterCore#plan}
 * 完成一次路由预测，再将 {@link RoutePlan} 绑定为 {@link RoutedChatModel}。
 * 不依赖任何 Spring AI provider starter（OpenAI/Anthropic 等），因此可与项目
 * 自己的上游协议适配和多 Key 路由共存。
 */
@Component
public class DefaultChatModelRouter implements ChatModelRouter {

    private final RouterCore routerCore;
    private final SpringAiPromptConverter promptConverter;
    private final SpringAiResponseConverter responseConverter;
    private final SpringAiStreamConverter streamConverter;
    private final ToolCallingManager toolCallingManager;
    private final ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

    private final com.miniapi.router.core.protocol.ProtocolRegistry protocolRegistry;

    public DefaultChatModelRouter(RouterCore routerCore,
                                  SpringAiPromptConverter promptConverter,
                                  SpringAiResponseConverter responseConverter,
                                  SpringAiStreamConverter streamConverter,
                                  com.miniapi.router.core.protocol.ProtocolRegistry protocolRegistry,
                                  ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
                                  ObjectProvider<ToolExecutionEligibilityPredicate> predicateProvider) {
        this.routerCore = routerCore;
        this.promptConverter = promptConverter;
        this.responseConverter = responseConverter;
        this.streamConverter = streamConverter;
        this.protocolRegistry = protocolRegistry;
        this.toolCallingManager = toolCallingManagerProvider.getIfAvailable(
                () -> DefaultToolCallingManager.builder().build());
        this.toolExecutionEligibilityPredicate = predicateProvider.getIfAvailable(
                DefaultToolExecutionEligibilityPredicate::new);
    }

    /**
     * 只路由不调用：返回绑定了 RoutePlan 的 ChatModel。
     */
    @Override
    public RoutedChatModel route(Prompt prompt) {
        RouterChatOptions options = toRouterOptions(prompt.getOptions());
        List<ToolDefinition> toolDefinitions = resolveToolDefinitions(options);
        Map<String, Object> body = promptConverter.toOpenAiBody(prompt, options, toolDefinitions, false);
        RouterRequest request = new RouterRequest(
                options.getTenantId(),
                SpringAiPromptConverter.PROTOCOL,
                body,
                options.getClientApiKey(),
                null,
                options.getAgentIdentity(),
                null,
                null,
                options.getIntentHint());
        RoutePlan plan = routerCore.plan(request);
        return new RoutedChatModel(routerCore, promptConverter, responseConverter, streamConverter,
                toolCallingManager, toolExecutionEligibilityPredicate, plan, options, protocolRegistry);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return route(prompt).call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return route(prompt).stream(prompt);
    }

    private RouterChatOptions toRouterOptions(ChatOptions options) {
        if (options instanceof RouterChatOptions routerOptions) {
            return routerOptions;
        }
        if (options instanceof org.springframework.ai.model.tool.ToolCallingChatOptions toolOptions) {
            return RouterChatOptions.merge(toolOptions, null);
        }
        RouterChatOptions result = new RouterChatOptions();
        if (options != null) {
            result.setModel(options.getModel());
            result.setTemperature(options.getTemperature());
            result.setMaxTokens(options.getMaxTokens());
            result.setTopP(options.getTopP());
            result.setTopK(options.getTopK());
            result.setFrequencyPenalty(options.getFrequencyPenalty());
            result.setPresencePenalty(options.getPresencePenalty());
            result.setStopSequences(options.getStopSequences());
        }
        return result;
    }

    private List<ToolDefinition> resolveToolDefinitions(RouterChatOptions options) {
        if (options.getToolCallbacks() == null || options.getToolCallbacks().isEmpty()) {
            return List.of();
        }
        return toolCallingManager.resolveToolDefinitions(options);
    }
}
