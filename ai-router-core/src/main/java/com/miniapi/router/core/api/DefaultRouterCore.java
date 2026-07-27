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
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.SensitiveErrorSanitizer;
import com.miniapi.router.core.util.TraceUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Core 进程内默认实现，封装协议转换、路由、上游代理和 fallback 全流程。 */
@Component
@AllArgsConstructor
public class DefaultRouterCore implements RouterCore {

    private final RoutePipeline routePipeline;
    private final StreamProxy streamProxy;
    private final ProtocolRegistry protocolRegistry;

    @Override
    public RouterResult proxy(RouterRequest request) {
        PreparedRequest prepared = prepare(request, false);
        ResponseConverter converter = protocolRegistry.getResponseConverter(prepared.protocol());
        try {
            StreamProxy.ProxyResult result = streamProxy.proxyNonStream(
                    prepared.routeResult(), prepared.protocol(), prepared.upstreamPath(),
                    prepared.upstreamBody(), prepared.model(), prepared.requestId());
            UnifiedResponse response = result.response();
            UsageStats usage = usage(response, prepared.model(), result.mappedProvider(), result.fallbackCount());
            return result(prepared, converter.convert(response, prepared.protocol()),
                    result.mappedProvider(), result.apiKeyId(), usage, result.fallbackCount(),
                    result.fallbackCount() > 0 ? "fallback" : "success", response.getContent(), null, null);
        } catch (RouterException exception) {
            return failure(prepared, converter, exception.getErrorCode(), exception.getMessage());
        } catch (Exception exception) {
            return failure(prepared, converter, "INTERNAL_ERROR", exception.getMessage());
        }
    }

    @Override
    public RouterResult proxyStream(RouterRequest request, OutputStream output) {
        PreparedRequest prepared = prepare(request, true);
        AtomicReference<String> terminalError = new AtomicReference<>();
        StreamProxy.StreamContext stream = streamProxy.proxyStream(new StreamProxy.StreamProxyContext(
                prepared.routeResult(), prepared.protocol(), prepared.upstreamPath(), prepared.upstreamBody(),
                prepared.model(), prepared.requestId(), prepared.traceId(), null), output, terminalError::set);
        String status = stream.apiKeyId() == null ? "failed"
                : stream.clientDisconnected() ? "client_closed"
                : stream.fallbackCount() > 0 ? "fallback" : "success";
        return result(prepared, null, stream.mappedProvider(), stream.apiKeyId(), stream.stats(),
                stream.fallbackCount(), status, stream.content(),
                stream.apiKeyId() == null ? "ALL_UPSTREAM_FAILED" : null,
                stream.apiKeyId() == null
                        ? valueOr(terminalError.get(), () -> "所有上游服务均不可用") : null);
    }

    private PreparedRequest prepare(RouterRequest request, boolean forceStream) {
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
        boolean stream = forceStream || Boolean.TRUE.equals(body.get("stream"));

        RouteContext context = RouteContext.builder()
                .tenantId(request.tenantId()).traceId(traceId).requestId(requestId)
                .clientIp(request.clientIp()).inboundProtocol(protocol).model(model)
                .messages(unified.getMessages()).tools(unified.getTools())
                .systemPrompt(unified.getSystemPrompt()).parameters(unified.getExtraParams())
                .stream(stream).agentIdentity(request.agentIdentity()).build();
        RouteResult route = routePipeline.route(context);
        ApiKeyConfig selected = route.getSelectedKey();
        String upstreamProtocol = selected.getProtocol() != null ? selected.getProtocol() : "openai";
        unified.setUpstreamProtocol(upstreamProtocol);
        unified.setModel(route.getSelectedModel() != null
                ? route.getSelectedModel() : route.resolveUpstreamModel(model));
        Map<String, Object> upstreamBody = converter.buildUpstreamRequest(unified, upstreamProtocol);
        String path = "anthropic".equalsIgnoreCase(upstreamProtocol)
                ? "/v1/messages" : "/v1/chat/completions";
        return new PreparedRequest(protocol, model, traceId, requestId, route, path, upstreamBody);
    }

    private RouterResult failure(PreparedRequest prepared, ResponseConverter converter,
                                 String errorCode, String errorMessage) {
        ApiKeyConfig selected = prepared.routeResult().getSelectedKey();
        String clientMessage = SensitiveErrorSanitizer.sanitize(errorMessage);
        return result(prepared, converter.convertError(errorCode, clientMessage, prepared.protocol()),
                selected != null ? selected.getProvider() : null, null, null, 0,
                "failed", null, errorCode, errorMessage);
    }

    private RouterResult result(PreparedRequest prepared, Map<String, Object> body,
                                String provider, Long apiKeyId, UsageStats usage, int fallbackCount,
                                String status, String responseContent,
                                String errorCode, String errorMessage) {
        RouteResult route = prepared.routeResult();
        Long ruleId = route.getMatchedRule() != null ? route.getMatchedRule().getId() : null;
        return new RouterResult(body, prepared.traceId(), prepared.requestId(), prepared.protocol(),
                prepared.model(), provider, apiKeyId, ruleId, route.getIntent(), usage,
                fallbackCount, status, JsonUtils.toJson(prepared.upstreamBody().get("messages")),
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

    private record PreparedRequest(
            String protocol, String model, String traceId, String requestId,
            RouteResult routeResult, String upstreamPath, Map<String, Object> upstreamBody
    ) {}
}
