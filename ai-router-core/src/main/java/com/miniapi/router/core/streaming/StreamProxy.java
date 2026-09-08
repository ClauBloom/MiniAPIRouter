package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.*;
import com.miniapi.router.core.exception.AllUpstreamFailedException;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.spi.UpstreamClient;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.SensitiveErrorSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * 流代理：负责将路由结果转化为对上游 AI API 的实际调用。
 * 支持两种模式：
 * <ul>
 *   <li><b>非流式代理</b>：同步调用上游，解析响应并返回统一格式</li>
 *   <li><b>流式代理</b>：通过 {@link StreamSink} 逐块输出上游流式内容，支持回退切换</li>
 * </ul>
 * 同时负责协议转换、Token 估算、用量上报和回退信号发射。
 * <p>
 * 回退（failover）遵循错误分类原则：
 * 仅对<b>可回退错误</b>（限流/5xx/网络超时，见 {@link UpstreamException#isFailoverEligible(int)}）
 * 尝试下一个 Key，并向 {@link UpstreamCooldownTracker} 上报失败反馈；
 * 对请求级确定性错误（400 参数错误等）立即失败并透传真实错误，不浪费回退次数。
 * 下游断开（sink 返回 false）时立即停止消费上游流，避免浪费上游 Token。
 * </p>
 */
@Component
public class StreamProxy {

    private static final Logger log = LoggerFactory.getLogger(StreamProxy.class);

    /** 错误响应体在异常消息中的最大截取长度 */
    private static final int ERROR_BODY_SNIPPET_LEN = 500;

    private final UpstreamClient upstreamClient;             // 上游传输端口
    private final ProtocolRegistry protocolRegistry;         // 协议注册表
    private final ReasoningContentCache reasoningCache;      // 推理内容缓存
    private final UpstreamResponseParser responseParser;     // 无状态协议响应解析器
    private final UpstreamCooldownTracker cooldownTracker;   // 上游 Key 冷却追踪器

    public StreamProxy(UpstreamClient upstreamClient, ProtocolRegistry protocolRegistry,
                       ReasoningContentCache reasoningCache,
                       UpstreamResponseParser responseParser,
                       UpstreamCooldownTracker cooldownTracker) {
        this.upstreamClient = upstreamClient;
        this.protocolRegistry = protocolRegistry;
        this.reasoningCache = reasoningCache;
        this.responseParser = responseParser;
        this.cooldownTracker = cooldownTracker;
    }

    /** 非流式代理结果 */
    public record ProxyResult(UnifiedResponse response, String mappedProvider, Long apiKeyId, int fallbackCount) {}

    /**
     * 非流式代理：按路由结果调用上游，支持回退链。
     * 依次尝试主选 Key 和回退链中的 Key，直到成功或全部失败。
     * <p>
     * 请求级确定性错误（如上游 400）会立即抛出并携带上游真实状态码与响应片段，
     * 不再尝试后续 Key —— 同样的请求体发给任何上游都会得到相同错误。
     * </p>
     */
    public ProxyResult proxyNonStream(RouteResult routeResult, String inboundProtocol,
                                      String upstreamPath, Map<String, Object> upstreamBody,
                                      String defaultModel, String requestId) {
        /* 构建调用链：主目标 + 回退链，均为 RouteTarget */
        List<RouteTarget> chain = buildChain(routeResult);

        Exception lastError = null;
        for (int i = 0; i < chain.size(); i++) {
            RouteTarget target = chain.get(i);
            ApiKeyConfig key = target.key();
            upstreamBody.put("model", target.realName());
            try {
                UpstreamClient.Response result = upstreamClient.call(key, upstreamPath, upstreamBody);
                int status = result.statusCode();
                if (status >= 400) {
                    String message = "Upstream " + key.getProvider() + " returned " + status
                            + ": " + snippet(result.body());
                    /* 请求级错误：立即失败并透传，可回退错误：记录冷却反馈后尝试下一个 Key */
                    UpstreamException error = new UpstreamException(message, status);
                    if (!error.isFailoverEligible()) {
                        throw error;
                    }
                    cooldownTracker.recordFailure(key.getId());
                    lastError = error;
                    continue;
                }
                /* 将上游原生响应解析为统一格式 */
                UnifiedResponse unified = responseParser.parse(result.body(), key, target.displayName(), requestId);
                reasoningCache.store(unified.getContent(), unified.getReasoningContent());
                cooldownTracker.recordSuccess(key.getId());
                return new ProxyResult(unified, key.getProvider(), key.getId(), i);
            } catch (UpstreamException e) {
                if (!e.isFailoverEligible()) {
                    /* 换 Key 无意义的确定性错误，直接向上抛出真实错误 */
                    throw e;
                }
                cooldownTracker.recordFailure(key.getId());
                lastError = e;
            } catch (Exception e) {
                cooldownTracker.recordFailure(key.getId());
                lastError = e;
            }
        }
        throw new AllUpstreamFailedException("所有上游服务均不可用: " + (lastError != null ? lastError.getMessage() : ""));
    }

    /** 流式代理的上下文参数 */
    public record StreamProxyContext(
            RouteResult routeResult,
            String inboundProtocol,
            String upstreamPath,
            Map<String, Object> upstreamBody,
            String defaultModel,
            String requestId,
            String traceId,
            Consumer<UsageStats> usageConsumer
    ) {}

    /** 流式代理返回结果 */
    public record StreamContext(
            String requestId,
            String model,
            String mappedProvider,
            Long apiKeyId,
            String content,
            UsageStats stats,
            int fallbackCount,
            boolean clientDisconnected
    ) {}

    /**
     * SSE 便捷重载：保持 HTTP 宿主现有行为，把 chunk 转成 SSE 文本写到 {@link OutputStream}。
     */
    public StreamContext proxyStream(StreamProxyContext ctx, OutputStream os) {
        return proxyStream(ctx, os, ignored -> {});
    }

    public StreamContext proxyStream(StreamProxyContext ctx, OutputStream os,
                                     Consumer<String> terminalErrorConsumer) {
        StreamConverter converter = protocolRegistry.getStreamConverter(ctx.inboundProtocol());
        return proxyStreamSink(ctx, new SseStreamSink(os, converter, ctx.inboundProtocol()), terminalErrorConsumer);
    }

    /**
     * 流式代理：建立上游 SSE 连接，实时解析并转换为统一流块，通过 {@link StreamSink} 输出。
     * <p>
     * 支持上游失败时的静默回退（尚未发送任何内容时）和显式回退信号；
     * sink 返回 false 视为下游断开，立即终止上游消费。
     * </p>
     */
    public StreamContext proxyStreamSink(StreamProxyContext ctx, StreamSink sink) {
        return proxyStreamSink(ctx, sink, ignored -> {});
    }

    public StreamContext proxyStreamSink(StreamProxyContext ctx, StreamSink sink,
                                         Consumer<String> terminalErrorConsumer) {
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(terminalErrorConsumer, "terminalErrorConsumer");
        /* 构建调用链 */
        List<RouteTarget> chain = buildChain(ctx.routeResult());

        /* 状态变量 */
        StringBuilder accumulated = new StringBuilder();           // 累积的文本内容
        StringBuilder accumulatedReasoning = new StringBuilder();  // 累积的推理内容
        boolean firstChunk = true;                                 // 是否首个 chunk
        boolean clientDisconnected = false;                        // 客户端是否已断开
        int promptTokens = 0;
        int completionTokens = 0;
        long startTime = System.currentTimeMillis();
        long ttft = 0;                                             // Time To First Token
        int fallbackCount = 0;
        String mappedProvider = null;
        Long apiKeyId = null;
        String lastErrorMessage = null;
        Map<String, Integer> upstreamUsageFromChunks = null;      // 从流块中提取的上游真实用量

        for (int i = 0; i < chain.size() && !clientDisconnected; i++) {
            RouteTarget target = chain.get(i);
            ApiKeyConfig key = target.key();
            ctx.upstreamBody().put("model", target.realName());
            BufferedReader reader = null;
            try {
                reader = upstreamClient.stream(key, ctx.upstreamPath(), ctx.upstreamBody());
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    /* 解析每行 SSE 数据为统一流式块 */
                    Object parsed = DeltaJsonParser.parseSseLine(line, key.getProtocol(), ctx.requestId(), ctx.defaultModel());
                    if (parsed == null) continue;
                    if (parsed == DeltaJsonParser.DONE) {
                        break;  // 流结束
                    }
                    if (parsed instanceof UnifiedStreamChunk chunk) {
                        /* 记录首 Token 时间 */
                        if (ttft == 0 && (chunk.getDeltaContent() != null || chunk.getDeltaRole() != null)) {
                            ttft = System.currentTimeMillis() - startTime;
                        }
                        /* 确保首个 chunk 有 assistant 角色 */
                        if (firstChunk && chunk.getDeltaRole() == null) {
                            chunk.setDeltaRole("assistant");
                        }
                        firstChunk = false;
                        if (chunk.getDeltaContent() != null) {
                            accumulated.append(chunk.getDeltaContent());
                            completionTokens += TokenCounter.estimate(chunk.getDeltaContent());
                        }
                        if (chunk.getReasoningContent() != null) {
                            accumulatedReasoning.append(chunk.getReasoningContent());
                        }
                        // 收集上游返回的真实 usage（优于估算值）
                        if (chunk.getUpstreamUsage() != null) {
                            upstreamUsageFromChunks = chunk.getUpstreamUsage();
                        }
                        /* 覆盖 id/model 为请求级标识，交给 sink 输出 */
                        chunk.setId(ctx.requestId());
                        chunk.setModel(ctx.defaultModel());
                        if (!sink.onChunk(chunk)) {
                            /* 下游已断开：立即停止消费上游，避免继续浪费上游 Token */
                            clientDisconnected = true;
                            log.info("[StreamProxy] Client disconnected, aborting upstream consumption "
                                    + "(provider={}, {} chars sent)", key.getProvider(), accumulated.length());
                            break;
                        }
                    }
                }
                /* 缓存推理内容供后续使用 */
                reasoningCache.store(accumulated.toString(), accumulatedReasoning.toString());
                mappedProvider = key.getProvider();
                apiKeyId = key.getId();
                lastErrorMessage = null;
                cooldownTracker.recordSuccess(key.getId());
                break;  // 成功则跳出调用链
            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                boolean failoverEligible = !(e instanceof UpstreamException ue) || ue.isFailoverEligible();
                if (!failoverEligible) {
                    /* 请求级确定性错误：换 Key 无意义，透传真实错误并终止 */
                    log.warn("[StreamProxy] Non-retryable upstream error from {}, fail fast: {}",
                            key.getProvider(), e.getMessage());
                    sink.onError("UPSTREAM_ERROR", SensitiveErrorSanitizer.sanitize(e.getMessage()), ctx.traceId());
                    break;
                }
                log.warn("[StreamProxy] Retryable upstream error from {}: {}",
                        key.getProvider(), e.getMessage());
                cooldownTracker.recordFailure(key.getId());
                fallbackCount++;
                if (i < chain.size() - 1) {
                    ApiKeyConfig nextKey = chain.get(i + 1).key();
                    int maxFallback = ctx.routeResult().getMatchedRule().getMaxFallback() != null
                            ? ctx.routeResult().getMatchedRule().getMaxFallback() : 2;
                    /* 构建回退事件 */
                    FallbackEvent event = FallbackEvent.builder()
                            .reason("upstream_error")
                            .failedProvider(key.getProvider())
                            .failedKeyId(key.getId())
                            .fallbackProvider(nextKey.getProvider())
                            .fallbackKeyId(nextKey.getId())
                            .fallbackIndex(fallbackCount)
                            .maxFallback(maxFallback)
                            .partialContentLength(accumulated.length())
                            .timestamp(System.currentTimeMillis())
                            .build();
                    StreamSink.FallbackOutcome outcome = sink.onFallback(event);
                    if (outcome == StreamSink.FallbackOutcome.DISCONNECTED) {
                        clientDisconnected = true;
                        break;
                    }
                    if (outcome == StreamSink.FallbackOutcome.NO_SIGNAL) {
                        /* 静默回退：尚未发送内容，可安全切换到下一个 Key */
                        if (firstChunk) {
                            log.info("[StreamProxy] Silent fallback from {} to {} (no content sent yet)",
                                    key.getProvider(), nextKey.getProvider());
                            accumulated.setLength(0);
                            accumulatedReasoning.setLength(0);
                            completionTokens = 0;
                            ttft = 0;
                        } else {
                            /* 已发送内容，无法静默回退，发送中断错误 */
                            log.warn("[StreamProxy] Cannot fallback in {} protocol (content already sent), stopping",
                                    ctx.inboundProtocol());
                            sink.onError("UPSTREAM_INTERRUPTED",
                                    SensitiveErrorSanitizer.sanitize("上游流式输出中断: " + e.getMessage()),
                                    ctx.traceId());
                            break;
                        }
                    } else {
                        /* 显式回退：sink 已下发回退事件信号 */
                        firstChunk = false;
                    }
                } else {
                    /* 所有上游均失败，发送全部失败错误 */
                    sink.onError("ALL_UPSTREAM_FAILED",
                            SensitiveErrorSanitizer.sanitize("所有上游服务均不可用: " + e.getMessage()),
                            ctx.traceId());
                }
            } finally {
                closeQuietly(reader);
            }
        }

        /* 计算并构建用量统计，优先使用上游返回的真实用量 */
        promptTokens = TokenCounter.estimate(JsonUtils.toJson(ctx.upstreamBody().get("messages")));
        if (upstreamUsageFromChunks != null) {
            if (upstreamUsageFromChunks.containsKey("prompt_tokens")) {
                promptTokens = upstreamUsageFromChunks.get("prompt_tokens");
            }
            if (upstreamUsageFromChunks.containsKey("completion_tokens")) {
                completionTokens = upstreamUsageFromChunks.get("completion_tokens");
            } else if (upstreamUsageFromChunks.containsKey("output_tokens")) {
                completionTokens = upstreamUsageFromChunks.get("output_tokens");
            }
        }
        int totalTokens = promptTokens + completionTokens;
        int latencyMs = (int) (System.currentTimeMillis() - startTime);
        UsageStats stats = UsageStats.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .estimated(true)
                .latencyMs(latencyMs)
                .ttftMs((int) ttft)
                .model(ctx.defaultModel())
                .provider(mappedProvider)
                .fallbackCount(fallbackCount)
                .timestamp(System.currentTimeMillis())
                .build();

        if (ctx.usageConsumer() != null) {
            ctx.usageConsumer().accept(stats);
        }

        /* 输出用量统计和流结束标记（客户端已断开时跳过无效写出） */
        if (!clientDisconnected) {
            sink.onUsage(stats);
            sink.onComplete();
        }

        if (lastErrorMessage != null) {
            terminalErrorConsumer.accept(lastErrorMessage);
        }
        return new StreamContext(ctx.requestId(), ctx.defaultModel(), mappedProvider, apiKeyId,
                accumulated.toString(), stats, fallbackCount, clientDisconnected);
    }

    /**
     * 构建调用链：主目标 + 回退链，均为 RouteTarget。
     */
    private List<RouteTarget> buildChain(RouteResult routeResult) {
        List<RouteTarget> chain = new ArrayList<>();
        ApiKeyConfig selectedKey = routeResult.getSelectedKey();
        String selectedModel = routeResult.getSelectedModel();
        Map<String, String> mm = selectedKey.getModelMapping();
        String displayName = selectedModel;
        String realName;
        if (selectedModel != null && mm != null && mm.containsKey(selectedModel)) {
            realName = mm.get(selectedModel);
        } else if (mm != null && !mm.isEmpty()) {
            Map.Entry<String, String> first = mm.entrySet().iterator().next();
            displayName = first.getKey();
            realName = first.getValue();
        } else {
            realName = selectedModel;
        }
        chain.add(new RouteTarget(selectedKey, displayName, realName));
        if (routeResult.hasFallback()) {
            chain.addAll(routeResult.getFallbackChain());
        }
        return chain;
    }

    /** 截取错误响应体片段，避免异常消息过长 */
    private static String snippet(String body) {
        if (body == null) return "";
        return body.length() > ERROR_BODY_SNIPPET_LEN ? body.substring(0, ERROR_BODY_SNIPPET_LEN) : body;
    }

    /** 静默关闭上游读取流（同时中断空闲监控线程、释放底层连接） */
    private static void closeQuietly(BufferedReader reader) {
        if (reader == null) return;
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }
}
