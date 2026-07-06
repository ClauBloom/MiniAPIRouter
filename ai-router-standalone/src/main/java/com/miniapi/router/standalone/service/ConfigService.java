package com.miniapi.router.standalone.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.routing.FailureTracker;
import com.miniapi.router.core.routing.SessionRouteMemory;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
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

@Service
public class ConfigService {

    private static final Long TENANT_ID = 1L;

    private final ApiKeyConfigRepository keyRepository;
    private final RouteRuleRepository ruleRepository;
    private final ApiKeyConfigMapper apiKeyMapper;
    private final RouteRuleMapper ruleMapper;
    private final IntentConfigMapper intentMapper;
    private final FailureTracker failureTracker;
    private final SessionRouteMemory sessionRouteMemory;

    public ConfigService(ApiKeyConfigRepository keyRepository, RouteRuleRepository ruleRepository,
                         ApiKeyConfigMapper apiKeyMapper, RouteRuleMapper ruleMapper,
                         IntentConfigMapper intentMapper, FailureTracker failureTracker,
                         SessionRouteMemory sessionRouteMemory) {
        this.keyRepository = keyRepository;
        this.ruleRepository = ruleRepository;
        this.apiKeyMapper = apiKeyMapper;
        this.ruleMapper = ruleMapper;
        this.intentMapper = intentMapper;
        this.failureTracker = failureTracker;
        this.sessionRouteMemory = sessionRouteMemory;
    }

    // ===== API Key Config =====

    public Map<String, Object> createKey(ApiKeyConfig config) {
        config.setTenantId(TENANT_ID);
        if (config.getStatus() == null) config.setStatus(1);
        if (config.getWeight() == null) config.setWeight(1);
        if (config.getPriority() == null) config.setPriority(0);
        if (config.getMaxConcurrent() == null) config.setMaxConcurrent(10);
        if (config.getTimeoutMs() == null) config.setTimeoutMs(30000);
        if (config.getRetryCount() == null) config.setRetryCount(1);
        if (config.getHealthStatus() == null) config.setHealthStatus("unknown");
        keyRepository.save(config);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toKeyResponse(keyRepository.findById(config.getId()));
    }

    public Map<String, Object> updateKey(Long id, ApiKeyConfig config) {
        ApiKeyConfig existing = keyRepository.findById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Key not found", 404);
        config.setId(id);
        config.setTenantId(TENANT_ID);
        if (config.getApiKey() == null) config.setApiKeyEnc(existing.getApiKeyEnc());
        keyRepository.update(config);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toKeyResponse(keyRepository.findById(id));
    }

    public void deleteKey(Long id) {
        keyRepository.delete(id, TENANT_ID);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    public void updateKeyStatus(Long id, int status) {
        keyRepository.updateStatus(id, TENANT_ID, status);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

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

    public Map<String, Object> getKey(Long id) {
        ApiKeyConfig config = keyRepository.findById(id);
        if (config == null) throw new RouterException("RESOURCE_NOT_FOUND", "Key not found", 404);
        return toKeyResponse(config);
    }

    private Map<String, Object> toKeyResponse(ApiKeyConfig c) {
        if (c == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("provider", c.getProvider());
        m.put("protocol", c.getProtocol());
        m.put("base_url", c.getBaseUrl());
        m.put("models", c.getModels());
        m.put("weight", c.getWeight());
        m.put("priority", c.getPriority());
        m.put("max_concurrent", c.getMaxConcurrent());
        m.put("qps_limit", c.getQpsLimit());
        m.put("timeout_ms", c.getTimeoutMs());
        m.put("retry_count", c.getRetryCount());
        m.put("status", c.getStatus());
        m.put("health_status", c.getHealthStatus());
        m.put("api_key_masked", maskKey(c.getApiKey()));
        m.put("created_at", c.getCreatedAt());
        return m;
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }

    // ===== Route Rule =====

    public Map<String, Object> createRule(RouteRule rule) {
        rule.setTenantId(TENANT_ID);
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

    public Map<String, Object> updateRule(Long id, RouteRule rule) {
        RouteRule existing = ruleRepository.findById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Rule not found", 404);
        rule.setId(id);
        rule.setTenantId(TENANT_ID);
        ruleRepository.update(rule);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toRuleResponse(ruleRepository.findById(id));
    }

    public void deleteRule(Long id) {
        ruleRepository.delete(id, TENANT_ID);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    public void updateRuleEnabled(Long id, boolean enabled) {
        ruleRepository.updateEnabled(id, TENANT_ID, enabled);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

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

    public Map<String, Object> getRule(Long id) {
        RouteRule rule = ruleRepository.findById(id);
        if (rule == null) throw new RouterException("RESOURCE_NOT_FOUND", "Rule not found", 404);
        return toRuleResponse(rule);
    }

    private Map<String, Object> toRuleResponse(RouteRule r) {
        if (r == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("rule_name", r.getRuleName());
        m.put("match_type", r.getMatchType());
        m.put("match_pattern", r.getMatchPattern());
        m.put("target_key_ids", r.getTargetKeyIds());
        if (r.getTargetKeyIds() != null && !r.getTargetKeyIds().isEmpty()) {
            List<ApiKeyConfig> keys = keyRepository.findByIds(r.getTargetKeyIds());
            List<Map<String, Object>> targetKeys = keys.stream().map(k -> {
                Map<String, Object> tk = new LinkedHashMap<>();
                tk.put("id", k.getId());
                tk.put("name", k.getName());
                tk.put("provider", k.getProvider());
                tk.put("weight", k.getWeight());
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
        m.put("created_at", r.getCreatedAt());
        return m;
    }

    // ===== Intent Config =====

    public Map<String, Object> listIntents() {
        LambdaQueryWrapper<IntentConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentConfigDO::getTenantId, TENANT_ID).orderByAsc(IntentConfigDO::getSortOrder);
        List<IntentConfigDO> list = intentMapper.selectList(wrapper);
        List<Map<String, Object>> result = list.stream()
                .map(this::toIntentResponse)
                .collect(Collectors.toList());
        return Map.of("list", result, "total", (long) list.size());
    }

    public Map<String, Object> getIntent(Long id) {
        IntentConfigDO dO = intentMapper.selectById(id);
        if (dO == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        return toIntentResponse(dO);
    }

    public Map<String, Object> createIntent(IntentConfig config) {
        config.setTenantId(TENANT_ID);
        if (config.getEnabled() == null) config.setEnabled(true);
        if (config.getSortOrder() == null) config.setSortOrder(0);
        if (config.getTargetKeyIds() == null) config.setTargetKeyIds(List.of());
        if (config.getKeyWeights() == null) config.setKeyWeights(Map.of());
        alignTargetKeyIds(config);
        IntentConfigDO dO = toIntentDO(config);
        dO.setIsDefault(0);
        dO.setCustomized(0);
        intentMapper.insert(dO);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toIntentResponse(intentMapper.selectById(dO.getId()));
    }

    public Map<String, Object> updateIntent(Long id, IntentConfig config) {
        IntentConfigDO existing = intentMapper.selectById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        config.setId(id);
        config.setTenantId(TENANT_ID);
        alignTargetKeyIds(config);
        IntentConfigDO dO = toIntentDO(config);

        boolean isDefault = existing.getIsDefault() != null && existing.getIsDefault() == 1;
        if (isDefault) {
            dO.setIsDefault(1);
            dO.setCustomized(0);
            intentMapper.updateById(dO);
            cascadeToNonCustomized(dO.getTargetKeyIds(), dO.getKeyWeights());
        } else {
            dO.setCustomized(1);
            intentMapper.updateById(dO);
        }
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
        return toIntentResponse(intentMapper.selectById(id));
    }

    public void deleteIntent(Long id) {
        IntentConfigDO existing = intentMapper.selectById(id);
        if (existing == null) throw new RouterException("RESOURCE_NOT_FOUND", "Intent not found", 404);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new RouterException("CANNOT_DELETE_DEFAULT", "默认意图路由不允许删除", 400);
        }
        intentMapper.deleteById(id);
        failureTracker.clearAll(); sessionRouteMemory.clearAll();
    }

    private void alignTargetKeyIds(IntentConfig config) {
        Map<String, Integer> kw = config.getKeyWeights();
        if (kw != null && !kw.isEmpty()) {
            List<Long> ids = kw.keySet().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            config.setTargetKeyIds(ids);
        }
    }

    private void cascadeToNonCustomized(List<Long> targetKeyIds, Map<String, Integer> keyWeights) {
        List<IntentConfigDO> nonCustomized = intentMapper.selectList(
                new LambdaQueryWrapper<IntentConfigDO>()
                        .eq(IntentConfigDO::getTenantId, TENANT_ID)
                        .eq(IntentConfigDO::getIsDefault, 0)
                        .eq(IntentConfigDO::getCustomized, 0));
        for (IntentConfigDO d : nonCustomized) {
            d.setTargetKeyIds(targetKeyIds);
            d.setKeyWeights(keyWeights);
            intentMapper.updateById(d);
        }
    }

    private Map<String, Object> toIntentResponse(IntentConfigDO dO) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dO.getId());
        m.put("label", dO.getLabel());
        m.put("name", dO.getName());
        m.put("description", dO.getDescription());
        m.put("target_key_ids", dO.getTargetKeyIds());
        if (dO.getTargetKeyIds() != null && !dO.getTargetKeyIds().isEmpty()) {
            List<ApiKeyConfig> keys = keyRepository.findByIds(dO.getTargetKeyIds());
            List<Map<String, Object>> targetKeys = keys.stream().map(k -> {
                Map<String, Object> tk = new LinkedHashMap<>();
                tk.put("id", k.getId());
                tk.put("name", k.getName());
                tk.put("provider", k.getProvider());
                tk.put("weight", k.getWeight());
                return tk;
            }).collect(Collectors.toList());
            m.put("target_keys", targetKeys);
        }
        m.put("key_weights", dO.getKeyWeights());
        m.put("sort_order", dO.getSortOrder());
        m.put("enabled", dO.getEnabled() != null && dO.getEnabled() == 1);
        m.put("is_default", dO.getIsDefault() != null && dO.getIsDefault() == 1);
        m.put("customized", dO.getCustomized() != null && dO.getCustomized() == 1);
        return m;
    }

    private IntentConfigDO toIntentDO(IntentConfig c) {
        IntentConfigDO dO = new IntentConfigDO();
        dO.setId(c.getId());
        dO.setTenantId(c.getTenantId());
        dO.setLabel(c.getLabel());
        dO.setName(c.getName());
        dO.setDescription(c.getDescription());
        dO.setTargetKeyIds(c.getTargetKeyIds());
        dO.setKeyWeights(c.getKeyWeights());
        dO.setSortOrder(c.getSortOrder());
        dO.setEnabled(Boolean.TRUE.equals(c.getEnabled()) ? 1 : 0);
        return dO;
    }
}
