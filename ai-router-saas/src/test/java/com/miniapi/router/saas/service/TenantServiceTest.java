package com.miniapi.router.saas.service;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.QuotaAdjustmentRequest;
import com.miniapi.router.saas.dto.request.TenantCreateRequest;
import com.miniapi.router.saas.entity.QuotaAdjustmentDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.QuotaAdjustmentMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantServiceTest {
    private TenantMapper tenantMapper;
    private QuotaAdjustmentMapper adjustmentMapper;
    private AuditLogService audit;
    private TenantService service;

    @BeforeEach void setUp() {
        tenantMapper = mock(TenantMapper.class);
        adjustmentMapper = mock(QuotaAdjustmentMapper.class);
        audit = mock(AuditLogService.class);
        service = new TenantService(tenantMapper, adjustmentMapper, audit);
        TenantContext.setUserId(42L);
        TenantContext.setAuthenticatedTenantId(0L);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void genericUpdateDoesNotChangeQuotaLimit() {
        TenantDO tenant = tenant(7L, 1000L);
        when(tenantMapper.selectById(7L)).thenReturn(tenant);
        TenantCreateRequest request = new TenantCreateRequest();
        request.setTenantName("Renamed");
        request.setQuotaLimit(999999L);

        service.update(7L, request);

        assertThat(tenant.getQuotaLimit()).isEqualTo(1000L);
    }

    @Test void adjustsQuotaWithReasonAndBeforeAfterAudit() {
        TenantDO tenant = tenant(7L, 1000L);
        when(tenantMapper.selectById(7L)).thenReturn(tenant);

        Map<String,Object> response = service.adjustQuota(7L, new QuotaAdjustmentRequest(2500L, "annual renewal"));

        assertThat(tenant.getQuotaLimit()).isEqualTo(2500L);
        var captor = org.mockito.ArgumentCaptor.forClass(QuotaAdjustmentDO.class);
        verify(adjustmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getBeforeLimit()).isEqualTo(1000L);
        assertThat(captor.getValue().getAfterLimit()).isEqualTo(2500L);
        assertThat(captor.getValue().getReason()).isEqualTo("annual renewal");
        verify(audit).record(eq("TENANT_QUOTA_ADJUST"), eq("tenant"), eq(7L), eq(7L), any());
        assertThat(response).containsEntry("quota_limit", 2500L);
    }

    @Test void rejectsInvalidQuotaAdjustment() {
        when(tenantMapper.selectById(7L)).thenReturn(tenant(7L, 1000L));
        assertThatThrownBy(() -> service.adjustQuota(7L, new QuotaAdjustmentRequest(0L, " ")))
                .isInstanceOfSatisfying(RouterException.class, e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_QUOTA_ADJUSTMENT"));
        verify(adjustmentMapper, never()).insert(any(QuotaAdjustmentDO.class));
    }

    @Test void statusOnlyAcceptsEnabledOrDisabled() {
        when(tenantMapper.selectById(7L)).thenReturn(tenant(7L, 1000L));
        assertThatThrownBy(() -> service.changeStatus(7L, 2))
                .isInstanceOfSatisfying(RouterException.class, e -> assertThat(e.getErrorCode()).isEqualTo("INVALID_TENANT_STATUS"));
        verify(tenantMapper, never()).updateById(any(TenantDO.class));
    }

    private TenantDO tenant(Long id, Long limit) { TenantDO t = new TenantDO(); t.setId(id); t.setTenantCode("demo"); t.setTenantName("Demo"); t.setQuotaLimit(limit); t.setQuotaUsed(100L); t.setStatus(1); return t; }
}
