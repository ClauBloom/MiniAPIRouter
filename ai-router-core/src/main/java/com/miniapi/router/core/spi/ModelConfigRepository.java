package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.ModelConfig;
import java.util.List;

/**
 * 模型配置仓储 SPI。
 * <p>
 * 每个宿主模块提供自己的实现（SQLite / MyBatis）。
 * </p>
 */
public interface ModelConfigRepository {

    /** 查找租户下所有模型 */
    List<ModelConfig> findByTenantId(Long tenantId);

    /** 按对外模型名查找（租户内唯一） */
    ModelConfig findByDisplayName(Long tenantId, String displayName);

    /** 查找指定 Key 下的所有模型 */
    List<ModelConfig> findByApiKeyId(Long apiKeyId);

    /** 保存模型（新增或更新） */
    void save(ModelConfig model);

    /** 批量保存模型 */
    void saveAll(List<ModelConfig> models);

    /** 删除指定 Key 下的所有模型 */
    void deleteByApiKeyId(Long apiKeyId);

    /** 删除单个模型 */
    void deleteById(Long id);
}
