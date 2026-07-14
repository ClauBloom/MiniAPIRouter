package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 {@link BaseMapper} 实现的系统用户数据访问层。
 * 提供对 sys_user 表的基本 CRUD 操作，包括插入、查询、更新和删除。
 * </p>
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {
}
