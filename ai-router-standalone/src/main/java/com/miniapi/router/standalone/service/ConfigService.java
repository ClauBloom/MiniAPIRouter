package com.miniapi.router.standalone.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.routing.FailureTracker;
import com.miniapi.router.core.routing.SessionRouteMemory;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.standalone.entity.ApiKeyConfigDO;
import com.miniapi.router.standalone.entity.IntentConfigDO;
import com.miniapi.router.standalone.entity.RouteRuleDO;
import com.miniapi.router.standalone.mapper.ApiKeyConfigMapper;
import com.miniapi.router.standalone.mapper.IntentConfigMapper;
import com.miniapi.router.standalone.mapper.RouteRuleMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置管理服务。
 * <p>
 * 提供 API Key 配置、路由规则和意图配置的业务逻辑处理。
 * 每次配置变更后会清除失败追踪器和会话路由内存缓存，确保路由使用最新配置。
 * </p>
 */
@Service
public class ConfigService {

    private static final Long TENANT_ID = 1L; // 独立版固定租户 ID

    private final ApiKeyConfigRepository keyRepository;   // API Key 仓储
    private final RouteRuleRepository ruleRepository;     // 路由规则仓储
    private final ApiKeyConfigMapper apiKeyMapper;        // API Key Mapper（直接操作数据库）
    private final RouteRuleMapper ruleMapper;             // 路由规则 Mapper
    private final IntentConfigMapper intentMapper;        // 意图配置 Mapper
    private final FailureTracker failureTracker;          // 失败追踪器（配置变更后清除）
    private final SessionRouteMemory sessionRouteMemory;  // 会话路由内存（配置变更后清除）
    private final ModelConfigRepository modelConfigRepository;

    public ConfigService(ApiKeyConfigRepository keyRepository, RouteRuleRepository ruleRepository,
                         ApiKeyConfigMapper apiKeyMapper, RouteRuleMapper ruleMapper,
                         IntentConfigMapper intentMapper, FailureTracker failureTracker,
                         SessionRouteMemory sessionRouteMemory, ModelConfigRepository modelConfigRepository) {
        this.keyRepository = keyRepository;
        this.ruleRepository = ruleRepository;
        this.apiKeyMapper = apiKeyMapper;
        this.ruleMapper = ruleMapper;
        this.intentMapper = intentMapper;
        this.failureTracker = failureTracker;
        this.sessionRouteMemory = sessionRouteMemory;
        this.modelConfigRepository = modelConfigRepository;
    }

    // ===== API Key 配置管理 =====

    /**
     * 创建 API Key 配置。
     * 设置默认值后保存，并清除路由缓存。
     *
     * @param config API Key 配置
     * @return 创建后的 API Key 响应数据
     */
    public Map<String, Object> createKey(ApiKeyConfig config) {
        config.setTenantId(TENANT_ID);
        // 设置默认值
        if (config.getStatus() == null) config.setStatus(1);
        if (config.getPriority() == null) config.setPriority(0);
        if (config.getMaxConcurrent() == null) config.setMaxConcurrent(10);
        if (config.getTimeoutMs() == null) config.setTimeoutMs(30000);
        if (config.getRetryCount() == null) config.setRetryCount(1);
        if (config.getHealthStatus() == null) config.setHealthStatus("unknown");
        keyRepository.save(config);
        syncModelConfigs(config.getId(), TENANT_ID, config.getModelMapping());
        failureTracker.clearAll(); sessionRouteMemory.clearAll(); // 清除路由缓存
        return toKeyResponse(keyRepository.findById(config.getId()));
    }

    /**
     * 更新 API Key 配置。
     * 如果未提供 API Key，则保留原有的加密 Key。
     *
     * @param id     API Key ID
     * @param config 更新的配置
     * @return 更新后的 API Key 响应数据
     */
    public Map<String, Object> updateKey(Long id, ApiKeyConfig config) {
        ApiKeyConfig existing = keyRepository.findById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Key not found", 404);
        config.setId(id);
        config.setTenantId(TENANT_ID);
        // 如果未提供新的 API Key，保留原有加密 Key
        if (config.getApiKey() == null) config.setApiKeyEnc(existing.getApiKeyEnc());
        keyRepository.update(config);
        syncModelConfigs(id, TENANT_ID, config.getModelMapping());
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toKeyResponse(keyRepository.findById(id));
    }

    /**
     * 删除 API Key。
     *
     * @param id API Key ID
     */
    public void deleteKey(Long id) {
        modelConfigRepository.deleteByApiKeyId(id);
        keyRepository.delete(id, TENANT_ID);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 更新 API Key 的启用/禁用状态。
     *
     * @param id     API Key ID
     * @param status 状态（1=启用, 0=禁用）
     */
    public void updateKeyStatus(Long id, int status) {
        keyRepository.updateStatus(id, TENANT_ID, status);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 分页查询 API Key 列表。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 包含列表和分页信息的 Map
     */
    public Map<String, Object> listKeys(int page, int pageSize) {
        LambdaQueryWrapper<ApiKeyConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyConfigDO::getTenantId, TENANT_ID).orderByDesc(ApiKeyConfigDO::getCreatedAt);
        Page<ApiKeyConfigDO> p = new Page<>(page, pageSize);
        Page<ApiKeyConfigDO> result = apiKeyMapper.selectPage(p, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(dO -> toKeyResponse(keyRepository.findById(dO.getId())))
                .collect(Collectors.toList());
        return Map.of("list", list, "total", result.getTotal(), "page", page, "page_size", pageSize);
    }

    /**
     * 根据 ID 查询单个 API Key。
     *
     * @param id API Key ID
     * @return API Key 响应数据
     */
    public Map<String, Object> getKey(Long id) {
        ApiKeyConfig config = keyRepository.findById(id);
        if (config == null) throw new RouterException("RESOURCE_NOT_FOUND", "Key not found", 404);
        return toKeyResponse(config);
    }

    /**
     * 将 API Key 域对象转换为响应 Map（API Key 做掩码处理）。
     *
     * @param c API Key 域对象
     * @return 响应 Map
     */
    private Map<String, Object> toKeyResponse(ApiKeyConfig c) {
        if (c == null) return Map.of();
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
        m.put("api_key_masked", maskKey(c.getApiKey())); // 掩码处理，不返回完整 Key
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    /**
     * 对 API Key 进行掩码处理（仅显示前 3 位和后 4 位）。
     *
     * @param key 原始 API Key
     * @return 掩码后的字符串
     */
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
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

    // ===== 路由规则管理 =====

    /**
     * 创建路由规则。
     * 设置默认值后保存，并清除路由缓存。
     *
     * @param rule 路由规则
     * @return 创建后的路由规则响应数据
     */
    public Map<String, Object> createRule(RouteRule rule) {
        rule.setTenantId(TENANT_ID);
        // 设置默认值
        if (rule.getStrategy() == null) rule.setStrategy("weight");
        if (rule.getMatchType() == null) rule.setMatchType("model");
        if (rule.getFallbackEnabled() == null) rule.setFallbackEnabled(true);
        if (rule.getMaxFallback() == null) rule.setMaxFallback(2);
        if (rule.getPriority() == null) rule.setPriority(0);
        if (rule.getEnabled() == null) rule.setEnabled(true);
        ruleRepository.save(rule);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toRuleResponse(ruleRepository.findById(rule.getId()));
    }

    /**
     * 更新路由规则。
     *
     * @param id   路由规则 ID
     * @param rule 更新的规则
     * @return 更新后的路由规则响应数据
     */
    public Map<String, Object> updateRule(Long id, RouteRule rule) {
        RouteRule existing = ruleRepository.findById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Rule not found", 404);
        rule.setId(id);
        rule.setTenantId(TENANT_ID);
        ruleRepository.update(rule);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toRuleResponse(ruleRepository.findById(id));
    }

    /**
     * 删除路由规则。
     *
     * @param id 路由规则 ID
     */
    public void deleteRule(Long id) {
        ruleRepository.delete(id, TENANT_ID);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 更新路由规则的启用/禁用状态。
     *
     * @param id      路由规则 ID
     * @param enabled 是否启用
     */
    public void updateRuleEnabled(Long id, boolean enabled) {
        ruleRepository.updateEnabled(id, TENANT_ID, enabled);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 分页查询路由规则列表。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 包含列表和分页信息的 Map
     */
    public Map<String, Object> listRules(int page, int pageSize) {
        LambdaQueryWrapper<RouteRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouteRuleDO::getTenantId, TENANT_ID).orderByDesc(RouteRuleDO::getCreatedAt);
        Page<RouteRuleDO> p = new Page<>(page, pageSize);
        Page<RouteRuleDO> result = ruleMapper.selectPage(p, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(dO -> toRuleResponse(ruleRepository.findById(dO.getId())))
                .collect(Collectors.toList());
        return Map.of("list", list, "total", result.getTotal(), "page", page, "page_size", pageSize);
    }

    /**
     * 根据 ID 查询单个路由规则。
     *
     * @param id 路由规则 ID
     * @return 路由规则响应数据
     */
    public Map<String, Object> getRule(Long id) {
        RouteRule rule = ruleRepository.findById(id);
        if (rule == null) throw new RouterException("RESOURCE_NOT_FOUND", "Rule not found", 404);
        return toRuleResponse(rule);
    }

    /**
     * 将路由规则域对象转换为响应 Map。
     * 如果配置了目标 Key ID，会查询并附加目标 Key 的摘要信息。
     *
     * @param r 路由规则域对象
     * @return 响应 Map
     */
    private Map<String, Object> toRuleResponse(RouteRule r) {
        if (r == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rule_name", r.getRuleName());
        m.put("match_type", r.getMatchType());
        m.put("match_pattern", r.getMatchPattern());
        m.put("target_key_ids", r.getTargetKeyIds());
        // 如果配置了目标 Key，查询并附加摘要信息
        if (r.getTargetKeyIds() != null && !r.getTargetKeyIds().isEmpty()) {
            List<ApiKeyConfig> keys = keyRepository.findByIds(r.getTargetKeyIds());
            List<Map<String, Object>> targetKeys = keys.stream().map(k -> {
                Map<String, Object> tk = new LinkedHashMap<>();
                tk.put("id", k.getId());
                tk.put("name", k.getName());
                tk.put("provider", k.getProvider());
                return tk;
            }).collect(Collectors.toList());
            m.put("target_keys", targetKeys);
        }
        m.put("strategy", r.getStrategy());
        m.put("intent_model", r.getIntentModel());
        m.put("intent_weights", r.getIntentWeights());
        m.put("fallback_enabled", r.getFallbackEnabled());
        m.put("max_fallback", r.getMaxFallback());
        m.put("priority", r.getPriority());
        m.put("enabled", r.getEnabled());
        m.put("description", r.getDescription());
        m.put("agent_type", r.getAgentType());
        m.put("created_at", r.getCreatedAt());
        return m;
    }

    // ===== 意图配置管理 =====

    /**
     * 查询所有意图配置列表（按排序顺序排列）。
     *
     * @return 包含列表和总数的 Map
     */
    public Map<String, Object> listIntents() {
        LambdaQueryWrapper<IntentConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentConfigDO::getTenantId, TENANT_ID).orderByAsc(IntentConfigDO::getSortOrder);
        List<IntentConfigDO> list = intentMapper.selectList(wrapper);
        List<Map<String, Object>> result = list.stream()
                .map(this::toIntentResponse)
                .collect(Collectors.toList());
        return Map.of("list", result, "total", (long) list.size());
    }

    /**
     * 根据 ID 查询单个意图配置。
     *
     * @param id 意图配置 ID
     * @return 意图配置响应数据
     */
    public Map<String, Object> getIntent(Long id) {
        IntentConfigDO dO = intentMapper.selectById(id);
        if (dO == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        return toIntentResponse(dO);
    }

    /**
     * 创建意图配置。
     * 设置默认值并对齐目标 Key ID 后保存。
     *
     * @param config 意图配置
     * @return 创建后的意图配置响应数据
     */
    public Map<String, Object> createIntent(IntentConfig config) {
        config.setTenantId(TENANT_ID);
        // 设置默认值
        if (config.getEnabled() == null) config.setEnabled(true);
        if (config.getSortOrder() == null) config.setSortOrder(0);
        if (config.getTargetModels() == null) config.setTargetModels(List.of());
        if (config.getModelWeights() == null) config.setModelWeights(Map.of());
        alignTargetModels(config);
        IntentConfigDO dO = toIntentDO(config);
        dO.setIsDefault(0);   // 新建的意图不是默认意图
        dO.setCustomized(0);  // 新建的意图未被自定义
        intentMapper.insert(dO);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toIntentResponse(intentMapper.selectById(dO.getId()));
    }

    /**
     * 更新意图配置。
     * 如果更新的是默认意图，则同步配置到所有未自定义的意图（级联更新）。
     * 如果更新的是普通意图，则标记为已自定义。
     *
     * @param id     意图配置 ID
     * @param config 更新的配置
     * @return 更新后的意图配置响应数据
     */
    public Map<String, Object> updateIntent(Long id, IntentConfig config) {
        IntentConfigDO existing = intentMapper.selectById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        config.setId(id);
        config.setTenantId(TENANT_ID);
        alignTargetModels(config);
        IntentConfigDO dO = toIntentDO(config);

        // 判断是否为默认意图
        boolean isDefault = existing.getIsDefault() != null && existing.getIsDefault() == 1;
        if (isDefault) {
            // 默认意图更新：级联同步到所有未自定义的意图
            dO.setIsDefault(1);
            dO.setCustomized(0);
            intentMapper.updateById(dO);
            cascadeToNonCustomizedModels(dO.getTargetModels(), dO.getModelWeights());
        } else {
            // 普通意图更新：标记为已自定义
            dO.setCustomized(1);
            intentMapper.updateById(dO);
        }
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toIntentResponse(intentMapper.selectById(id));
    }

    /**
     * 删除意图配置（默认意图不允许删除）。
     *
     * @param id 意图配置 ID
     */
    public void deleteIntent(Long id) {
        IntentConfigDO existing = intentMapper.selectById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        // 默认意图不允许删除
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new RouterException("CANNOT_DELETE_DEFAULT", "默认意图路由不允许删除", 400);
        }
        intentMapper.deleteById(id);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 重置意图配置为跟随默认。
     * 将 customized 设为 0，并同步默认意图的 targetKeyIds 和 keyWeights。
     *
     * @param id 意图配置 ID
     */
    public void resetIntentToDefault(Long id) {
        IntentConfigDO existing = intentMapper.selectById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new RouterException("CANNOT_RESET_DEFAULT", "默认意图路由不允许此操作", 400);
        }
        IntentConfigDO dft = intentMapper.selectOne(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, TENANT_ID)
                        .eq(IntentConfigDO::getIsDefault, 1));
        if (dft == null) throw new RouterException("NO_DEFAULT_INTENT", "默认意图路由不存在", 500);
        existing.setTargetModels(dft.getTargetModels());
        existing.setModelWeights(dft.getModelWeights());
        existing.setCustomized(0);
        intentMapper.updateById(existing);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    /**
     * 根据权重 Map 的 Key 对齐目标 Key ID 列表。
     * 如果有权重配置，则从权重 Map 的 Key 中提取 Key ID 列表。
     *
     * @param config 意图配置
     */
    private void alignTargetKeyIds(IntentConfig config) {
        Map<String, Integer> kw = config.getKeyWeights();
        if (kw != null && !kw.isEmpty()) {
            List<Long> ids = kw.keySet().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            config.setTargetKeyIds(ids);
        }
    }

    /**
     * 根据 modelWeights 的 Key 对齐 targetModels 列表。
     */
    private void alignTargetModels(IntentConfig config) {
        Map<String, Integer> mw = config.getModelWeights();
        if (mw != null && !mw.isEmpty()) {
            config.setTargetModels(new ArrayList<>(mw.keySet()));
        }
    }

    /**
     * 将默认意图的配置级联同步到所有未自定义的意图。
     * 当默认意图被编辑后，所有未被用户自定义过的意图会继承默认意图的目标 Key 和权重配置。
     *
     * @param targetKeyIds 默认意图的目标 Key ID 列表
     * @param keyWeights   默认意图的权重配置
     */
    private void cascadeToNonCustomized(List<Long> targetKeyIds, Map<String, Integer> keyWeights) {
        // 查询所有未自定义的非默认意图
        List<IntentConfigDO> nonCustomized = intentMapper.selectList(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, TENANT_ID)
                        .eq(IntentConfigDO::getIsDefault, 0)
                        .eq(IntentConfigDO::getCustomized, 0));
        // 逐个同步配置
        for (IntentConfigDO d : nonCustomized) {
            d.setTargetKeyIds(targetKeyIds);
            d.setKeyWeights(keyWeights);
            intentMapper.updateById(d);
        }
    }

    /**
     * 将默认意图的模型配置级联同步到所有未自定义的意图。
     */
    private void cascadeToNonCustomizedModels(List<String> targetModels, Map<String, Integer> modelWeights) {
        List<IntentConfigDO> nonCustomized = intentMapper.selectList(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, TENANT_ID)
                        .eq(IntentConfigDO::getIsDefault, 0)
                        .eq(IntentConfigDO::getCustomized, 0));
        for (IntentConfigDO d : nonCustomized) {
            d.setTargetModels(targetModels);
            d.setModelWeights(modelWeights);
            intentMapper.updateById(d);
        }
    }

    /**
     * 将意图配置 DO 转换为响应 Map。
     * 如果配置了目标 Key ID，会查询并附加目标 Key 的摘要信息。
     *
     * @param dO 意图配置 DO
     * @return 响应 Map
     */
    private Map<String, Object> toIntentResponse(IntentConfigDO dO) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dO.getId());
        m.put("label", dO.getLabel());
        m.put("name", dO.getName());
        m.put("description", dO.getDescription());
        m.put("target_key_ids", dO.getTargetKeyIds());
        m.put("target_models", dO.getTargetModels());
        // 附加模型详情（所属 Key 名）
        if (dO.getTargetModels() != null && !dO.getTargetModels().isEmpty()) {
            List<Map<String, Object>> targetModelList = new ArrayList<>();
            for (String modelName : dO.getTargetModels()) {
                ModelConfig mc = modelConfigRepository.findByDisplayName(TENANT_ID, modelName);
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("display_name", modelName);
                if (mc != null) {
                    ApiKeyConfig k = keyRepository.findById(mc.getApiKeyId());
                    tm.put("key_name", k != null ? k.getName() : null);
                    tm.put("provider", k != null ? k.getProvider() : null);
                }
                targetModelList.add(tm);
            }
            m.put("target_model_details", targetModelList);
        }
        m.put("key_weights", dO.getKeyWeights());
        m.put("model_weights", dO.getModelWeights());
        m.put("sort_order", dO.getSortOrder());
        m.put("enabled", dO.getEnabled() != null && dO.getEnabled() == 1);
        m.put("is_default", dO.getIsDefault() != null && dO.getIsDefault() == 1);
        m.put("customized", dO.getCustomized() != null && dO.getCustomized() == 1);
        return m;
    }

    /**
     * 将意图配置域对象转换为 DO。
     *
     * @param c 意图配置域对象
     * @return 意图配置 DO
     */
    private IntentConfigDO toIntentDO(IntentConfig c) {
        IntentConfigDO dO = new IntentConfigDO();
        dO.setId(c.getId());
        dO.setTenantId(c.getTenantId());
        dO.setLabel(c.getLabel());
        dO.setName(c.getName());
        dO.setDescription(c.getDescription());
        dO.setTargetKeyIds(c.getTargetKeyIds());
        dO.setKeyWeights(c.getKeyWeights());
        dO.setTargetModels(c.getTargetModels());
        dO.setModelWeights(c.getModelWeights());
        dO.setSortOrder(c.getSortOrder());
        dO.setEnabled(Boolean.TRUE.equals(c.getEnabled()) ? 1 : 0); // Boolean 转 Integer
        return dO;
    }
}
