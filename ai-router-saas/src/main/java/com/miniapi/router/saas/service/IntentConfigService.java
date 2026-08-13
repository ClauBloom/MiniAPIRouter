package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.IntentConfigRequest;
import com.miniapi.router.saas.entity.IntentConfigDO;
import com.miniapi.router.saas.mapper.IntentConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IntentConfigService {
    private final IntentConfigMapper mapper;
    private final ModelConfigRepository modelConfigRepository;

    public IntentConfigService(IntentConfigMapper mapper, ModelConfigRepository modelConfigRepository) {
        this.mapper = mapper;
        this.modelConfigRepository = modelConfigRepository;
    }

    public List<Map<String, Object>> list() {
        Long tenantId = TenantContext.getTenantId();
        return mapper.selectList(new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, tenantId)
                        .orderByAsc(IntentConfigDO::getSortOrder))
                .stream().map(this::toResponse).toList();
    }

    public Map<String, Object> get(Long id) {
        return toResponse(requireOwned(id, TenantContext.getTenantId()));
    }

    @Transactional
    public Map<String, Object> create(IntentConfigRequest request) {
        Long tenantId = TenantContext.getTenantId();
        requireValid(request, tenantId);
        IntentConfigDO dO = new IntentConfigDO();
        apply(dO, request, tenantId);
        mapper.insert(dO);
        return toResponse(dO);
    }

    @Transactional
    public Map<String, Object> update(Long id, IntentConfigRequest request) {
        Long tenantId = TenantContext.getTenantId();
        IntentConfigDO dO = requireOwned(id, tenantId);
        requireValid(request, tenantId);
        apply(dO, request, tenantId);
        mapper.updateById(dO);
        return toResponse(dO);
    }

    public void delete(Long id) {
        requireOwned(id, TenantContext.getTenantId());
        mapper.deleteById(id);
    }

    private void apply(IntentConfigDO dO, IntentConfigRequest request, Long tenantId) {
        dO.setTenantId(tenantId);
        dO.setLabel(request.label().trim());
        dO.setName(request.name() != null && !request.name().isBlank() ? request.name().trim() : request.label().trim());
        dO.setDescription(request.description());
        dO.setTargetModels(request.targetModels());
        dO.setModelWeights(request.modelWeights());
        dO.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        dO.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        dO.setIsDefault(Boolean.TRUE.equals(request.isDefault()) ? 1 : 0);
        dO.setCustomized(Boolean.FALSE.equals(request.customized()) ? 0 : 1);
    }

    private void requireValid(IntentConfigRequest request, Long tenantId) {
        if (request == null || request.label() == null || request.label().isBlank()) {
            throw new RouterException("INVALID_INTENT", "意图标签不能为空", 400);
        }
        Map<String, Integer> weights = request.modelWeights();
        if (weights == null || weights.isEmpty()) {
            throw new RouterException("INVALID_INTENT", "模型权重不能为空", 400);
        }
        if (weights.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new RouterException("INVALID_INTENT", "模型权重不能为负数", 400);
        }
        Set<String> available = modelConfigRepository.findByTenantId(tenantId).stream()
                .map(ModelConfig::getDisplayName).collect(Collectors.toSet());
        if (!available.containsAll(weights.keySet())) {
            throw new RouterException("INVALID_INTENT_MODELS", "权重引用了不属于当前租户的模型", 400);
        }
    }

    private IntentConfigDO requireOwned(Long id, Long tenantId) {
        IntentConfigDO dO = mapper.selectById(id);
        if (dO == null || !Objects.equals(dO.getTenantId(), tenantId)) {
            throw new RouterException("RESOURCE_NOT_FOUND", "意图不存在", 404);
        }
        return dO;
    }

    private Map<String, Object> toResponse(IntentConfigDO dO) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", dO.getId());
        response.put("tenant_id", dO.getTenantId());
        response.put("label", dO.getLabel());
        response.put("name", dO.getName());
        response.put("description", dO.getDescription());
        response.put("target_models", dO.getTargetModels());
        response.put("model_weights", dO.getModelWeights());
        response.put("sort_order", dO.getSortOrder());
        response.put("enabled", dO.getEnabled() != null && dO.getEnabled() == 1);
        response.put("is_default", dO.getIsDefault() != null && dO.getIsDefault() == 1);
        response.put("customized", dO.getCustomized() != null && dO.getCustomized() == 1);
        response.put("created_at", dO.getCreatedAt());
        response.put("updated_at", dO.getUpdatedAt());
        return response;
    }
}
