package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.RequestLogMetaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
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
    List<Map<String, Object>> modelDistribution(Long tenantId, String startTime, String endTime);

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
    List<Map<String, Object>> providerDistribution(Long tenantId, String startTime, String endTime);
}
