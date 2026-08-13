package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.saas.entity.IntentConfigDO;
import com.miniapi.router.saas.mapper.IntentConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MybatisIntentCatalogProvider implements IntentCatalogProvider {
    private final IntentConfigMapper mapper;

    public MybatisIntentCatalogProvider(IntentConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IntentConfig> findAll(Long tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, tenantId)
                        .eq(IntentConfigDO::getEnabled, 1)
                        .orderByAsc(IntentConfigDO::getSortOrder))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public IntentConfig findByLabel(Long tenantId, String label) {
        IntentConfigDO dO = mapper.selectOne(new LambdaQueryWrapper<IntentConfigDO>()
                .eq(IntentConfigDO::getTenantId, tenantId)
                .eq(IntentConfigDO::getLabel, label)
                .eq(IntentConfigDO::getEnabled, 1)
                .last("LIMIT 1"));
        return dO != null ? toDomain(dO) : null;
    }

    @Override
    public IntentConfig findDefault(Long tenantId) {
        IntentConfigDO dO = mapper.selectOne(new LambdaQueryWrapper<IntentConfigDO>()
                .eq(IntentConfigDO::getTenantId, tenantId)
                .eq(IntentConfigDO::getIsDefault, 1)
                .eq(IntentConfigDO::getEnabled, 1)
                .last("LIMIT 1"));
        return dO != null ? toDomain(dO) : null;
    }

    private IntentConfig toDomain(IntentConfigDO dO) {
        IntentConfig config = new IntentConfig();
        config.setId(dO.getId());
        config.setTenantId(dO.getTenantId());
        config.setLabel(dO.getLabel());
        config.setName(dO.getName());
        config.setDescription(dO.getDescription());
        config.setTargetModels(dO.getTargetModels());
        config.setModelWeights(dO.getModelWeights());
        config.setSortOrder(dO.getSortOrder());
        config.setEnabled(dO.getEnabled() != null && dO.getEnabled() == 1);
        config.setIsDefault(dO.getIsDefault() != null && dO.getIsDefault() == 1);
        config.setCustomized(dO.getCustomized() != null && dO.getCustomized() == 1);
        return config;
    }
}
