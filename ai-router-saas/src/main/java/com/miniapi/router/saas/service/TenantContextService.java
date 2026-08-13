package com.miniapi.router.saas.service;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TenantContextService {
    private final TenantMapper tenants;

    public TenantContextService(TenantMapper tenants) { this.tenants = tenants; }

    public TenantDO requireActiveTenant(Long id) {
        TenantDO tenant = tenants.selectById(id);
        if (tenant == null) throw new RouterException("RESOURCE_NOT_FOUND", "Tenant not found", 404);
        if (Integer.valueOf(0).equals(tenant.getStatus()))
            throw new RouterException("TENANT_DISABLED", "Tenant is disabled", 403);
        if (tenant.getExpiresAt() != null && tenant.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RouterException("TENANT_EXPIRED", "Tenant is expired", 403);
        return tenant;
    }
}
