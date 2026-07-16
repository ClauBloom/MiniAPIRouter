package com.miniapi.router.standalone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.standalone.entity.ModelConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型配置 Mapper。
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigDO> {
}
