package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.saas.entity.ModelConfigDO;
import com.miniapi.router.saas.mapper.ModelConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MybatisModelConfigRepository implements ModelConfigRepository {

    private final ModelConfigMapper mapper;

    public MybatisModelConfigRepository(ModelConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ModelConfig> findByTenantId(Long tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<ModelConfigDO>()
                        .eq(ModelConfigDO::getTenantId, tenantId))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public ModelConfig findByDisplayName(Long tenantId, String displayName) {
        if (displayName == null) return null;
        ModelConfigDO dO = mapper.selectOne(new LambdaQueryWrapper<ModelConfigDO>()
                .eq(ModelConfigDO::getTenantId, tenantId)
                .eq(ModelConfigDO::getDisplayName, displayName));
        return dO != null ? toDomain(dO) : null;
    }

    @Override
    public List<ModelConfig> findByApiKeyId(Long apiKeyId) {
        return mapper.selectList(new LambdaQueryWrapper<ModelConfigDO>()
                        .eq(ModelConfigDO::getApiKeyId, apiKeyId))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void save(ModelConfig model) {
        ModelConfigDO dO = toDO(model);
        if (model.getId() != null) {
            mapper.updateById(dO);
        } else {
            mapper.insert(dO);
            model.setId(dO.getId());
        }
    }

    @Override
    public void saveAll(List<ModelConfig> models) {
        if (models == null) return;
        for (ModelConfig m : models) save(m);
    }

    @Override
    public void deleteByApiKeyId(Long apiKeyId) {
        mapper.delete(new LambdaQueryWrapper<ModelConfigDO>()
                .eq(ModelConfigDO::getApiKeyId, apiKeyId));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private ModelConfig toDomain(ModelConfigDO dO) {
        ModelConfig c = new ModelConfig();
        c.setId(dO.getId());
        c.setTenantId(dO.getTenantId());
        c.setDisplayName(dO.getDisplayName());
        c.setRealName(dO.getRealName());
        c.setApiKeyId(dO.getApiKeyId());
        c.setCreatedAt(dO.getCreatedAt());
        c.setUpdatedAt(dO.getUpdatedAt());
        return c;
    }

    private ModelConfigDO toDO(ModelConfig c) {
        ModelConfigDO dO = new ModelConfigDO();
        dO.setId(c.getId());
        dO.setTenantId(c.getTenantId());
        dO.setDisplayName(c.getDisplayName());
        dO.setRealName(c.getRealName());
        dO.setApiKeyId(c.getApiKeyId());
        return dO;
    }
}
