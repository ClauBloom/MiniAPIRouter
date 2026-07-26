package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.security.ApiKeyAuthService;
import com.miniapi.router.saas.service.ProxyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void openAiEndpointDelegatesWithOpenAiProtocol() {
        Fixture fixture = fixture();

        Object response = fixture.controller.proxyOpenAi(
                Map.of("model", "gpt"), fixture.request, new MockHttpServletResponse());

        assertDelegation(fixture, response, "openai");
    }

    @Test
    void anthropicEndpointDelegatesWithAnthropicProtocol() {
        Fixture fixture = fixture();

        Object response = fixture.controller.proxyAnthropic(
                Map.of("model", "claude"), fixture.request, new MockHttpServletResponse());

        assertDelegation(fixture, response, "anthropic");
    }

    private Fixture fixture() {
        ProxyService proxyService = mock(ProxyService.class);
        ApiKeyAuthService authService = mock(ApiKeyAuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(authService.extractApiKey(request)).thenReturn("sk-miniapi-demo-value");
        when(authService.authenticate("sk-miniapi-demo-value"))
                .thenReturn(ApiKeyAuthService.AuthResult.success(9L, "sk-miniapi-demo-value"));
        when(proxyService.proxy(any())).thenReturn(Map.of("ok", true));
        return new Fixture(new ProxyController(proxyService, authService), proxyService, request);
    }

    private void assertDelegation(Fixture fixture, Object response, String expectedProtocol) {
        assertThat(response).isInstanceOfSatisfying(ResponseEntity.class,
                entity -> assertThat(entity.getBody()).isEqualTo(Map.of("ok", true)));
        ArgumentCaptor<ProxyService.ProxyRequest> captor = ArgumentCaptor.forClass(ProxyService.ProxyRequest.class);
        verify(fixture.proxyService).proxy(captor.capture());
        assertThat(captor.getValue().inboundProtocol()).isEqualTo(expectedProtocol);
        assertThat(captor.getValue().apiKey()).isEqualTo("sk-miniapi-demo-value");
        assertThat(TenantContext.getTenantId()).isEqualTo(9L);
    }

    private record Fixture(
            ProxyController controller,
            ProxyService proxyService,
            MockHttpServletRequest request) {
    }
}
