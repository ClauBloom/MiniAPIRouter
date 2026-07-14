package com.miniapi.router.saas.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 仪表盘服务
 * <p>
 * 提供仪表盘概览数据，包括请求统计、Token 使用量、延迟指标、成功率等汇总信息，
 * 以及租户配额使用情况。
 * </p>
 */
@Service
public class DashboardService {

    private final LogSearchRepository searchRepository;  // 日志搜索仓库，用于查询统计数据
    private final TenantMapper tenantMapper;              // 租户 Mapper，用于查询配额信息

    public DashboardService(LogSearchRepository searchRepository, TenantMapper tenantMapper) {
        this.searchRepository = searchRepository;
        this.tenantMapper = tenantMapper;
    }

    /**
     * 获取仪表盘汇总数据
     * <p>
     * 从日志搜索仓库获取指定时间范围内的请求统计数据，
     * 并附加当前租户的配额使用情况。
     * </p>
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param interval  时间间隔（用于趋势数据分组）
     * @return 汇总数据 Map，包含请求统计、Token 使用量、延迟指标、配额信息等
     */
    public Map<String, Object> summary(String startTime, String endTime, String interval) {
        Long tenantId = TenantContext.getTenantId();
        // 从日志搜索仓库获取统计数据
        Map<String, Object> summary = searchRepository.dashboardSummary(tenantId, startTime, endTime, interval);
        // 附加租户配额信息
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant != null) {
            summary.put("quota_used", tenant.getQuotaUsed());
            summary.put("quota_limit", tenant.getQuotaLimit());
        }
        return summary;
    }
}
