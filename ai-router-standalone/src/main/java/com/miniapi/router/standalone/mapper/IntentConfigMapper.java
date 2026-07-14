package com.miniapi.router.standalone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.standalone.entity.IntentConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意图配置 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供对 intent_config 表的基本 CRUD 操作。
 * </p>
 */
@Mapper
public interface IntentConfigMapper extends BaseMapper<IntentConfigDO> {
}
