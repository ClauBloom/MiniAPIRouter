package com.miniapi.router.saas.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Maps stable application roles to explicit management permissions. */
@Component
public class PermissionCatalog {

    private static final Set<String> TENANT_ADMIN = Set.of(
            "tenant:playground:use", "tenant:upstream:read", "tenant:upstream:write",
            "tenant:routing:read", "tenant:routing:write", "tenant:proxy_key:manage",
            "tenant:log:read", "tenant:usage:read", "tenant:member:manage");

    private static final Set<String> SUPER_ADMIN = Set.of(
            "platform:tenant:read", "platform:tenant:write", "platform:user:manage",
            "platform:system:read", "platform:system:write",
            "tenant:playground:use", "tenant:upstream:read", "tenant:upstream:write",
            "tenant:routing:read", "tenant:routing:write", "tenant:proxy_key:manage",
            "tenant:log:read", "tenant:usage:read", "tenant:member:manage");

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "super_admin", SUPER_ADMIN,
            "tenant_admin", TENANT_ADMIN,
            "user", Set.of("tenant:playground:use"));

    public Set<String> permissionsFor(String role) {
        Set<String> permissions = BY_ROLE.get(role);
        if (permissions == null) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
        return permissions;
    }
}
