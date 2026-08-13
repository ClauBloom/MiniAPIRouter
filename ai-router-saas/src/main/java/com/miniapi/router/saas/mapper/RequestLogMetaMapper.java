package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.RequestLogMetaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 请求日志元数据 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 {@link BaseMapper} 实现的请求日志数据访问层。
 * 除了基本的 CRUD 操作外，还提供了用于仪表盘统计的自定义查询方法。
 * </p>
 */
@Mapper
public interface RequestLogMetaMapper extends BaseMapper<RequestLogMetaDO> {

    /**
     * 在数据库中聚合仪表盘标量指标，避免将时间范围内的全部日志加载到 JVM。
     */
    @Select("""
            SELECT COUNT(*) AS total_requests,
                   COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS total_tokens,
                   COALESCE(AVG(COALESCE(latency_ms, 0)), 0) AS avg_latency_ms,
                   COALESCE(AVG(ttft_ms), 0) AS avg_ttft_ms,
                   COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END), 0) AS successful_requests,
                   COALESCE(SUM(CASE WHEN fallback_count > 0 THEN 1 ELSE 0 END), 0) AS fallback_requests
            FROM request_log_meta
            WHERE tenant_id = #{tenantId}
              AND created_at >= #{startTime}
              AND created_at <= #{endTime}
            """)
    Map<String, Object> dashboardScalarSummary(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间范围内模型分布统计
     * <p>
     * 按模型分组统计请求数量和 Token 总量，按请求数降序排列，最多返回 20 条。
     * </p>
     *
     * @param tenantId  租户ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 模型分布列表，每条包含 model（模型名）、cnt（请求数）、tokens（Token总量）
     */
    @Select("SELECT model, COUNT(*) as cnt, SUM(total_tokens) as tokens FROM request_log_meta " +
            "WHERE tenant_id = #{tenantId} AND created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY model ORDER BY cnt DESC LIMIT 20")
    List<Map<String, Object>> modelDistribution(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间范围内提供商分布统计
     * <p>
     * 按映射的提供商分组统计请求数量，按请求数降序排列。
     * </p>
     *
     * @param tenantId  租户ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 提供商分布列表，每条包含 provider（提供商名）和 cnt（请求数）
     */
    @Select("SELECT mapped_provider as provider, COUNT(*) as cnt FROM request_log_meta " +
            "WHERE tenant_id = #{tenantId} AND created_at >= #{startTime} AND created_at <= #{endTime} " +
            "GROUP BY mapped_provider ORDER BY cnt DESC")
    List<Map<String, Object>> providerDistribution(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT DATE(created_at) AS day, COUNT(*) AS cnt, COALESCE(SUM(total_tokens),0) AS tokens
            FROM request_log_meta
            WHERE tenant_id = #{tenantId}
              AND (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            GROUP BY DATE(created_at) ORDER BY day ASC
            """)
    List<Map<String, Object>> usageTrend(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT user_id, COUNT(*) AS cnt, COALESCE(SUM(total_tokens),0) AS tokens
            FROM request_log_meta
            WHERE tenant_id = #{tenantId}
              AND (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            GROUP BY user_id ORDER BY cnt DESC
            """)
    List<Map<String, Object>> usageByUser(
            @Param("tenantId") Long tenantId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT COUNT(*) AS total_requests,
                   COALESCE(SUM(COALESCE(total_tokens,0)),0) AS total_tokens
            FROM request_log_meta
            WHERE (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            """)
    Map<String, Object> adminUsageSummary(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
