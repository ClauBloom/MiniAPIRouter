package com.miniapi.router.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebMvcConfigTest {

    @Test
    void blankAllowlistDoesNotEnableCrossOriginRequests() {
        CorsRegistry registry = mock(CorsRegistry.class);

        new WebMvcConfig("").addCorsMappings(registry);

        verifyNoInteractions(registry);
    }

    @Test
    void explicitOriginsAllowRefreshCookieCredentials() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class, RETURNS_SELF);
        when(registry.addMapping("/**")).thenReturn(registration);

        new WebMvcConfig("http://localhost:5173").addCorsMappings(registry);

        verify(registration).allowedOrigins("http://localhost:5173");
        verify(registration).allowCredentials(true);
    }
}
