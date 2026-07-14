package com.miniapi.router.saas.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.context.TenantContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志查询服务
 * <p>
 * 提供请求日志的搜索和详情查询功能。
 * 支持按时间范围、模型、提供商、状态、关键词、追踪ID等条件进行筛选。
 * </p>
 */
@Service
public class LogQueryService {

    private final LogSearchRepository searchRepository;  // 日志搜索仓库，用于查询日志数据

    public LogQueryService(LogSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    /**
     * 搜索请求日志
     * <p>
     * 根据多种条件组合查询请求日志，支持分页。
     * </p>
     *
     * @param page      页码
     * @param pageSize  每页条数
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param model     模型名称过滤（可选）
     * @param provider  提供商过滤（可选）
     * @param status    状态过滤（可选）
     * @param keyword   关键词搜索（可选，匹配错误消息或模型名）
     * @param traceId   追踪ID过滤（可选）
     * @return 查询结果 Map，包含日志列表和分页信息
     */
    public Map<String, Object> search(int page, int pageSize, String startTime, String endTime,
                                      String model, String provider, String status, String keyword, String traceId) {
        Long tenantId = TenantContext.getTenantId();
        // 构建查询条件 Map，仅包含非空的筛选条件
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("tenantId", tenantId);
        if (startTime != null) query.put("startTime", startTime);
        if (endTime != null) query.put("endTime", endTime);
        if (model != null) query.put("model", model);
        if (provider != null) query.put("provider", provider);
        if (status != null) query.put("status", status);
        if (keyword != null) query.put("keyword", keyword);
        if (traceId != null) query.put("traceId", traceId);
        return searchRepository.search(query, page, pageSize);
    }

    /**
     * 获取日志详情
     * <p>
     * 根据日志ID查询详细信息，包括 Prompt 和 Response 内容。
     * </p>
     *
     * @param id 日志ID
     * @return 日志详情 Map
     */
    public Map<String, Object> getDetail(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return searchRepository.getDetail(id, tenantId);
    }
}
