package com.miniapi.router.standalone.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.standalone.entity.RequestLogMetaDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请求日志元数据 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供对 request_log_meta 表的基本 CRUD 操作。
 * </p>
 */
@Mapper
public interface RequestLogMetaMapper extends BaseMapper<RequestLogMetaDO> {
}
