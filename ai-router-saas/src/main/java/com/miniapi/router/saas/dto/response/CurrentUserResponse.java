package com.miniapi.router.saas.dto.response;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String username,
        String nickname,
        String role,
        Long tenantId,
        String tenantName,
        Set<String> permissions) {
}
