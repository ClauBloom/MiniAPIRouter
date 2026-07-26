package com.miniapi.router.saas.config;

import com.miniapi.router.saas.controller.ApiKeyConfigController;
import com.miniapi.router.saas.controller.ProxyApiKeyController;
import com.miniapi.router.saas.controller.RouteRuleController;
import com.miniapi.router.saas.controller.TenantManageController;
import com.miniapi.router.saas.controller.UserManageController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementAuthorizationTest {

    @Test
    void methodSecurityIsEnabled() {
        assertThat(AnnotatedElementUtils.hasAnnotation(SecurityConfig.class, EnableMethodSecurity.class)).isTrue();
    }

    @Test
    void tenantManagementRequiresSuperAdmin() {
        PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                TenantManageController.class, PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }

    @Test
    void userManagementRequiresAnAdministrator() {
        PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(
                UserManageController.class, PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')");
    }

    @Test
    void tenantConfigurationManagementRequiresTenantAdmin() {
        assertTenantAdminOnly(ApiKeyConfigController.class);
        assertTenantAdminOnly(RouteRuleController.class);
        assertTenantAdminOnly(ProxyApiKeyController.class);
    }

    private void assertTenantAdminOnly(Class<?> controller) {
        PreAuthorize authorization = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('TENANT_ADMIN')");
    }
}
