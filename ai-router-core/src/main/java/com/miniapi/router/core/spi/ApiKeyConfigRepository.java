package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.ApiKeyConfig;
import java.util.List;

/**
 * API Key 配置存储仓库接口（SPI 扩展点）。
 * <p>
 * 提供 API Key 配置的持久化访问能力，支持按 ID、租户、API Key 等多种维度查询，
 * 以及增删改和状态更新操作。
 * </p>
 */
public interface ApiKeyConfigRepository {

    /** 根据主键 ID 查询单条 API Key 配置 */
    ApiKeyConfig findById(Long id);

    /** 根据 API Key 字符串查询对应的配置（通常用于请求认证时查找） */
    ApiKeyConfig findByApiKey(String apiKey);

    /** 查询指定租户下所有的 API Key 配置列表 */
    List<ApiKeyConfig> findByTenantId(Long tenantId);

    /** 根据 ID 列表批量查询 API Key 配置 */
    List<ApiKeyConfig> findByIds(List<Long> ids);

    /** 新增或保存一条 API Key 配置，返回持久化后的对象 */
    ApiKeyConfig save(ApiKeyConfig config);

    /** 更新已有 API Key 配置 */
    void update(ApiKeyConfig config);

    /** 删除指定租户下指定 ID 的 API Key 配置 */
    void delete(Long id, Long tenantId);

    /** 更新 API Key 的状态（如启用/禁用） */
    void updateStatus(Long id, Long tenantId, int status);

    /** 更新 API Key 的健康检查状态 */
    void updateHealthStatus(Long id, String healthStatus);
}
