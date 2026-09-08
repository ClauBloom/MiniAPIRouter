package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteContext;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.protocol.ProtocolRegistry;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.UnifiedResponse;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.protocol.converter.ResponseConverter;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.streaming.StreamProxy;
import com.miniapi.router.core.streaming.StreamSink;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.SensitiveErrorSanitizer;
import com.miniapi.router.core.util.TraceUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Core 进程内默认实现：Plan/Execute 两阶段为核心，一步式 API 基于其组合实现。 */
@Component
@AllArgsConstructor
public class DefaultRouterCore implements RouterCore {

    private final RoutePipeline routePipeline;
    private final StreamProxy streamProxy;
    private final ProtocolRegistry protocolRegistry;

    /* ---------- 一步式 API ---------- */

    @Override
    public RouterResult proxy(RouterRequest request) {
        RoutePlan plan = plan(request);
        ResponseConverter converter = protocolRegistry.getResponseConverter(plan.inboundProtocol());
        try {
            return execute(plan);
        } catch (RouterException exception) {
            return failure(plan, converter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            return failure(plan, converter, "INTERNAL_ERROR", exception.getMessage());
        }
    }

    @Override
    public RouterResult proxyStream(RouterRequest request, OutputStream output) {
        return executeStream(plan(request), output);
    }

    /* ---------- 两阶段 API ---------- */

    @Override
    public RoutePlan plan(RouterRequest request) {
        Map<String, Object> body = request.body();
        String model = body != null ? (String) body.get("model") : null;
        if (model == null) {
            throw new RouterException("MISSING_REQUIRED_FIELD", "model is required", 400);
        }
        String protocol = request.inboundProtocol();
        String traceId = valueOr(request.traceId(), TraceUtils::newTraceId);
        String requestId = valueOr(request.requestId(), TraceUtils::newRequestId);
        RequestConverter converter = protocolRegistry.getRequestConverter(protocol);
        UnifiedRequest unified = converter.convert(body, request.clientApiKey());
        unified.setInboundProtocol(protocol);
        boolean stream = Boolean.TRUE.equals(body.get("stream"));

        RouteContext context = RouteContext.builder()
                .tenantId(request.tenantId()).traceId(traceId).requestId(requestId)
                .clientIp(request.clientIp()).inboundProtocol(protocol).model(model)
                .messages(unified.getMessages()).tools(unified.getTools())
                .systemPrompt(unified.getSystemPrompt()).parameters(unified.getExtraParams())
                .stream(stream).agentIdentity(request.agentIdentity())
                .intent(request.intentHint()).build();
        RouteResult route = routePipeline.route(context);
        ApiKeyConfig selected = route.getSelectedKey();
        String upstreamProtocol = selected.getProtocol() != null ? selected.getProtocol() : "openai";
        unified.setUpstreamProtocol(upstreamProtocol);
        unified.setModel(route.getSelectedModel() != null
                ? route.getSelectedModel() : route.resolveUpstreamModel(model));
        Map<String, Object> upstreamBody = converter.buildUpstreamRequest(unified, upstreamProtocol);
        String path = "anthropic".equalsIgnoreCase(upstreamProtocol)
                ? "/v1/messages" : "/v1/chat/completions";
        int estimatedPromptTokens = com.miniapi.router.core.streaming.TokenCounter.estimate(
                JsonUtils.toJson(upstreamBody.get("messages")));
        return new RoutePlan(route, unified, upstreamProtocol, path, upstreamBody, protocol,
                model, request.tenantId(), request.clientApiKey(), request.clientIp(),
                traceId, requestId, stream, estimatedPromptTokens);
    }

    @Override
    public RouterResult execute(RoutePlan plan) {
        ResponseConverter converter = protocolRegistry.getResponseConverter(plan.inboundProtocol());
        try {
            StreamProxy.ProxyResult result = streamProxy.proxyNonStream(
                    plan.routeResult(), plan.inboundProtocol(), plan.upstreamPath(),
                    plan.upstreamBody(), plan.unified().getModel(), plan.requestId());
            UnifiedResponse response = result.response();
            UsageStats usage = usage(response, plan.unified().getModel(), result.mappedProvider(), result.fallbackCount());
            return result(plan, converter.convert(response, plan.inboundProtocol()),
                    result.mappedProvider(), result.apiKeyId(), usage, result.fallbackCount(),
                    result.fallbackCount() > 0 ? "fallback" : "success", response.getContent(), null, null);
        } catch (RouterException exception) {
            return failure(plan, converter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            return failure(plan, converter, "INTERNAL_ERROR", exception.getMessage());
        }
    }

    @Override
    public RouterResult executeStream(RoutePlan plan, OutputStream output) {
        AtomicReference<String> terminalError = new AtomicReference<>();
        StreamProxy.StreamContext stream = streamProxy.proxyStream(new StreamProxy.StreamProxyContext(
                plan.routeResult(), plan.inboundProtocol(), plan.upstreamPath(), plan.upstreamBody(),
                plan.unified().getModel(), plan.requestId(), plan.traceId(), null), output, terminalError::set);
        return streamResult(plan, stream, terminalError);
    }

    @Override
    public RouterResult executeStream(RoutePlan plan, StreamSink sink) {
        AtomicReference<String> terminalError = new AtomicReference<>();
        StreamProxy.StreamContext stream = streamProxy.proxyStreamSink(new StreamProxy.StreamProxyContext(
                plan.routeResult(), plan.inboundProtocol(), plan.upstreamPath(), plan.upstreamBody(),
                plan.unified().getModel(), plan.requestId(), plan.traceId(), null), sink, terminalError::set);
        return streamResult(plan, stream, terminalError);
    }

    /* ---------- 内部辅助 ---------- */

    private RouterResult streamResult(RoutePlan plan, StreamProxy.StreamContext stream,
                                      AtomicReference<String> terminalError) {
        String status = stream.apiKeyId() == null ? "failed"
                : stream.clientDisconnected() ? "client_closed"
                : stream.fallbackCount() > 0 ? "fallback" : "success";
        return result(plan, null, stream.mappedProvider(), stream.apiKeyId(), stream.stats(),
                stream.fallbackCount(), status, stream.content(),
                stream.apiKeyId() == null ? "ALL_UPSTREAM_FAILED" : null,
                stream.apiKeyId() == null
                        ? valueOr(terminalError.get(), () -> "所有上游服务均不可用") : null);
    }

    private RouterResult failure(RoutePlan plan, ResponseConverter converter,
                                 String errorCode, String errorMessage) {
        ApiKeyConfig selected = plan.routeResult().getSelectedKey();
        String clientMessage = SensitiveErrorSanitizer.sanitize(errorMessage);
        return result(plan, converter.convertError(errorCode, clientMessage, plan.inboundProtocol()),
                selected != null ? selected.getProvider() : null, null, null, 0,
                "failed", null, errorCode, errorMessage);
    }

    private RouterResult result(RoutePlan plan, Map<String, Object> body,
                                String provider, Long apiKeyId, UsageStats usage, int fallbackCount,
                                String status, String responseContent,
                                String errorCode, String errorMessage) {
        RouteResult route = plan.routeResult();
        Long ruleId = route.getMatchedRule() != null ? route.getMatchedRule().getId() : null;
        return new RouterResult(body, plan.traceId(), plan.requestId(), plan.inboundProtocol(),
                plan.requestedModel(), provider, apiKeyId, ruleId, route.getIntent(), usage,
                fallbackCount, status, JsonUtils.toJson(plan.upstreamBody().get("messages")),
                responseContent, errorCode, errorMessage);
    }

    private UsageStats usage(UnifiedResponse response, String model, String provider, int fallbackCount) {
        return UsageStats.builder()
                .promptTokens(response.getPromptTokens()).completionTokens(response.getCompletionTokens())
                .totalTokens(response.getTotalTokens()).estimated(false).model(model)
                .provider(provider).fallbackCount(fallbackCount).timestamp(System.currentTimeMillis()).build();
    }

    private String valueOr(String value, java.util.function.Supplier<String> fallback) {
        return value == null || value.isBlank() ? fallback.get() : value;
    }
}
