package com.miniapi.router.saas.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日志持久化事件
 * <p>
 * 这是一个 Java Record 类，用于封装请求日志的完整信息，作为事件在系统中传递。
 * 当代理请求完成（无论成功或失败）后，系统会创建此事件并发布，由 {@code LogPersistConsumer} 异步消费并持久化。
 * </p>
 *
 * @param tenantId          租户ID
 * @param userId            用户ID
 * @param traceId           链路追踪ID，用于全链路追踪
 * @param requestId         请求ID，唯一标识一次请求
 * @param clientIp          客户端IP地址
 * @param protocol          入站协议（如 openai、anthropic）
 * @param model             请求的模型名称
 * @param mappedProvider    实际映射到的上游提供商
 * @param apiKeyId          使用的 API Key 配置ID
 * @param routeRuleId       匹配的路由规则ID
 * @param intent            意图分类（用于智能路由）
 * @param promptTokens      Prompt Token 数量
 * @param completionTokens  Completion Token 数量
 * @param totalTokens       总 Token 数量
 * @param latencyMs         请求总延迟（毫秒）
 * @param ttftMs            首字延迟（Time To First Token，毫秒）
 * @param fallbackCount     故障转移次数
 * @param status            请求状态（success、failed、fallback）
 * @param promptStorageUrl  Prompt 内容的存储地址
 * @param responseStorageUrl Response 内容的存储地址
 * @param errorCode         错误码（失败时）
 * @param errorMessage      错误信息（失败时）
 * @param promptContent     Prompt 原始内容
 * @param responseContent   Response 原始内容
 * @param agentId           Agent 身份标识
 * @param agentType         Agent 类型
 * @param createdAt         日志创建时间
 */
public record LogPersistEvent(
        Long tenantId,
        Long userId,
        String traceId,
        String requestId,
        String clientIp,
        String protocol,
        String model,
        String mappedProvider,
        Long apiKeyId,
        Long routeRuleId,
        String intent,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int latencyMs,
        int ttftMs,
        int fallbackCount,
        String status,
        String promptStorageUrl,
        String responseStorageUrl,
        String errorCode,
        String errorMessage,
        String promptContent,
        String responseContent,
        String agentId,
        String agentType,
        LocalDateTime createdAt
) {}
