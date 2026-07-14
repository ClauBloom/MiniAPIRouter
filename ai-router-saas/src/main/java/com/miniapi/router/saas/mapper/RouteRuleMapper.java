package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.RouteRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 路由规则 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 {@link BaseMapper} 实现的路由规则数据访问层。
 * 提供对 route_rule 表的基本 CRUD 操作，包括插入、查询、更新和删除。
 * </p>
 */
@Mapper
public interface RouteRuleMapper extends BaseMapper<RouteRuleDO> {
}
