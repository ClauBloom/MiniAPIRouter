package com.miniapi.router.saas.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

public record SaasPrincipal(Long userId, String username, Long tenantId,
                            String role, Set<String> permissions) {
    public List<SimpleGrantedAuthority> authorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
