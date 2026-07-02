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

@Component
public class StreamProxy {

    private static final Logger log = LoggerFactory.getLogger(StreamProxy.class);

    private final UpstreamStreamClient upstreamClient;
    private final ProtocolRegistry protocolRegistry;
    private final ReasoningContentCache reasoningCache;

    public StreamProxy(UpstreamStreamClient upstreamClient, ProtocolRegistry protocolRegistry,
                       ReasoningContentCache reasoningCache) {
        this.upstreamClient = upstreamClient;
        this.protocolRegistry = protocolRegistry;
        this.reasoningCache = reasoningCache;
    }

    public record ProxyResult(UnifiedResponse response, String mappedProvider, Long apiKeyId) {}

    public ProxyResult proxyNonStream(RouteResult routeResult, String inboundProtocol,
                                      String upstreamPath, Map<String, Object> upstreamBody,
                                      String defaultModel, String requestId) {
        List<ApiKeyConfig> chain = new ArrayList<>();
        chain.add(routeResult.getSelectedKey());
        if (routeResult.hasFallback()) chain.addAll(routeResult.getFallbackChain());

        Exception lastError = null;
        for (int i = 0; i < chain.size(); i++) {
            ApiKeyConfig key = chain.get(i);
            upstreamBody.put("model", resolveEffectiveModel(key, defaultModel));
            try {
                UpstreamStreamClient.NonStreamResult result = upstreamClient.callUpstream(key, upstreamPath, upstreamBody);
                if (result.statusCode() >= 400) {
                    lastError = new UpstreamException("Upstream " + key.getProvider() + " returned " + result.statusCode());
                    continue;
                }
                UnifiedResponse unified = parseUpstreamResponse(result.body(), inboundProtocol, key, defaultModel, requestId);
                reasoningCache.store(unified.getContent(), unified.getReasoningContent());
                return new ProxyResult(unified, key.getProvider(), key.getId());
            } catch (Exception e) {
                lastError = e;
            }
        }
        throw new AllUpstreamFailedException("所有上游服务均不可用: " + (lastError != null ? lastError.getMessage() : ""));
    }

    @SuppressWarnings("unchecked")
    private UnifiedResponse parseUpstreamResponse(String body, String inboundProtocol, ApiKeyConfig key,
                                                  String defaultModel, String requestId) {
        JsonNode node = JsonUtils.parse(body);
        UnifiedResponse resp = new UnifiedResponse();
        resp.setUpstreamProtocol(key.getProtocol());
        resp.setModel(node.path("model").asText(defaultModel));
        resp.setId(node.path("id").asText(requestId));

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
            resp.setContent(extractAnthropicContent(node));
            resp.setRole("assistant");
            resp.setFinishReason(mapAnthropicStop(node.path("stop_reason").asText("end_turn")));
        }

        JsonNode usage = node.path("usage");
        if (!usage.isMissingNode()) {
            resp.setPromptTokens(usage.path("prompt_tokens").asInt(usage.path("input_tokens").asInt(0)));
            resp.setCompletionTokens(usage.path("completion_tokens").asInt(usage.path("output_tokens").asInt(0)));
            resp.setTotalTokens(usage.path("total_tokens").asInt(resp.getPromptTokens() + resp.getCompletionTokens()));
        }
        return resp;
    }

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

    private String mapAnthropicStop(String reason) {
        return switch (reason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> reason;
        };
    }

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

    public record StreamContext(
            String requestId,
            String model,
            String mappedProvider,
            Long apiKeyId,
            String content,
            UsageStats stats,
            int fallbackCount
    ) {}

    public StreamContext proxyStream(StreamProxyContext ctx, OutputStream os) {
        List<ApiKeyConfig> chain = new ArrayList<>();
        chain.add(ctx.routeResult().getSelectedKey());
        if (ctx.routeResult().hasFallback()) chain.addAll(ctx.routeResult().getFallbackChain());

        StreamConverter streamConverter = protocolRegistry.getStreamConverter(ctx.inboundProtocol());

        StringBuilder accumulated = new StringBuilder();
        StringBuilder accumulatedReasoning = new StringBuilder();
        boolean firstChunk = true;
        int promptTokens = 0;
        int completionTokens = 0;
        long startTime = System.currentTimeMillis();
        long ttft = 0;
        int fallbackCount = 0;
        String mappedProvider = null;
        Long apiKeyId = null;

        for (int i = 0; i < chain.size(); i++) {
            ApiKeyConfig key = chain.get(i);
            ctx.upstreamBody().put("model", resolveEffectiveModel(key, ctx.defaultModel()));
            try {
                BufferedReader reader = upstreamClient.streamUpstream(key, ctx.upstreamPath(), ctx.upstreamBody());
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    Object parsed = DeltaJsonParser.parseSseLine(line, key.getProtocol(), ctx.requestId(), ctx.defaultModel());
                    if (parsed == null) continue;
                    if (parsed == DeltaJsonParser.DONE) {
                        break;
                    }
                    if (parsed instanceof UnifiedStreamChunk chunk) {
                        if (ttft == 0 && (chunk.getDeltaContent() != null || chunk.getDeltaRole() != null)) {
                            ttft = System.currentTimeMillis() - startTime;
                        }
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
                        chunk.setId(ctx.requestId());
                        chunk.setModel(ctx.defaultModel());
                        writeChunk(os, streamConverter.toSseChunk(chunk, ctx.inboundProtocol()));
                    }
                }
                reasoningCache.store(accumulated.toString(), accumulatedReasoning.toString());
                reader.close();
                mappedProvider = key.getProvider();
                apiKeyId = key.getId();
                break;
            } catch (Exception e) {
                fallbackCount++;
                if (i < chain.size() - 1) {
                    ApiKeyConfig nextKey = chain.get(i + 1);
                    int maxFallback = ctx.routeResult().getMatchedRule().getMaxFallback() != null
                            ? ctx.routeResult().getMatchedRule().getMaxFallback() : 2;
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
                        if (firstChunk) {
                            log.info("[StreamProxy] Silent fallback from {} to {} (no content sent yet)",
                                    key.getProvider(), nextKey.getProvider());
                            accumulated.setLength(0);
                            accumulatedReasoning.setLength(0);
                            completionTokens = 0;
                            ttft = 0;
                        } else {
                            log.warn("[StreamProxy] Cannot fallback in {} protocol (content already sent), stopping",
                                    ctx.inboundProtocol());
                            writeChunk(os, streamConverter.toErrorSseChunk("UPSTREAM_INTERRUPTED",
                                    "上游流式输出中断: " + e.getMessage(), ctx.traceId()));
                            break;
                        }
                    } else {
                        writeChunk(os, fbChunk);
                        firstChunk = false;
                    }
                } else {
                    writeChunk(os, streamConverter.toErrorSseChunk("ALL_UPSTREAM_FAILED",
                            "所有上游服务均不可用: " + e.getMessage(), ctx.traceId()));
                }
            }
        }

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

        writeChunk(os, streamConverter.toUsageSseChunk(stats));
        writeChunk(os, streamConverter.toDoneMark(ctx.inboundProtocol()));

        return new StreamContext(ctx.requestId(), ctx.defaultModel(), mappedProvider, apiKeyId,
                accumulated.toString(), stats, fallbackCount);
    }

    private static String resolveEffectiveModel(ApiKeyConfig key, String inboundModel) {
        if (key.getModels() == null || key.getModels().isEmpty()) {
            return inboundModel;
        }
        if (inboundModel != null && key.getModels().contains(inboundModel)) {
            return inboundModel;
        }
        return key.getModels().get(0);
    }

    private static void writeChunk(OutputStream os, String chunk) {
        try {
            os.write(chunk.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException ignored) {
        }
    }
}
