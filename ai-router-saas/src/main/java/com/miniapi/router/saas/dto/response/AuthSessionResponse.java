package com.miniapi.router.saas.dto.response;

public record AuthSessionResponse(
        String accessToken,
        long expiresIn,
        CurrentUserResponse user) {
}
