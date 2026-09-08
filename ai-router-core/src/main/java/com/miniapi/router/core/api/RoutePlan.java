package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.UsageStats;

import java.util.Map;

/**
 * 路由决策快照（Route Plan）。
 * <p>
 * {@link RouterCore#plan(RouterRequest)} 只执行路由决策（规则匹配、意图评估、策略选择），
 * 不触碰上游；返回的 Plan 是不可变的"决策 + 已构建上游请求"快照，
 * 可以被 {@link RouterCore#execute(RoutePlan)} / {@link RouterCore#executeStream(RoutePlan, ...)}
 * 反复执行（例如重试），也可以被调用方用于可观测、成本预估或绑定为 Spring AI 的
 * {@code ChatModel}（见 {@code com.miniapi.router.core.springai.RoutedChatModel}）。
 *
 * @param routeResult      路由决策结果（选中的 Key / 模型 / fallback 链 / 意图 / 策略）
 * @param unified          内部统一请求（路由后已写入最终模型与上游协议）
 * @param upstreamProtocol 上游协议（openai / anthropic）
 * @param upstreamPath     上游请求路径
 * @param upstreamBody     已构建好的上游请求体（执行阶段只允许替换 model）
 * @param inboundProtocol  入站协议名（openai / anthropic / spring-ai）
 * @param requestedModel   入站请求的模型名（对外模型名）
 * @param tenantId         租户 ID
 * @param clientApiKey     客户端 API Key（仅当入站请求携带时非空）
 * @param clientIp         客户端 IP
 * @param traceId          链路追踪 ID
 * @param requestId        请求 ID
 * @param stream           是否为流式执行
 * @param estimatedPromptTokens 预估的提示词 Token 数（供 Plan 阶段的可观测使用，不调上游）
 */
public record RoutePlan(
        RouteResult routeResult,
        com.miniapi.router.core.protocol.UnifiedRequest unified,
        String upstreamProtocol,
        String upstreamPath,
        Map<String, Object> upstreamBody,
        String inboundProtocol,
        String requestedModel,
        Long tenantId,
        String clientApiKey,
        String clientIp,
        String traceId,
        String requestId,
        boolean stream,
        int estimatedPromptTokens
) {

    /**
     * 构建一个执行统计信息（失败场景下 usage 未知时使用）。
     */
    public UsageStats emptyUsage(String model, String provider) {
        return UsageStats.builder()
                .promptTokens(estimatedPromptTokens)
                .completionTokens(0)
                .totalTokens(estimatedPromptTokens)
                .estimated(true)
                .model(model)
                .provider(provider)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
