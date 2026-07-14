package com.miniapi.router.standalone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.standalone.entity.RouteRuleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 路由规则 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供对 model_route_rule 表的基本 CRUD 操作。
 * </p>
 */
@Mapper
public interface RouteRuleMapper extends BaseMapper<RouteRuleDO> {
}
