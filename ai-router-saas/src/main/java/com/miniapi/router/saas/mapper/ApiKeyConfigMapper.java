package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Key 配置 Mapper 接口
 * <p>
 * 基于 MyBatis-Plus 的 {@link BaseMapper} 实现的 API Key 配置数据访问层。
 * 提供对 api_key_config 表的基本 CRUD 操作，包括插入、查询、更新和删除。
 * </p>
 */
@Mapper
public interface ApiKeyConfigMapper extends BaseMapper<ApiKeyConfigDO> {
}
