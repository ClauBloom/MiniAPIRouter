package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.TraceUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.ApiKeyConfigRequest;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API Key 配置服务
 * <p>
 * 提供上游 AI 提供商的 API Key 配置管理功能，包括创建、查询、更新、删除和健康检查。
 * 每个配置代表一个上游提供商的 API Key，包含提供商信息、协议类型、优先级、并发限制等参数。
 * </p>
 * <p>
 * 所有操作都基于当前租户上下文，确保数据隔离。
 * </p>
 */
@Service
public class ApiKeyConfigService {

    private final ApiKeyConfigRepository keyRepository;  // API Key 配置仓库（SPI 层），支持缓存
    private final ApiKeyConfigMapper mapper;              // MyBatis-Plus Mapper，用于分页查询
    private final CryptoUtils cryptoUtils;                // 加密工具类，用于脱敏显示
    private final ModelConfigRepository modelConfigRepository;

    public ApiKeyConfigService(ApiKeyConfigRepository keyRepository, ApiKeyConfigMapper mapper, CryptoUtils cryptoUtils,
                               ModelConfigRepository modelConfigRepository) {
        this.keyRepository = keyRepository;
        this.mapper = mapper;
        this.cryptoUtils = cryptoUtils;
        this.modelConfigRepository = modelConfigRepository;
    }

    /**
     * 创建 API Key 配置
     *
     * @param req API Key 配置请求对象
     * @return 创建后的配置信息（脱敏后的 Map）
     */
    public Map<String, Object> create(ApiKeyConfigRequest req) {
        Long tenantId = TenantContext.getTenantId();
        ApiKeyConfig config = new ApiKeyConfig();
        config.setTenantId(tenantId);
        config.setName(req.getName());
        config.setProvider(req.getProvider());
        // 若未指定协议，则根据提供商自动推断
        config.setProtocol(req.getProtocol() != null ? req.getProtocol() : inferProtocol(req.getProvider()));
        config.setApiKey(req.getApiKey());
        config.setBaseUrl(req.getBaseUrl());
        config.setModelMapping(req.getModelMapping());
        config.setPriority(req.getPriority());
        config.setMaxConcurrent(req.getMaxConcurrent());
        config.setQpsLimit(req.getQpsLimit());
        config.setTimeoutMs(req.getTimeoutMs());
        config.setRetryCount(req.getRetryCount());
        config.setStatus(1);
        config.setHealthStatus("unknown");
        keyRepository.save(config);
        syncModelConfigs(config.getId(), tenantId, config.getModelMapping());
        return toResponse(config);
    }

    /**
     * 分页查询 API Key 配置列表
     *
     * @param page         页码
     * @param pageSize     每页条数
     * @param provider     提供商过滤条件（可选）
     * @param status       状态过滤条件（可选）
     * @param healthStatus 健康状态过滤条件（可选）
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize, String provider, Integer status, String healthStatus) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<ApiKeyConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyConfigDO::getTenantId, tenantId);
        if (provider != null) wrapper.eq(ApiKeyConfigDO::getProvider, provider);
        if (status != null) wrapper.eq(ApiKeyConfigDO::getStatus, status);
        if (healthStatus != null) wrapper.eq(ApiKeyConfigDO::getHealthStatus, healthStatus);
        wrapper.orderByDesc(ApiKeyConfigDO::getCreatedAt);

        Page<ApiKeyConfigDO> p = new Page<>(page, pageSize);
        Page<ApiKeyConfigDO> result = mapper.selectPage(p, wrapper);
        // 通过仓库层逐条查询完整配置（包含解密后的 API Key），若查询失败则从 DO 直接转换
        List<Map<String, Object>> list = result.getRecords().stream().map(dO -> {
            ApiKeyConfig c = keyRepository.findById(dO.getId());
            return c != null ? toResponse(c) : toResponseFromDO(dO);
        }).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    /**
     * 更新 API Key 配置
     * <p>
     * 仅更新请求中非空的字段，支持部分更新。
     * </p>
     *
     * @param id  配置ID
     * @param req 更新请求对象
     * @return 更新后的配置信息（脱敏后的 Map）
     * @throws RouterException 当配置不存在或不属于当前租户时抛出 404
     */
    public Map<String, Object> update(Long id, ApiKeyConfigRequest req) {
        Long tenantId = TenantContext.getTenantId();
        ApiKeyConfig config = keyRepository.findById(id);
        // 校验配置存在性及租户归属
        if (config == null || !config.getTenantId().equals(tenantId)) {
            throw new RouterException("RESOURCE_NOT_FOUND", "API Key 配置不存在", 404);
        }
        // 逐字段条件更新，仅更新非空字段
        if (req.getName() != null) config.setName(req.getName());
        if (req.getProvider() != null) config.setProvider(req.getProvider());
        if (req.getProtocol() != null) config.setProtocol(req.getProtocol());
        if (req.getApiKey() != null) config.setApiKey(req.getApiKey());
        if (req.getBaseUrl() != null) config.setBaseUrl(req.getBaseUrl());
        if (req.getModelMapping() != null) config.setModelMapping(req.getModelMapping());
        if (req.getPriority() != null) config.setPriority(req.getPriority());
        if (req.getMaxConcurrent() != null) config.setMaxConcurrent(req.getMaxConcurrent());
        if (req.getQpsLimit() != null) config.setQpsLimit(req.getQpsLimit());
        if (req.getTimeoutMs() != null) config.setTimeoutMs(req.getTimeoutMs());
        if (req.getRetryCount() != null) config.setRetryCount(req.getRetryCount());
        keyRepository.update(config);
        syncModelConfigs(id, tenantId, config.getModelMapping());
        return toResponse(keyRepository.findById(id));
    }

    /**
     * 删除 API Key 配置
     *
     * @param id 配置ID
     */
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        modelConfigRepository.deleteByApiKeyId(id);
        keyRepository.delete(id, tenantId);
    }

    /**
     * 更新 API Key 配置状态（启用/禁用）
     *
     * @param id      配置ID
     * @param enabled 是否启用
     */
    public void updateStatus(Long id, boolean enabled) {
        Long tenantId = TenantContext.getTenantId();
        keyRepository.updateStatus(id, tenantId, enabled ? 1 : 0);
    }

    /**
     * 健康检查
     * <p>
     * 返回指定 API Key 配置的当前健康状态。
     * </p>
     *
     * @param id 配置ID
     * @return 健康检查结果
     * @throws RouterException 当配置不存在时抛出 404
     */
    public Map<String, Object> healthCheck(Long id) {
        ApiKeyConfig config = keyRepository.findById(id);
        if (config == null) throw new RouterException("RESOURCE_NOT_FOUND", "配置不存在", 404);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("health_status", config.getHealthStatus() != null ? config.getHealthStatus() : "unknown");
        result.put("last_check_at", java.time.LocalDateTime.now());
        return result;
    }

    /**
     * 将 Key 的 modelMapping 同步到 model_config 表。
     * 先删除该 Key 下的所有旧模型，再逐条插入新模型。
     * 校验对外模型名在租户内唯一。
     *
     * @param keyId        API Key ID
     * @param tenantId     租户 ID
     * @param modelMapping 模型映射（对外名 -> 真实名）
     */
    private void syncModelConfigs(Long keyId, Long tenantId, Map<String, String> modelMapping) {
        modelConfigRepository.deleteByApiKeyId(keyId);
        if (modelMapping == null || modelMapping.isEmpty()) return;
        for (Map.Entry<String, String> entry : modelMapping.entrySet()) {
            String displayName = entry.getKey();
            // 唯一性校验：检查租户内是否已有其他 Key 使用此模型名
            ModelConfig existing = modelConfigRepository.findByDisplayName(tenantId, displayName);
            if (existing != null && !existing.getApiKeyId().equals(keyId)) {
                throw new RouterException("MODEL_NAME_DUPLICATE",
                        "对外模型名 '" + displayName + "' 已被其他 Key 使用", 409);
            }
            ModelConfig mc = new ModelConfig();
            mc.setTenantId(tenantId);
            mc.setDisplayName(displayName);
            mc.setRealName(entry.getValue());
            mc.setApiKeyId(keyId);
            modelConfigRepository.save(mc);
        }
    }

    /**
     * 根据提供商推断协议类型
     *
     * @param provider 提供商名称
     * @return 协议类型（anthropic 或 openai）
     */
    private String inferProtocol(String provider) {
        return "anthropic".equalsIgnoreCase(provider) ? "anthropic" : "openai";
    }

    /**
     * 将领域对象转换为响应 Map（包含脱敏的 API Key）
     *
     * @param c API Key 配置领域对象
     * @return 响应 Map，包含完整配置信息（API Key 已脱敏）
     */
    private Map<String, Object> toResponse(ApiKeyConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("provider", c.getProvider());
        m.put("protocol", c.getProtocol());
        m.put("base_url", c.getBaseUrl());
        m.put("model_mapping", c.getModelMapping());
        m.put("priority", c.getPriority());
        m.put("max_concurrent", c.getMaxConcurrent());
        m.put("qps_limit", c.getQpsLimit());
        m.put("timeout_ms", c.getTimeoutMs());
        m.put("retry_count", c.getRetryCount());
        m.put("status", c.getStatus());
        m.put("health_status", c.getHealthStatus());
        m.put("api_key_masked", cryptoUtils.mask(c.getApiKey()));
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    /**
     * 将 DO 对象转换为响应 Map（不含脱敏 API Key，用于仓库层查询失败的回退场景）
     *
     * @param dO API Key 配置 DO 对象
     * @return 响应 Map，包含基本配置信息
     */
    private Map<String, Object> toResponseFromDO(ApiKeyConfigDO dO) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dO.getId());
        m.put("name", dO.getName());
        m.put("provider", dO.getProvider());
        m.put("protocol", dO.getProtocol());
        m.put("base_url", dO.getBaseUrl());
        m.put("model_mapping", convertModelMapping(dO.getModelMapping()));
        m.put("priority", dO.getPriority());
        m.put("status", dO.getStatus());
        m.put("health_status", dO.getHealthStatus());
        m.put("created_at", dO.getCreatedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> convertModelMapping(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) return (Map<String, String>) raw;
        if (raw instanceof List) {
            Map<String, String> mapping = new LinkedHashMap<>();
            for (Object item : (List<?>) raw) {
                String s = String.valueOf(item);
                mapping.put(s, s);
            }
            return mapping;
        }
        return null;
    }
}
