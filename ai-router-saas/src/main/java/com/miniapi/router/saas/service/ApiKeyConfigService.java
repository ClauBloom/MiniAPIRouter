package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.api.RouterCoreManagement;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.ApiKeyConfigRequest;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * API Key 配置服务
 * <p>
 * 提供上游 AI 提供商的 API Key 配置管理功能，包括创建、查询、更新、删除和健康检查。
 * 每个配置代表一个上游提供商的 API Key，包含提供商信息、协议类型、优先级、并发限制等参数。
 * </p>
 * <p>
 * 所有操作都基于当前租户上下文并校验资源归属，确保数据隔离。
 * </p>
 */
@Service
public class ApiKeyConfigService {

    private final ApiKeyConfigRepository keyRepository;  // API Key 配置仓库（SPI 层），支持缓存
    private final ApiKeyConfigMapper mapper;              // MyBatis-Plus Mapper，用于分页查询
    private final CryptoUtils cryptoUtils;                // 加密工具类，用于脱敏显示
    private final ModelConfigRepository modelConfigRepository;
    private final RouteRuleRepository routeRuleRepository;
    private final RouterCoreManagement coreManagement;
    private final Set<Long> healthChecksInFlight = ConcurrentHashMap.newKeySet();

    public ApiKeyConfigService(ApiKeyConfigRepository keyRepository, ApiKeyConfigMapper mapper, CryptoUtils cryptoUtils,
                               ModelConfigRepository modelConfigRepository, RouteRuleRepository routeRuleRepository,
                               RouterCoreManagement coreManagement) {
        this.keyRepository = keyRepository;
        this.mapper = mapper;
        this.cryptoUtils = cryptoUtils;
        this.modelConfigRepository = modelConfigRepository;
        this.routeRuleRepository = routeRuleRepository;
        this.coreManagement = coreManagement;
    }

    /**
     * 创建 API Key 配置
     *
     * @param req API Key 配置请求对象
     * @return 创建后的配置信息（脱敏后的 Map）
     */
    @Transactional
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
     * <p>
     * 直接从分页结果构建响应，模型映射通过一次查询按 Key 分组加载，避免逐条回查（N+1）。
     * </p>
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
        // 只查询当前页 Key 的模型映射并按 Key 分组
        List<Long> pageKeyIds = result.getRecords().stream()
                .map(ApiKeyConfigDO::getId)
                .collect(Collectors.toList());
        Map<Long, Map<String, String>> mappingsByKey = new HashMap<>();
        for (ModelConfig mc : modelConfigRepository.findByApiKeyIds(pageKeyIds)) {
            mappingsByKey.computeIfAbsent(mc.getApiKeyId(), k -> new LinkedHashMap<>())
                    .put(mc.getDisplayName(), mc.getRealName());
        }
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(dO -> toResponse(dO, mappingsByKey.get(dO.getId())))
                .collect(Collectors.toList());
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
    @Transactional
    public Map<String, Object> update(Long id, ApiKeyConfigRequest req) {
        Long tenantId = TenantContext.getTenantId();
        ApiKeyConfig config = requireOwned(id, tenantId);
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
        return toResponse(config);
    }

    /**
     * 删除 API Key 配置
     * <p>
     * 先校验资源归属再删除，防止跨租户越权删除关联的模型配置。
     * </p>
     *
     * @param id 配置ID
     * @throws RouterException 当配置不存在或不属于当前租户时抛出 404
     */
    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        requireOwned(id, tenantId);
        List<String> references = routeRuleRepository.findByTenantId(tenantId).stream()
                .filter(rule -> rule.getTargetKeyIds() != null && rule.getTargetKeyIds().contains(id))
                .map(RouteRule::getRuleName).filter(Objects::nonNull).limit(3).toList();
        if (!references.isEmpty()) {
            throw new RouterException("RESOURCE_IN_USE",
                    "上游仍被路由规则引用: " + String.join(", ", references), 409);
        }
        modelConfigRepository.deleteByApiKeyId(id);
        keyRepository.delete(id, tenantId);
    }

    /**
     * 更新 API Key 配置状态（启用/禁用）
     *
     * @param id      配置ID
     * @param enabled 是否启用
     * @throws RouterException 当配置不存在或不属于当前租户时抛出 404
     */
    public void updateStatus(Long id, boolean enabled) {
        Long tenantId = TenantContext.getTenantId();
        requireOwned(id, tenantId);
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
     * @throws RouterException 当配置不存在或不属于当前租户时抛出 404
     */
    public List<Map<String, Object>> listModels() {
        Long tenantId = TenantContext.getTenantId();
        List<ModelConfig> models = modelConfigRepository.findByTenantId(tenantId);
        Set<Long> keyIds = models.stream().map(ModelConfig::getApiKeyId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> keyNames = keyRepository.findByIds(new ArrayList<>(keyIds)).stream()
                .filter(key -> Objects.equals(key.getTenantId(), tenantId))
                .collect(Collectors.toMap(ApiKeyConfig::getId, ApiKeyConfig::getName));
        return models.stream().map(model -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.getId());
            item.put("tenant_id", model.getTenantId());
            item.put("display_name", model.getDisplayName());
            item.put("real_name", model.getRealName());
            item.put("api_key_id", model.getApiKeyId());
            item.put("upstream_name", keyNames.getOrDefault(model.getApiKeyId(), ""));
            return item;
        }).toList();
    }

    public Map<String, Object> healthCheck(Long id) {
        ApiKeyConfig config = requireOwned(id, TenantContext.getTenantId());
        if (!healthChecksInFlight.add(id)) {
            throw new RouterException("HEALTH_CHECK_IN_PROGRESS", "健康检查正在进行", 409);
        }
        try {
            RouterCoreManagement.HealthCheckResult probe = coreManagement.checkHealth(config);
            String status = "healthy".equals(probe.status()) ? "healthy" : "down";
            keyRepository.updateHealthStatus(id, status);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("status", probe.status());
            result.put("detail", probe.detail());
            result.put("checked_at", java.time.LocalDateTime.now());
            return result;
        } finally {
            healthChecksInFlight.remove(id);
        }
    }

    /**
     * 校验配置存在且属于指定租户，否则抛出 404。
     *
     * @param id       配置ID
     * @param tenantId 租户ID
     * @return API Key 配置领域对象
     */
    private ApiKeyConfig requireOwned(Long id, Long tenantId) {
        ApiKeyConfig config = keyRepository.findById(id);
        if (config == null || !config.getTenantId().equals(tenantId)) {
            throw new RouterException("RESOURCE_NOT_FOUND", "API Key 配置不存在", 404);
        }
        return config;
    }

    /**
     * 将 Key 的 modelMapping 同步到 model_config 表。
     * 先批量校验对外模型名在租户内唯一（单次查询），校验通过后再删除旧数据并插入新数据，
     * 避免校验失败时旧数据已被删除的部分写入问题。
     *
     * @param keyId        API Key ID
     * @param tenantId     租户 ID
     * @param modelMapping 模型映射（对外名 -> 真实名）
     */
    private void syncModelConfigs(Long keyId, Long tenantId, Map<String, String> modelMapping) {
        if (modelMapping != null && !modelMapping.isEmpty()) {
            // 一次性加载租户内全部模型名归属，批量校验唯一性
            Map<String, Long> ownerByDisplayName = modelConfigRepository.findByTenantId(tenantId).stream()
                    .collect(Collectors.toMap(ModelConfig::getDisplayName, ModelConfig::getApiKeyId, (a, b) -> a));
            for (String displayName : modelMapping.keySet()) {
                Long ownerKeyId = ownerByDisplayName.get(displayName);
                if (ownerKeyId != null && !ownerKeyId.equals(keyId)) {
                    throw new RouterException("MODEL_NAME_DUPLICATE",
                            "对外模型名 '" + displayName + "' 已被其他 Key 使用", 409);
                }
            }
        }
        modelConfigRepository.deleteByApiKeyId(keyId);
        if (modelMapping == null || modelMapping.isEmpty()) return;
        for (Map.Entry<String, String> entry : modelMapping.entrySet()) {
            ModelConfig mc = new ModelConfig();
            mc.setTenantId(tenantId);
            mc.setDisplayName(entry.getKey());
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
     * 将 DO 对象直接转换为响应 Map（用于列表查询，避免逐条回查仓库层）。
     * 输出字段与 {@link #toResponse(ApiKeyConfig)} 保持一致。
     *
     * @param dO           API Key 配置 DO 对象
     * @param modelMapping 该 Key 的模型映射（可为 null）
     * @return 响应 Map
     */
    private Map<String, Object> toResponse(ApiKeyConfigDO dO, Map<String, String> modelMapping) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dO.getId());
        m.put("name", dO.getName());
        m.put("provider", dO.getProvider());
        m.put("protocol", dO.getProtocol());
        m.put("base_url", dO.getBaseUrl());
        m.put("model_mapping", modelMapping);
        m.put("priority", dO.getPriority());
        m.put("max_concurrent", dO.getMaxConcurrent());
        m.put("qps_limit", dO.getQpsLimit());
        m.put("timeout_ms", dO.getTimeoutMs());
        m.put("retry_count", dO.getRetryCount());
        m.put("status", dO.getStatus());
        m.put("health_status", dO.getHealthStatus());
        m.put("api_key_masked", maskFromEnc(dO.getApiKeyEnc()));
        m.put("created_at", dO.getCreatedAt());
        return m;
    }

    /**
     * 从密文解密后脱敏展示；解密失败（历史数据/密钥轮换）时返回占位符而非报错。
     */
    private String maskFromEnc(String enc) {
        try {
            return cryptoUtils.mask(cryptoUtils.decrypt(enc));
        } catch (Exception e) {
            return "****";
        }
    }
}
