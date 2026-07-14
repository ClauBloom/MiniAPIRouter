package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.entity.RequestLogMetaDO;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import com.miniapi.router.core.spi.BlobStorage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MariaDB 日志搜索仓库
 * <p>
 * 实现 {@link LogSearchRepository} SPI 接口，基于 MariaDB/MySQL 数据库提供日志搜索和统计功能。
 * 支持多条件查询、日志详情查看（包含 Blob 存储中的 Prompt/Response 内容）以及仪表盘数据汇总。
 * </p>
 */
@Component
public class MariaDbLogSearchRepository implements LogSearchRepository {

    private final RequestLogMetaMapper mapper;  // 请求日志元数据 Mapper
    private final BlobStorage blobStorage;      // Blob 存储，用于读取 Prompt/Response 内容

    public MariaDbLogSearchRepository(RequestLogMetaMapper mapper, BlobStorage blobStorage) {
        this.mapper = mapper;
        this.blobStorage = blobStorage;
    }

    /**
     * 搜索请求日志
     * <p>
     * 根据租户ID、时间范围、模型、提供商、状态、追踪ID和关键词等条件组合查询日志，
     * 支持分页，结果按创建时间降序排列。
     * </p>
     *
     * @param query    查询条件 Map
     * @param page     页码
     * @param pageSize 每页条数
     * @return 查询结果 Map，包含日志列表和分页信息
     */
    @Override
    public Map<String, Object> search(Map<String, Object> query, int page, int pageSize) {
        Long tenantId = (Long) query.get("tenantId");
        LambdaQueryWrapper<RequestLogMetaDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestLogMetaDO::getTenantId, tenantId);

        // 依次添加各筛选条件
        if (query.get("startTime") != null) {
            wrapper.ge(RequestLogMetaDO::getCreatedAt, query.get("startTime"));
        }
        if (query.get("endTime") != null) {
            wrapper.le(RequestLogMetaDO::getCreatedAt, query.get("endTime"));
        }
        if (query.get("model") != null) {
            wrapper.eq(RequestLogMetaDO::getModel, query.get("model"));
        }
        if (query.get("provider") != null) {
            wrapper.eq(RequestLogMetaDO::getMappedProvider, query.get("provider"));
        }
        if (query.get("status") != null) {
            wrapper.eq(RequestLogMetaDO::getStatus, query.get("status"));
        }
        if (query.get("traceId") != null) {
            wrapper.eq(RequestLogMetaDO::getTraceId, query.get("traceId"));
        }
        // 关键词搜索：同时匹配错误消息和模型名
        if (query.get("keyword") != null) {
            wrapper.and(w -> w.like(RequestLogMetaDO::getErrorMessage, query.get("keyword"))
                    .or().like(RequestLogMetaDO::getModel, query.get("keyword")));
        }
        wrapper.orderByDesc(RequestLogMetaDO::getCreatedAt);

        // 执行分页查询
        Page<RequestLogMetaDO> p = new Page<>(page, pageSize);
        Page<RequestLogMetaDO> result = mapper.selectPage(p, wrapper);

        // 转换为响应格式
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("list", list);
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("page_size", pageSize);
        return response;
    }

    /**
     * 获取日志详情
     * <p>
     * 查询指定日志的元数据，并从 Blob 存储读取 Prompt 和 Response 的完整内容。
     * </p>
     *
     * @param id       日志ID
     * @param tenantId 租户ID（用于数据隔离）
     * @return 日志详情 Map，包含元数据和内容；若不存在则返回 null
     */
    @Override
    public Map<String, Object> getDetail(Long id, Long tenantId) {
        LambdaQueryWrapper<RequestLogMetaDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestLogMetaDO::getId, id).eq(RequestLogMetaDO::getTenantId, tenantId);
        RequestLogMetaDO dO = mapper.selectOne(wrapper);
        if (dO == null) return null;
        Map<String, Object> detail = toMap(dO);
        // 从 Blob 存储读取 Prompt 内容，尝试解析为 JSON
        if (dO.getPromptStorageUrl() != null) {
            String content = blobStorage.read(dO.getPromptStorageUrl());
            if (content != null) {
                try {
                    detail.put("messages", com.miniapi.router.core.util.JsonUtils.parse(content));
                } catch (Exception e) {
                    // JSON 解析失败时直接存储原始字符串
                    detail.put("messages", content);
                }
            }
        }
        // 从 Blob 存储读取 Response 内容
        if (dO.getResponseStorageUrl() != null) {
            String content = blobStorage.read(dO.getResponseStorageUrl());
            if (content != null) {
                detail.put("response_content", content);
            }
        }
        detail.put("fallback_events", List.of());
        detail.put("error", dO.getErrorMessage());
        return detail;
    }

    /**
     * 获取仪表盘汇总数据
     * <p>
     * 统计指定时间范围内的请求总数、Token 使用量、平均延迟、平均首字延迟、成功率、故障转移率，
     * 以及模型分布和提供商分布。
     * </p>
     *
     * @param tenantId 租户ID
     * @param startTime 开始时间（默认为 7 天前）
     * @param endTime   结束时间（默认为当前时间）
     * @param interval  时间间隔（保留参数，暂未使用）
     * @return 汇总数据 Map
     */
    @Override
    public Map<String, Object> dashboardSummary(Long tenantId, String startTime, String endTime, String interval) {
        // 解析时间范围，默认最近 7 天
        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime.replace("Z", "")) : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime.replace("Z", "")) : LocalDateTime.now();

        // 查询时间范围内的所有日志
        LambdaQueryWrapper<RequestLogMetaDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestLogMetaDO::getTenantId, tenantId)
                .ge(RequestLogMetaDO::getCreatedAt, start)
                .le(RequestLogMetaDO::getCreatedAt, end);
        List<RequestLogMetaDO> all = mapper.selectList(wrapper);

        // 计算各项统计指标
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_requests", all.size());
        summary.put("total_tokens", all.stream().mapToLong(l -> l.getTotalTokens() != null ? l.getTotalTokens() : 0).sum());
        summary.put("avg_latency_ms", all.isEmpty() ? 0 : (int) all.stream().mapToInt(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0).average().orElse(0));
        summary.put("avg_ttft_ms", all.isEmpty() ? 0 : (int) all.stream().filter(l -> l.getTtftMs() != null).mapToInt(RequestLogMetaDO::getTtftMs).average().orElse(0));
        long successCount = all.stream().filter(l -> "success".equals(l.getStatus())).count();
        summary.put("success_rate", all.isEmpty() ? 0 : (double) successCount / all.size());
        long fallbackCount = all.stream().filter(l -> l.getFallbackCount() != null && l.getFallbackCount() > 0).count();
        summary.put("fallback_rate", all.isEmpty() ? 0 : (double) fallbackCount / all.size());

        // 模型分布统计
        List<Map<String, Object>> modelDist = mapper.modelDistribution(tenantId,
                start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        summary.put("model_distribution", modelDist);

        // 提供商分布统计，计算各提供商占比
        List<Map<String, Object>> providerDist = mapper.providerDistribution(tenantId,
                start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        long totalProvider = providerDist.stream().mapToLong(m -> ((Number) m.get("cnt")).longValue()).sum();
        providerDist.forEach(m -> m.put("percentage", totalProvider == 0 ? 0 : ((Number) m.get("cnt")).doubleValue() / totalProvider));
        summary.put("provider_distribution", providerDist);

        // Token 趋势数据（暂未实现，返回空列表）
        summary.put("tokens_trend", List.of());
        return summary;
    }

    /**
     * 索引日志（空实现）
     * <p>
     * 该方法为 SPI 接口要求的方法，当前实现暂不需要额外的索引操作。
     * </p>
     *
     * @param meta            日志元数据
     * @param promptContent   Prompt 内容
     * @param responseContent Response 内容
     */
    @Override
    public void index(RequestLogMeta meta, String promptContent, String responseContent) {
    }

    /**
     * 将日志 DO 对象转换为响应 Map
     *
     * @param dO 日志 DO 对象
     * @return 响应 Map
     */
    private Map<String, Object> toMap(RequestLogMetaDO dO) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dO.getId());
        m.put("trace_id", dO.getTraceId());
        m.put("request_id", dO.getRequestId());
        m.put("protocol", dO.getProtocol());
        m.put("model", dO.getModel());
        m.put("mapped_provider", dO.getMappedProvider());
        m.put("api_key_id", dO.getApiKeyId());
        m.put("route_rule_id", dO.getRouteRuleId());
        m.put("intent", dO.getIntent());
        m.put("prompt_tokens", dO.getPromptTokens());
        m.put("completion_tokens", dO.getCompletionTokens());
        m.put("total_tokens", dO.getTotalTokens());
        m.put("latency_ms", dO.getLatencyMs());
        m.put("ttft_ms", dO.getTtftMs());
        m.put("status", dO.getStatus());
        m.put("fallback_count", dO.getFallbackCount());
        m.put("prompt_storage_url", dO.getPromptStorageUrl());
        m.put("created_at", dO.getCreatedAt());
        return m;
    }
}
