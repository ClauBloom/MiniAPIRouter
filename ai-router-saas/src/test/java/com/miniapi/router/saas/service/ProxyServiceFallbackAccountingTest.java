package com.miniapi.router.saas.service;

import com.miniapi.router.core.api.RouterCore;
import com.miniapi.router.core.api.RouterRequest;
import com.miniapi.router.core.api.RouterResult;
import com.miniapi.router.core.domain.UsageStats;
import com.miniapi.router.core.spi.AgentIdentityExtractor;
import com.miniapi.router.core.spi.EventPublisher;
import com.miniapi.router.core.spi.RateLimiter;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.event.LogPersistEvent;
import com.miniapi.router.saas.mapper.TenantMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyServiceFallbackAccountingTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void successfulStreamFallbackIsLoggedAndCharged() throws Exception {
        Fixture fixture = fixture(2L, 1);
        execute(fixture.service);

        verify(fixture.tenantMapper).addQuotaUsed(10L, 3L);
        LogPersistEvent event = capturedEvent(fixture.eventPublisher);
        assertThat(event.status()).isEqualTo("fallback");
        assertThat(event.fallbackCount()).isEqualTo(1);
        assertThat(event.errorCode()).isNull();
    }

    @Test
    void failedStreamIsLoggedWithoutChargingQuota() throws Exception {
        Fixture fixture = fixture(null, 2);
        execute(fixture.service);

        verify(fixture.tenantMapper, never()).addQuotaUsed(any(), any(Long.class));
        LogPersistEvent event = capturedEvent(fixture.eventPublisher);
        assertThat(event.status()).isEqualTo("failed");
        assertThat(event.errorCode()).isEqualTo("ALL_UPSTREAM_FAILED");
    }

    private Fixture fixture(Long apiKeyId, int fallbackCount) {
        RouterCore core = mock(RouterCore.class);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        EventPublisher publisher = mock(EventPublisher.class);
        TenantDO tenant = new TenantDO();
        tenant.setId(10L);
        tenant.setStatus(1);
        when(tenantMapper.selectById(10L)).thenReturn(tenant);

        UsageStats usage = UsageStats.builder()
                .promptTokens(2).completionTokens(1).totalTokens(3)
                .fallbackCount(fallbackCount).build();
        RouterResult result = new RouterResult(null, "trace", "request", "openai", "display",
                apiKeyId == null ? null : "fallback", apiKeyId, 5L, null, usage, fallbackCount,
                apiKeyId == null ? "failed" : "fallback", "prompt", "content",
                apiKeyId == null ? "ALL_UPSTREAM_FAILED" : null,
                apiKeyId == null ? "所有上游服务均不可用" : null);
        when(core.proxyStream(any(RouterRequest.class), any(OutputStream.class))).thenReturn(result);

        ProxyService service = new ProxyService(core, tenantMapper, mock(RateLimiter.class),
                publisher, mock(AgentIdentityExtractor.class));
        return new Fixture(service, tenantMapper, publisher);
    }

    private void execute(ProxyService service) throws Exception {
        TenantContext.setTenantId(10L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "display");
        body.put("stream", true);
        Object response = service.proxy(new ProxyService.ProxyRequest(
                "openai", body, "proxy-key", mock(HttpServletRequest.class)));
        ((StreamingResponseBody) response).writeTo(new ByteArrayOutputStream());
    }

    private LogPersistEvent capturedEvent(EventPublisher publisher) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishLogEvent(captor.capture());
        return (LogPersistEvent) captor.getValue();
    }

    private record Fixture(ProxyService service, TenantMapper tenantMapper, EventPublisher eventPublisher) {}
}
