package com.miniapi.router.saas.security;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.service.TenantContextService;
import com.miniapi.router.saas.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

class TenantOverrideFilterTest {
    private final TenantContextService service = mock(TenantContextService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final TenantOverrideFilter filter = new TenantOverrideFilter(service, audit);

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void tenantAdminCannotOverrideTenant() {
        TenantContext.setAuthenticatedTenantId(42L); TenantContext.setTenantId(42L); TenantContext.setRole("tenant_admin");
        MockHttpServletRequest request = request("99");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> {}))
                .isInstanceOfSatisfying(RouterException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(403));
    }

    @Test void superAdminCanOverrideActiveTenantAndContextIsRestored() throws Exception {
        TenantContext.setAuthenticatedTenantId(0L); TenantContext.setTenantId(0L); TenantContext.setRole("super_admin");
        TenantContext.setUserId(8L); TenantContext.setTraceId("trace-override");
        TenantDO tenant = new TenantDO(); tenant.setId(99L); tenant.setStatus(1);
        when(service.requireActiveTenant(99L)).thenReturn(tenant);
        AtomicLong inside = new AtomicLong();
        filter.doFilter(request("99"), new MockHttpServletResponse(), (a,b) -> inside.set(TenantContext.getTenantId()));
        assertThat(inside.get()).isEqualTo(99L);
        assertThat(TenantContext.getTenantId()).isEqualTo(0L);
        verify(audit).record(eq("TENANT_CONTEXT_ENTER"), eq("tenant"), eq(99L), eq(99L),
                argThat(details -> "/api/v1/tenant/logs".equals(details.get("request_uri"))));
    }

    private MockHttpServletRequest request(String id) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tenant/logs");
        request.addHeader("X-Tenant-Id", id); return request;
    }
}
