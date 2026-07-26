package com.miniapi.router.saas.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class WebMvcConfigTest {

    @Test
    void blankAllowlistDoesNotEnableCrossOriginRequests() {
        CorsRegistry registry = mock(CorsRegistry.class);

        new WebMvcConfig("").addCorsMappings(registry);

        verifyNoInteractions(registry);
    }
}
