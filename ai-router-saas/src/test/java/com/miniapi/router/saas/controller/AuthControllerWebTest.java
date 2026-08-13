package com.miniapi.router.saas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.saas.dto.response.AuthSessionResponse;
import com.miniapi.router.saas.dto.response.CurrentUserResponse;
import com.miniapi.router.saas.security.RefreshCookieFactory;
import com.miniapi.router.saas.security.SaasPrincipal;
import com.miniapi.router.saas.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerWebTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private CurrentUserResponse user;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        user = new CurrentUserResponse(2L, "demo_admin", "Demo Admin", "tenant_admin",
                1L, "Demo", Set.of("tenant:routing:read"));
        AuthController controller = new AuthController(
                authService, new RefreshCookieFactory(false, 2_592_000_000L));
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new com.miniapi.router.saas.handler.GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        objectMapper))
                .build();
    }

    @Test
    void loginSetsHttpOnlyRefreshCookieWithoutReturningRawToken() throws Exception {
        when(authService.login(eq("demo_admin"), eq("correct"), eq("demo"), any(), any()))
                .thenReturn(new AuthService.SessionResult(
                        new AuthSessionResponse("jwt", 900L, user), "refresh-raw"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Browser/1.0")
                        .content("""
                                {"username":"demo_admin","password":"correct","tenant_code":"demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("miniapi_refresh", true))
                .andExpect(cookie().path("miniapi_refresh", "/api/v1/auth"))
                .andExpect(jsonPath("$.data.access_token").value("jwt"))
                .andExpect(jsonPath("$.data.user.permissions[0]").value("tenant:routing:read"))
                .andExpect(jsonPath("$.data.refresh_token").doesNotExist());
    }

    @Test
    void refreshRotatesCookieAndReturnsAccessToken() throws Exception {
        when(authService.refresh(eq("old-refresh"), any(), any()))
                .thenReturn(new AuthService.SessionResult(
                        new AuthSessionResponse("new-jwt", 900L, user), "new-refresh"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("miniapi_refresh", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("miniapi_refresh", "new-refresh"))
                .andExpect(jsonPath("$.data.access_token").value("new-jwt"));
    }

    @Test
    void logoutIsIdempotentAndClearsCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("miniapi_refresh", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("miniapi_refresh", 0));

        verify(authService).logout("old-refresh");
    }

    @Test
    void meUsesAuthenticatedPrincipalIdentity() throws Exception {
        SaasPrincipal principal = new SaasPrincipal(
                2L, "demo_admin", 1L, "tenant_admin", Set.of("tenant:routing:read"));
        when(authService.currentUser(2L)).thenReturn(user);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        try {
            mockMvc.perform(get("/api/v1/auth/me").principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("demo_admin"))
                    .andExpect(jsonPath("$.data.permissions[0]").value("tenant:routing:read"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
