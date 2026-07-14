package com.miniapi.router.standalone.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.core.spi.BlobStorage;
import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.standalone.entity.RequestLogMetaDO;
import com.miniapi.router.standalone.mapper.RequestLogMetaMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 SQLite 的日志搜索仓储实现。
 * <p>
 * 实现 LogSearchRepository SPI 接口，提供请求日志的搜索、详情查询和仪表盘统计功能。
 * 支持按模型、供应商、状态、追踪 ID、时间范围等条件进行过滤查询。
 * 日志详情查询会从 BlobStorage 读取完整的 Prompt 和 Response 内容。
 * </p>
 */
@Component
public class SqliteLogSearchRepository implements LogSearchRepository {

    private final RequestLogMetaMapper mapper; // 日志元数据 Mapper
    private final BlobStorage blobStorage;     // Blob 存储（读取 Prompt/Response 内容）

    public SqliteLogSearchRepository(RequestLogMetaMapper mapper, BlobStorage blobStorage) {
        this.mapper = mapper;
        this.blobStorage = blobStorage;
    }

    /**
     * 搜索请求日志。
     * 支持按时间范围、模型、供应商、状态、追踪 ID 等条件过滤，按创建时间倒序排列。
     *
     * @param query    查询条件 Map
     * @param page     页码
     * @param pageSize 每页条数
     * @return 包含列表和分页信息的 Map
     */
    @Override
    public Map<String, Object> search(Map<String, Object> query, int page, int pageSize) {
        Long tenantId = (Long) query.get("tenantId");
        LambdaQueryWrapper<RequestLogMetaDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestLogMetaDO::getTenantId, tenantId);

        // 按条件构建查询
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
        wrapper.orderByDesc(RequestLogMetaDO::getCreatedAt); // 按创建时间倒序

        // 分页查询
        Page<RequestLogMetaDO> p = new Page<>(page, pageSize);
        Page<RequestLogMetaDO> result = mapper.selectPage(p, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream()
                .map(this::toListMap)
                .collect(Collectors.toList());

        // 构建分页响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("list", list);
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("page_size", pageSize);
        return response;
    }

    /**
     * 查询日志详情（包含 Prompt 和 Response 完整内容）。
     * 从 BlobStorage 读取存储的 Prompt 和 Response 内容。
     *
     * @param id       日志 ID
     * @param tenantId 租户 ID（用于权限校验）
     * @return 日志详情 Map，不存在返回 null
     */
    @Override
    public Map<String, Object> getDetail(Long id, Long tenantId) {
        RequestLogMetaDO dO = mapper.selectById(id);
        if (dO == null || !dO.getTenantId().equals(tenantId)) return null;

        Map<String, Object> m = toListMap(dO);
        // 从 BlobStorage 读取 Prompt 内容
        if (dO.getPromptStorageUrl() != null) {
            m.put("messages", blobStorage.read(dO.getPromptStorageUrl()));
        }
        // 从 BlobStorage 读取 Response 内容
        if (dO.getResponseStorageUrl() != null) {
            m.put("response_content", blobStorage.read(dO.getResponseStorageUrl()));
        }
        return m;
    }

    /**
     * 获取仪表盘统计数据。
     * 统计总请求数、Token 用量、平均延迟、成功率、降级率、模型分布和供应商分布等。
     *
     * @param tenantId  租户 ID
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param interval  统计间隔（可选，当前未使用）
     * @return 仪表盘统计数据 Map
     */
    @Override
    public Map<String, Object> dashboardSummary(Long tenantId, String startTime, String endTime, String interval) {
        LambdaQueryWrapper<RequestLogMetaDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequestLogMetaDO::getTenantId, tenantId);
        if (startTime != null) wrapper.ge(RequestLogMetaDO::getCreatedAt, startTime);
        if (endTime != null) wrapper.le(RequestLogMetaDO::getCreatedAt, endTime);

        List<RequestLogMetaDO> all = mapper.selectList(wrapper);

        // 计算各项统计指标
        long totalRequests = all.size();
        long totalTokens = all.stream().mapToLong(d -> d.getTotalTokens() != null ? d.getTotalTokens() : 0).sum();
        double avgLatency = all.stream().mapToInt(d -> d.getLatencyMs() != null ? d.getLatencyMs() : 0)
                .average().orElse(0);
        long successCount = all.stream().filter(d -> "success".equals(d.getStatus())).count();
        double successRate = totalRequests > 0 ? (double) successCount / totalRequests : 0;
        long fallbackCount = all.stream().filter(d -> d.getFallbackCount() != null && d.getFallbackCount() > 0).count();
        double fallbackRate = totalRequests > 0 ? (double) fallbackCount / totalRequests : 0;

        // 模型分布统计
        List<Map<String, Object>> modelDist = all.stream()
                .collect(Collectors.groupingBy(d -> d.getModel(), Collectors.counting()))
                .entrySet().stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("model", e.getKey());
                    m.put("cnt", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        // 供应商分布统计（含百分比）
        List<Map<String, Object>> providerDist = all.stream()
                .collect(Collectors.groupingBy(d -> d.getMappedProvider(), Collectors.counting()))
                .entrySet().stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("provider", e.getKey());
                    m.put("cnt", e.getValue());
                    m.put("percentage", totalRequests > 0 ? (double) e.getValue() / totalRequests : 0);
                    return m;
                }).collect(Collectors.toList());

        // 构建统计响应
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_requests", totalRequests);
        summary.put("total_tokens", totalTokens);
        summary.put("avg_latency_ms", (int) avgLatency);
        summary.put("avg_ttft_ms", 0);
        summary.put("success_rate", successRate);
        summary.put("fallback_rate", fallbackRate);
        summary.put("model_distribution", modelDist);
        summary.put("provider_distribution", providerDist);
        summary.put("tokens_trend", List.of());
        return summary;
    }

    /**
     * 索引日志内容（独立版不实现，空方法）。
     *
     * @param meta            日志元数据
     * @param promptContent   Prompt 内容
     * @param responseContent Response 内容
     */
    @Override
    public void index(RequestLogMeta meta, String promptContent, String responseContent) {
    }

    /**
     * 将 DO 转换为列表展示用的 Map。
     *
     * @param dO 数据对象
     * @return 列表展示 Map
     */
    private Map<String, Object> toListMap(RequestLogMetaDO dO) {
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
