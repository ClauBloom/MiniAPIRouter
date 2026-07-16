package com.miniapi.router.core.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniapi.router.core.domain.*;
import com.miniapi.router.core.exception.AllUpstreamFailedException;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.exception.UpstreamException;
import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * 流代理：负责将路由结果转化为对上游 AI API 的实际调用。
 * 支持两种模式：
 * <ul>
 *   <li><b>非流式代理</b>：同步调用上游，解析响应并返回统一格式</li>
 *   <li><b>流式代理</b>：通过 SSE 长连接透传上游流式输出，支持回退切换</li>
 * </ul>
 * 同时负责协议转换、Token 估算、用量上报和回退信号发射。
 */
@Component
public class StreamProxy {

    private static final Logger log = LoggerFactory.getLogger(StreamProxy.class);

    private final UpstreamStreamClient upstreamClient;      // 上游 HTTP 客户端
    private final ProtocolRegistry protocolRegistry;         // 协议注册表
    private final ReasoningContentCache reasoningCache;      // 推理内容缓存

    public StreamProxy(UpstreamStreamClient upstreamClient, ProtocolRegistry protocolRegistry,
                       ReasoningContentCache reasoningCache) {
        this.upstreamClient = upstreamClient;
        this.protocolRegistry = protocolRegistry;
        this.reasoningCache = reasoningCache;
    }

    /** 非流式代理结果 */
    public record ProxyResult(UnifiedResponse response, String mappedProvider, Long apiKeyId) {}

    /**
     * 非流式代理：按路由结果调用上游，支持回退链。
     * 依次尝试主选 Key 和回退链中的 Key，直到成功或全部失败。
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
                UpstreamStreamClient.NonStreamResult result = upstreamClient.callUpstream(key, upstreamPath, upstreamBody);
                if (result.statusCode() >= 400) {
                    lastError = new UpstreamException("Upstream " + key.getProvider() + " returned " + result.statusCode());
                    continue;  // HTTP 错误状态码则尝试下一个回退 Key
                }
                /* 将上游原生响应解析为统一格式 */
                UnifiedResponse unified = parseUpstreamResponse(result.body(), inboundProtocol, key, target.displayName(), requestId);
                reasoningCache.store(unified.getContent(), unified.getReasoningContent());
                return new ProxyResult(unified, key.getProvider(), key.getId());
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new AllUpstreamFailedException("所有上游服务均不可用: " + (lastError != null ? lastError.getMessage() : ""));
    }

    /**
     * 解析上游非流式响应为统一格式。
     * 根据 Key 的协议类型（openai/anthropic）采用不同的解析逻辑。
     */
    @SuppressWarnings("unchecked")
    private UnifiedResponse parseUpstreamResponse(String body, String inboundProtocol, ApiKeyConfig key,
                                                  String defaultModel, String requestId) {
        JsonNode node = JsonUtils.parse(body);
        UnifiedResponse resp = new UnifiedResponse();
        resp.setUpstreamProtocol(key.getProtocol());
        resp.setModel(node.path("model").asText(defaultModel));
        resp.setId(node.path("id").asText(requestId));

        /* OpenAI 协议解析：取 choices[0].message */
        if ("openai".equalsIgnoreCase(key.getProtocol())) {
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                resp.setContent(message.path("content").asText(""));
                resp.setRole(message.path("role").asText("assistant"));
                resp.setFinishReason(choices.get(0).path("finish_reason").asText("stop"));
                if (message.has("reasoning_content") && !message.path("reasoning_content").isNull()) {
                    resp.setReasoningContent(message.path("reasoning_content").asText(""));
                }
            }
        } else {
            /* Anthropic 协议解析：从 content 数组中提取文本 */
            resp.setContent(extractAnthropicContent(node));
            resp.setRole("assistant");
            resp.setFinishReason(mapAnthropicStop(node.path("stop_reason").asText("end_turn")));
        }

        /* 解析 Token 用量信息，兼容 OpenAI(prompt_tokens) 和 Anthropic(input_tokens) 两种命名 */
        JsonNode usage = node.path("usage");
        if (!usage.isMissingNode()) {
            resp.setPromptTokens(usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0)));
            resp.setCompletionTokens(usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0)));
            resp.setTotalTokens(usage.path("total_tokens").asInt(resp.getPromptTokens() + resp.getCompletionTokens()));
        }
        return resp;
    }

    /** 从 Anthropic 响应的 content 数组中提取所有 text 类型的文本块并拼接 */
    private String extractAnthropicContent(JsonNode node) {
        JsonNode content = node.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText(""));
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** 将 Anthropic 的 stop_reason 映射为 OpenAI 风格的 finish_reason */
    private String mapAnthropicStop(String reason) {
        return switch (reason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> reason;
        };
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
            int fallbackCount
    ) {}

    /**
     * 流式代理：建立上游 SSE 连接，实时解析并转换流式数据块，
     * 通过 OutputStream 输出到下游客户端。
     * 支持上游失败时的静默回退（未发送任何内容时）和显式回退信号。
     */
    public StreamContext proxyStream(StreamProxyContext ctx, OutputStream os) {
        /* 构建调用链 */
        List<RouteTarget> chain = buildChain(ctx.routeResult());

        StreamConverter streamConverter = protocolRegistry.getStreamConverter(ctx.inboundProtocol());

        /* 状态变量 */
        StringBuilder accumulated = new StringBuilder();           // 累积的文本内容
        StringBuilder accumulatedReasoning = new StringBuilder();  // 累积的推理内容
        boolean firstChunk = true;                                 // 是否首个 chunk
        int promptTokens = 0;
        int completionTokens = 0;
        long startTime = System.currentTimeMillis();
        long ttft = 0;                                             // Time To First Token
        int fallbackCount = 0;
        String mappedProvider = null;
        Long apiKeyId = null;

        for (int i = 0; i < chain.size(); i++) {
            RouteTarget target = chain.get(i);
            ApiKeyConfig key = target.key();
            ctx.upstreamBody().put("model", target.realName());
            try {
                BufferedReader reader = upstreamClient.streamUpstream(key, ctx.upstreamPath(), ctx.upstreamBody());
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
                        /* 覆盖 id/model 为请求级标识，再转换为下游协议格式输出 */
                        chunk.setId(ctx.requestId());
                        chunk.setModel(ctx.defaultModel());
                        writeChunk(os, streamConverter.toSseChunk(chunk, ctx.inboundProtocol()));
                    }
                }
                /* 缓存推理内容供后续使用 */
                reasoningCache.store(accumulated.toString(), accumulatedReasoning.toString());
                reader.close();
                mappedProvider = key.getProvider();
                apiKeyId = key.getId();
                break;  // 成功则跳出调用链
            } catch (Exception e) {
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
                    String fbChunk = streamConverter.toFallbackSseChunk(event);
                    if (fbChunk == null || fbChunk.isEmpty()) {
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
                            writeChunk(os, streamConverter.toErrorSseChunk("UPSTREAM_INTERRUPTED",
                                    "上游流式输出中断: " + e.getMessage(), ctx.traceId()));
                            break;
                        }
                    } else {
                        /* 显式回退：下发回退事件信号 */
                        writeChunk(os, fbChunk);
                        firstChunk = false;
                    }
                } else {
                    /* 所有上游均失败，发送全部失败错误 */
                    writeChunk(os, streamConverter.toErrorSseChunk("ALL_UPSTREAM_FAILED",
                            "所有上游服务均不可用: " + e.getMessage(), ctx.traceId()));
                }
            }
        }

        /* 计算并构建用量统计 */
        promptTokens = TokenCounter.estimate(JsonUtils.toJson(ctx.upstreamBody().get("messages")));
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

        /* 输出用量统计和流结束标记 */
        writeChunk(os, streamConverter.toUsageSseChunk(stats));
        writeChunk(os, streamConverter.toDoneMark(ctx.inboundProtocol()));

        return new StreamContext(ctx.requestId(), ctx.defaultModel(), mappedProvider, apiKeyId,
                accumulated.toString(), stats, fallbackCount);
    }

    /**
     * 构建调用链：主目标 + 回退链，均为 RouteTarget。
     */
    private List<RouteTarget> buildChain(RouteResult routeResult) {
        List<RouteTarget> chain = new ArrayList<>();
        ApiKeyConfig selectedKey = routeResult.getSelectedKey();
        String selectedModel = routeResult.getSelectedModel();
        // 解析主目标的 realName
        String realName = selectedModel;
        if (selectedKey.getModelMapping() != null && selectedKey.getModelMapping().containsKey(selectedModel)) {
            realName = selectedKey.getModelMapping().get(selectedModel);
        }
        chain.add(new RouteTarget(selectedKey, selectedModel, realName));
        if (routeResult.hasFallback()) {
            chain.addAll(routeResult.getFallbackChain());
        }
        return chain;
    }

    /** 将 SSE 字符串写入 OutputStream 并立即刷新 */
    private static void writeChunk(OutputStream os, String chunk) {
        try {
            os.write(chunk.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ignored) {
        }
    }
}
