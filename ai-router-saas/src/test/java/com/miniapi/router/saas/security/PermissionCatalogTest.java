package com.miniapi.router.saas.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionCatalogTest {
    private final PermissionCatalog catalog = new PermissionCatalog();

    @Test
    void tenantAdminReceivesTenantManagementPermissions() {
        assertThat(catalog.permissionsFor("tenant_admin")).containsExactlyInAnyOrder(
                "tenant:playground:use", "tenant:upstream:read", "tenant:upstream:write",
                "tenant:routing:read", "tenant:routing:write", "tenant:proxy_key:manage",
                "tenant:log:read", "tenant:usage:read", "tenant:member:manage");
    }

    @Test
    void userReceivesOnlyPlaygroundPermission() {
        assertThat(catalog.permissionsFor("user"))
                .containsExactly("tenant:playground:use");
    }

    @Test
    void superAdminReceivesPlatformAndTenantWorkspacePermissions() {
        assertThat(catalog.permissionsFor("super_admin"))
                .contains("platform:tenant:read", "platform:tenant:write", "platform:user:manage",
                        "platform:system:read", "platform:system:write",
                        "tenant:upstream:read", "tenant:routing:read", "tenant:log:read");
    }

    @Test
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> catalog.permissionsFor("owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
