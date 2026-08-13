package com.miniapi.router.saas.security;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.service.TenantContextService;
import com.miniapi.router.saas.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/** Applies an explicitly authorized tenant workspace override for platform admins. */
public class TenantOverrideFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Tenant-Id";
    private final TenantContextService tenantService;
    private final AuditLogService auditLogService;

    public TenantOverrideFilter(TenantContextService tenantService, AuditLogService auditLogService) {
        this.tenantService = tenantService;
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/tenant/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) { chain.doFilter(request, response); return; }
        if (!"super_admin".equals(TenantContext.getRole()))
            throw new RouterException("TENANT_OVERRIDE_FORBIDDEN", "Tenant override is forbidden", 403);
        Long tenantId = parse(value);
        tenantService.requireActiveTenant(tenantId);
        auditLogService.record("TENANT_CONTEXT_ENTER", "tenant", tenantId, tenantId,
                Map.of("request_uri", request.getRequestURI(), "request_method", request.getMethod()));
        Long original = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try { chain.doFilter(request, response); }
        finally { TenantContext.setTenantId(original); }
    }

    private Long parse(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new RouterException("INVALID_TENANT_OVERRIDE", "Invalid tenant override", 400);
        }
    }
}
