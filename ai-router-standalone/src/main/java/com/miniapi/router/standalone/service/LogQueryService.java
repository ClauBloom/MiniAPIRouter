package com.miniapi.router.standalone.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 日志查询服务。
 * <p>
 * 封装请求日志的查询逻辑，为控制器层提供日志搜索、详情查询和仪表盘统计功能。
 * 所有查询自动绑定当前租户 ID（独立版固定为 1）。
 * </p>
 */
@Service
public class LogQueryService {

    private static final Long TENANT_ID = 1L; // 独立版固定租户 ID
    private final LogSearchRepository logSearchRepository; // 日志搜索仓库（SPI 实现）

    public LogQueryService(LogSearchRepository logSearchRepository) {
        this.logSearchRepository = logSearchRepository;
    }

    /**
     * 搜索请求日志。
     *
     * @param query    查询条件 Map（包含 model, provider, status, traceId, startTime, endTime 等）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 日志列表分页数据
     */
    public Map<String, Object> search(Map<String, Object> query, int page, int pageSize) {
        query.put("tenantId", TENANT_ID); // 自动注入租户 ID
        return logSearchRepository.search(query, page, pageSize);
    }

    /**
     * 查询指定日志的详情（包含 prompt 和 response 内容）。
     *
     * @param id 日志记录 ID
     * @return 日志详情
     */
    public Map<String, Object> getDetail(Long id) {
        return logSearchRepository.getDetail(id, TENANT_ID);
    }

    /**
     * 获取仪表盘统计数据。
     * 包括总请求数、Token 用量、平均延迟、成功率、模型分布等。
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param interval  统计间隔（可选）
     * @return 仪表盘统计数据
     */
    public Map<String, Object> dashboardSummary(String startTime, String endTime, String interval) {
        return logSearchRepository.dashboardSummary(TENANT_ID, startTime, endTime, interval);
    }
}
