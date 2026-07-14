package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.TenantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 租户 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 {@link BaseMapper} 实现的租户数据访问层。
 * 提供对 tenant 表的基本 CRUD 操作以及配额扣减等自定义操作。
 * </p>
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {

    /**
     * 增加租户已使用配额
     * <p>
     * 原子性地增加租户的已使用 Token 配额，同时更新记录的更新时间。
     * 仅对未删除（deleted=0）且启用（status=1）的租户生效。
     * </p>
     *
     * @param tenantId 租户ID
     * @param tokens   要增加的 Token 数量
     * @return 受影响的行数（1 表示成功，0 表示租户不存在或未启用）
     */
    @Update("UPDATE tenant SET quota_used = quota_used + #{tokens}, updated_at = NOW() WHERE id = #{tenantId} AND deleted = 0 AND status = 1")
    int addQuotaUsed(@Param("tenantId") Long tenantId, @Param("tokens") long tokens);
}
