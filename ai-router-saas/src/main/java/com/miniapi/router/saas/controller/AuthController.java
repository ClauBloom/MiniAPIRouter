package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.request.LoginRequest;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.AuthSessionResponse;
import com.miniapi.router.saas.dto.response.CurrentUserResponse;
import com.miniapi.router.saas.security.RefreshCookieFactory;
import com.miniapi.router.saas.security.SaasPrincipal;
import com.miniapi.router.saas.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookies;

    public AuthController(AuthService authService, RefreshCookieFactory cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(
            @Valid @RequestBody LoginRequest requestBody, HttpServletRequest request) {
        AuthService.SessionResult result = authService.login(
                requestBody.getUsername(), requestBody.getPassword(), requestBody.getTenantCode(),
                request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
        return withCookie(ApiResponse.success(result.response()),
                cookies.issue(result.rawRefreshToken()).toString());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME) String rawToken,
            HttpServletRequest request) {
        AuthService.SessionResult result = authService.refresh(
                rawToken, request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
        return withCookie(ApiResponse.success(result.response()),
                cookies.issue(result.rawRefreshToken()).toString());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String rawToken) {
        authService.logout(rawToken);
        return withCookie(ApiResponse.success(), cookies.clear().toString());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal SaasPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal.userId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> withCookie(ApiResponse<T> body, String cookie) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie).body(body);
    }
}
