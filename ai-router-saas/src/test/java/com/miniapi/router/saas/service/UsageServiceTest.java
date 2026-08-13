package com.miniapi.router.saas.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UsageServiceTest {
    private RequestLogMetaMapper mapper;
    private LogSearchRepository repository;
    private UsageService service;

    @BeforeEach void setUp() {
        mapper=mock(RequestLogMetaMapper.class); repository=mock(LogSearchRepository.class);
        service=new UsageService(mapper, repository);
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void trendReturnsDailyBucketsForTenant() {
        when(mapper.usageTrend(10L, null, null)).thenReturn(List.of(
                Map.of("day","2026-08-12","cnt",5L,"tokens",120L)));

        List<Map<String,Object>> trend=service.trend(null, null);

        assertThat(trend).singleElement().satisfies(bucket -> assertThat(bucket).containsEntry("day","2026-08-12"));
    }

    @Test void byUserAggregatesPerUserWithinTenant() {
        when(mapper.usageByUser(10L, null, null)).thenReturn(List.of(
                Map.of("user_id",2L,"cnt",9L,"tokens",300L)));

        List<Map<String,Object>> byUser=service.byUser(null, null);

        assertThat(byUser).singleElement().satisfies(bucket -> assertThat(bucket).containsEntry("user_id",2L));
    }

    @Test void adminSummaryAggregatesAcrossTenantsWithoutTenantFilter() {
        when(mapper.adminUsageSummary(null, null)).thenReturn(Map.of("total_requests",42L));

        Map<String,Object> summary=service.adminSummary(null, null);

        assertThat(summary).containsEntry("total_requests",42L);
        verify(mapper).adminUsageSummary(null, null);
        verify(mapper, never()).usageTrend(anyLong(), any(), any());
    }
}
