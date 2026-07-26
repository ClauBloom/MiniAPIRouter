package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MariaDbLogSearchRepositoryTest {

    @Test
    void dashboardSummaryUsesScalarAggregationWithoutLoadingLogRows() {
        RequestLogMetaMapper mapper = mock(RequestLogMetaMapper.class);
        MariaDbLogSearchRepository repository = new MariaDbLogSearchRepository(mapper, mock(BlobStorage.class));
        Map<String, Object> scalars = new LinkedHashMap<>();
        scalars.put("total_requests", 4L);
        scalars.put("total_tokens", new BigDecimal("120"));
        scalars.put("avg_latency_ms", new BigDecimal("12.75"));
        scalars.put("avg_ttft_ms", new BigDecimal("4.50"));
        scalars.put("successful_requests", 3L);
        scalars.put("fallback_requests", 1L);
        when(mapper.dashboardScalarSummary(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(scalars);
        when(mapper.modelDistribution(eq(9L), any(), any())).thenReturn(List.of());
        when(mapper.providerDistribution(eq(9L), any(), any())).thenReturn(List.of());

        Map<String, Object> summary = repository.dashboardSummary(
                9L, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "day");

        assertThat(summary)
                .containsEntry("total_requests", 4)
                .containsEntry("total_tokens", 120L)
                .containsEntry("avg_latency_ms", 12)
                .containsEntry("avg_ttft_ms", 4)
                .containsEntry("success_rate", 0.75d)
                .containsEntry("fallback_rate", 0.25d);
        verify(mapper, never()).selectList(any());
    }
}
