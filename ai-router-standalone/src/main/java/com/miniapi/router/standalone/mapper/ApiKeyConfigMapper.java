package com.miniapi.router.standalone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.standalone.entity.ApiKeyConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * API Key 配置 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供对 api_key_config 表的基本 CRUD 操作。
 * </p>
 */
@Mapper
public interface ApiKeyConfigMapper extends BaseMapper<ApiKeyConfigDO> {
}
