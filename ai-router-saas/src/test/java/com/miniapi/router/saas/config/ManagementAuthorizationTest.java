package com.miniapi.router.saas.config;

import com.miniapi.router.saas.controller.ApiKeyConfigController;
import com.miniapi.router.saas.controller.ProxyApiKeyController;
import com.miniapi.router.saas.controller.RouteRuleController;
import com.miniapi.router.saas.controller.TenantManageController;
import com.miniapi.router.saas.controller.MemberController;
import com.miniapi.router.saas.controller.UserManageController;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementAuthorizationTest {
    @Test void methodSecurityIsEnabled() {
        assertThat(AnnotatedElementUtils.hasAnnotation(SecurityConfig.class, EnableMethodSecurity.class)).isTrue();
    }
    @Test void controllersUseExplicitPermissions() {
        assertPermission(MemberController.class, "hasAuthority('tenant:member:manage')");
        assertMethodPermission(TenantManageController.class, "list", "hasAuthority('platform:tenant:read')");
        assertMethodPermission(TenantManageController.class, "create", "hasAuthority('platform:tenant:write')");
        assertPermission(UserManageController.class, "hasAnyAuthority('platform:user:manage', 'tenant:member:manage')");
        assertMethodPermission(ApiKeyConfigController.class, "list", "hasAuthority('tenant:upstream:read')");
        assertMethodPermission(ApiKeyConfigController.class, "create", "hasAuthority('tenant:upstream:write')");
        assertMethodPermission(RouteRuleController.class, "list", "hasAuthority('tenant:routing:read')");
        assertMethodPermission(RouteRuleController.class, "create", "hasAuthority('tenant:routing:write')");
        assertPermission(ProxyApiKeyController.class, "hasAuthority('tenant:proxy_key:manage')");
    }
    private void assertMethodPermission(Class<?> type, String methodName, String expression) {
        var method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
    private void assertPermission(Class<?> type, String expression) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
