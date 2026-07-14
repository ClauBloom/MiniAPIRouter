package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.RequestLogMeta;
import java.util.List;
import java.util.Map;

/**
 * 日志搜索仓库接口（SPI 扩展点）。
 * <p>
 * 提供日志的全文检索、详情查询、仪表盘统计和索引建库能力。
 * 典型实现基于 Elasticsearch 或类似搜索引擎，用于运营监控和问题排查。
 * </p>
 */
public interface LogSearchRepository {

    /**
     * 分页搜索日志。
     *
     * @param query    查询条件（键值对形式的过滤条件）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 搜索结果，通常包含 total、items 等字段
     */
    Map<String, Object> search(Map<String, Object> query, int page, int pageSize);

    /** 获取指定日志的详细内容（包含提示词和响应文本） */
    Map<String, Object> getDetail(Long id, Long tenantId);

    /** 获取仪表盘概览数据（用于运营监控面板的时间序列聚合统计） */
    Map<String, Object> dashboardSummary(Long tenantId, String startTime, String endTime, String interval);

    /** 将日志及其完整提示词/响应内容索引进搜索引擎 */
    void index(RequestLogMeta meta, String promptContent, String responseContent);
}
