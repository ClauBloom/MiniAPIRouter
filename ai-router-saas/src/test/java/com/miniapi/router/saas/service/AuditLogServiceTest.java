package com.miniapi.router.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.AuditLogDO;
import com.miniapi.router.saas.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AuditLogServiceTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void returnsNewestAuditEventsAsPublicPagination() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLogService service = new AuditLogService(mapper, new ObjectMapper());
        AuditLogDO row = new AuditLogDO();
        row.setId(5L);
        row.setActorUserId(42L);
        row.setTargetTenantId(7L);
        row.setAction("TENANT_STATUS_CHANGE");
        row.setResourceType("tenant");
        row.setResourceId(7L);
        row.setTraceId("trace-5");
        row.setDetailsJson("{\"status\":0}");
        Page<AuditLogDO> result = new Page<>(1, 20);
        result.setRecords(java.util.List.of(row));
        result.setTotal(1);
        when(mapper.selectPage(any(Page.class), any())).thenReturn(result);

        var page = service.list(1, 20, 7L, "TENANT_STATUS_CHANGE");

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList()).singleElement().satisfies(event -> {
            assertThat(event.get("action")).isEqualTo("TENANT_STATUS_CHANGE");
            assertThat(event.get("details")).isEqualTo(Map.of("status", 0));
        });
    }

    @Test
    void recordsTrustedActorTraceAndTargetWhileRedactingSensitiveDetails() throws Exception {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AuditLogService service = new AuditLogService(mapper, objectMapper);
        TenantContext.setUserId(42L);
        TenantContext.setAuthenticatedTenantId(0L);
        TenantContext.setTraceId("trace-platform-123");

        service.record("QUOTA_ADJUST", "tenant", 7L, 7L, Map.of(
                "reason", "annual renewal",
                "password", "must-not-persist",
                "api_key", "also-secret"));

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLogDO.class);
        verify(mapper).insert(captor.capture());
        AuditLogDO row = captor.getValue();
        assertThat(row.getActorUserId()).isEqualTo(42L);
        assertThat(row.getActorTenantId()).isZero();
        assertThat(row.getTargetTenantId()).isEqualTo(7L);
        assertThat(row.getTraceId()).isEqualTo("trace-platform-123");
        assertThat(row.getAction()).isEqualTo("QUOTA_ADJUST");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = objectMapper.readValue(row.getDetailsJson(), Map.class);
        assertThat(details).containsEntry("reason", "annual renewal");
        assertThat(details).doesNotContainKeys("password", "api_key");
    }
}
