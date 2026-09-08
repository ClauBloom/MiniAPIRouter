package com.miniapi.router.core.springai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Spring AI Prompt 路由门面：先预测路由，再返回绑定了路由结果的 {@link ChatModel}。
 */
public interface ChatModelRouter {

    /**
     * 仅执行路由决策，返回固定到该决策的 ChatModel。
     */
    RoutedChatModel route(Prompt prompt);

    /** 便捷的决策 + 非流式调用。 */
    ChatResponse call(Prompt prompt);

    /** 便捷的决策 + 流式调用。 */
    Flux<ChatResponse> stream(Prompt prompt);
}
