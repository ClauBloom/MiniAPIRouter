package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.RequestLogMeta;

/**
 * 请求日志存储仓库接口（SPI 扩展点）。
 * <p>
 * 提供请求日志元数据的持久化能力，记录每次路由请求的元信息
 * （如请求时间、延迟、Token 消耗、状态码等）。
 * </p>
 */
public interface LogRepository {

    /** 保存一条请求日志元数据 */
    void save(RequestLogMeta meta);

    /** 根据主键 ID 查询请求日志元数据 */
    RequestLogMeta findById(Long id);
}
