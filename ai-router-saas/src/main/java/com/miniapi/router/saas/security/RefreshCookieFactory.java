package com.miniapi.router.saas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieFactory {
    public static final String COOKIE_NAME = "miniapi_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration lifetime;

    public RefreshCookieFactory(
            @Value("${miniapi.router.refresh-cookie-secure:true}") boolean secure,
            @Value("${miniapi.router.refresh-expiration:2592000000}") long expirationMs) {
        this.secure = secure;
        this.lifetime = Duration.ofMillis(expirationMs);
    }

    public ResponseCookie issue(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path(COOKIE_PATH).maxAge(lifetime).build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path(COOKIE_PATH).maxAge(Duration.ZERO).build();
    }
}
