package com.miniapi.router.standalone.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.standalone.entity.IntentConfigDO;
import com.miniapi.router.standalone.mapper.IntentConfigMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 SQLite 的意图目录提供者实现。
 * <p>
 * 实现 IntentCatalogProvider SPI 接口，从 intent_config 表读取意图配置。
 * 使用 @Primary 注解确保在有多个实现时优先使用此实现。
 * 查询时排除默认意图模板，只返回实际可用的意图配置。
 * </p>
 */
@Component
@Primary
public class SqliteIntentCatalogProvider implements IntentCatalogProvider {

    private final IntentConfigMapper mapper; // 意图配置 Mapper

    public SqliteIntentCatalogProvider(IntentConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查找指定租户的所有意图配置（排除默认意图模板，按排序顺序排列）。
     *
     * @param tenantId 租户 ID
     * @return 意图配置列表
     */
    @Override
    public List<IntentConfig> findAll(Long tenantId) {
        List<IntentConfigDO> list = mapper.selectList(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, tenantId)
                        .ne(IntentConfigDO::getIsDefault, 1) // 排除默认意图模板
                        .orderByAsc(IntentConfigDO::getSortOrder)); // 按排序顺序排列
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * 根据标签查找意图配置。
     *
     * @param tenantId 租户 ID
     * @param label    意图标签
     * @return 意图配置，不存在返回 null
     */
    @Override
    public IntentConfig findByLabel(Long tenantId, String label) {
        if (label == null) return null;
        IntentConfigDO dO = mapper.selectOne(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, tenantId)
                        .eq(IntentConfigDO::getLabel, label));
        return dO != null ? toDomain(dO) : null;
    }

    /**
     * 将 DO 转换为域对象（Integer 转 Boolean）。
     *
     * @param dO 数据对象
     * @return 域对象
     */
    private IntentConfig toDomain(IntentConfigDO dO) {
        IntentConfig c = new IntentConfig();
        c.setId(dO.getId());
        c.setTenantId(dO.getTenantId());
        c.setLabel(dO.getLabel());
        c.setName(dO.getName());
        c.setDescription(dO.getDescription());
        c.setTargetKeyIds(dO.getTargetKeyIds());
        c.setKeyWeights(dO.getKeyWeights());
        c.setSortOrder(dO.getSortOrder());
        // Integer 转 Boolean
        c.setEnabled(dO.getEnabled() != null && dO.getEnabled() == 1);
        c.setIsDefault(dO.getIsDefault() != null && dO.getIsDefault() == 1);
        c.setCustomized(dO.getCustomized() != null && dO.getCustomized() == 1);
        return c;
    }
}
