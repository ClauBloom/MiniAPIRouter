package com.miniapi.router.saas.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import com.miniapi.router.saas.util.IsoDateTimeParser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UsageService {
    private final RequestLogMetaMapper mapper;
    private final LogSearchRepository repository;

    public UsageService(RequestLogMetaMapper mapper, LogSearchRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    public Map<String, Object> summary(String startTime, String endTime) {
        Long tenantId = TenantContext.getTenantId();
        return repository.dashboardSummary(tenantId, startTime, endTime, null);
    }

    public List<Map<String, Object>> trend(String startTime, String endTime) {
        return mapper.usageTrend(TenantContext.getTenantId(), parse(startTime), parse(endTime));
    }

    public List<Map<String, Object>> byModel(String startTime, String endTime) {
        return mapper.modelDistribution(TenantContext.getTenantId(), parse(startTime), parse(endTime));
    }

    public List<Map<String, Object>> byUser(String startTime, String endTime) {
        return mapper.usageByUser(TenantContext.getTenantId(), parse(startTime), parse(endTime));
    }

    public Map<String, Object> adminSummary(String startTime, String endTime) {
        return mapper.adminUsageSummary(parse(startTime), parse(endTime));
    }

    private LocalDateTime parse(String value) {
        return value != null && !value.isBlank() ? IsoDateTimeParser.parse(value) : null;
    }
}
