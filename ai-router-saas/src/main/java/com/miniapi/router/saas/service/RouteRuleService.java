package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.RouteRuleRequest;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.entity.RouteRuleDO;
import com.miniapi.router.saas.mapper.RouteRuleMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 路由规则服务
 * <p>
 * 提供路由规则的管理功能，包括创建、查询、更新、删除和启用/禁用。
 * 路由规则定义了如何根据请求特征（模型、意图等）选择上游 API Key，
 * 支持多种匹配类型和选择策略（权重、优先级等）。
 * </p>
 */
@Service
public class RouteRuleService {

    private final RouteRuleRepository ruleRepository;   // 路由规则仓库（SPI 层），支持缓存
    private final ApiKeyConfigRepository keyRepository;  // API Key 配置仓库，用于查询目标 Key 信息
    private final RouteRuleMapper mapper;                // MyBatis-Plus Mapper，用于分页查询

    public RouteRuleService(RouteRuleRepository ruleRepository, ApiKeyConfigRepository keyRepository, RouteRuleMapper mapper) {
        this.ruleRepository = ruleRepository;
        this.keyRepository = keyRepository;
        this.mapper = mapper;
    }

    /**
     * 创建路由规则
     *
     * @param req 路由规则请求对象
     * @return 创建后的规则信息
     */
    public Map<String, Object> create(RouteRuleRequest req) {
        Long tenantId = TenantContext.getTenantId();
        validateTargetKeys(tenantId, req.getTargetKeyIds());
        RouteRule rule = new RouteRule();
        rule.setTenantId(tenantId);
        rule.setRuleName(req.getRuleName());
        rule.setMatchType(req.getMatchType());
        rule.setMatchPattern(req.getMatchPattern());
        rule.setTargetKeyIds(req.getTargetKeyIds());
        rule.setStrategy(req.getStrategy());
        rule.setIntentModel(req.getIntentModel());
        rule.setIntentWeights(req.getIntentWeights());
        rule.setFallbackEnabled(req.getFallbackEnabled());
        rule.setAgentType(req.getAgentType());
        ruleRepository.save(rule);
        return toResponse(ruleRepository.findById(rule.getId()));
    }

    /**
     * 分页查询路由规则列表
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<RouteRuleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RouteRuleDO::getTenantId, tenantId).orderByDesc(RouteRuleDO::getCreatedAt);
        Page<RouteRuleDO> p = new Page<>(page, pageSize);
        Page<RouteRuleDO> result = mapper.selectPage(p, wrapper);
        // 批量查询本页规则的完整信息（单次 IN 查询，避免逐条回查）
        List<Long> ids = result.getRecords().stream().map(RouteRuleDO::getId).collect(Collectors.toList());
        List<RouteRule> rules = ruleRepository.findByIds(ids);
        List<Long> targetIds = rules.stream()
                .filter(rule -> rule.getTargetKeyIds() != null)
                .flatMap(rule -> rule.getTargetKeyIds().stream())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ApiKeyConfig> targetKeys = keyRepository.findByIds(targetIds).stream()
                .filter(key -> Objects.equals(key.getTenantId(), tenantId))
                .collect(Collectors.toMap(ApiKeyConfig::getId, key -> key));
        List<Map<String, Object>> list = rules.stream()
                .map(rule -> toResponse(rule, targetKeys))
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    /**
     * 根据ID查询路由规则
     *
     * @param id 规则ID
     * @return 规则信息
     * @throws RouterException 当规则不存在或不属于当前租户时抛出 404
     */
    public Map<String, Object> findById(Long id) {
        return toResponse(requireOwned(id, TenantContext.getTenantId()));
    }

    /**
     * 更新路由规则
     * <p>
     * 仅更新请求中非空的字段，支持部分更新。
     * </p>
     *
     * @param id  规则ID
     * @param req 更新请求对象
     * @return 更新后的规则信息
     * @throws RouterException 当规则不存在或不属于当前租户时抛出 404
     */
    public Map<String, Object> update(Long id, RouteRuleRequest req) {
        Long tenantId = TenantContext.getTenantId();
        RouteRule rule = requireOwned(id, tenantId);
        // 逐字段条件更新，仅更新非空字段
        if (req.getRuleName() != null) rule.setRuleName(req.getRuleName());
        if (req.getMatchType() != null) rule.setMatchType(req.getMatchType());
        if (req.getMatchPattern() != null) rule.setMatchPattern(req.getMatchPattern());
        if (req.getTargetKeyIds() != null) {
            validateTargetKeys(tenantId, req.getTargetKeyIds());
            rule.setTargetKeyIds(req.getTargetKeyIds());
        }
        if (req.getStrategy() != null) rule.setStrategy(req.getStrategy());
        if (req.getIntentModel() != null) rule.setIntentModel(req.getIntentModel());
        if (req.getIntentWeights() != null) rule.setIntentWeights(req.getIntentWeights());
        if (req.getFallbackEnabled() != null) rule.setFallbackEnabled(req.getFallbackEnabled());
        if (req.getMaxFallback() != null) rule.setMaxFallback(req.getMaxFallback());
        if (req.getPriority() != null) rule.setPriority(req.getPriority());
        if (req.getDescription() != null) rule.setDescription(req.getDescription());
        if (req.getAgentType() != null) rule.setAgentType(req.getAgentType());
        ruleRepository.update(rule);
        return toResponse(ruleRepository.findById(id));
    }

    /**
     * 删除路由规则
     *
     * @param id 规则ID
     */
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        requireOwned(id, tenantId);
        ruleRepository.delete(id, tenantId);
    }

    /**
     * 更新路由规则启用状态
     *
     * @param id      规则ID
     * @param enabled 是否启用
     */
    public void updateEnabled(Long id, boolean enabled) {
        Long tenantId = TenantContext.getTenantId();
        requireOwned(id, tenantId);
        ruleRepository.updateEnabled(id, tenantId, enabled);
    }

    /**
     * 校验规则存在且属于指定租户，否则统一返回 404，避免泄露其他租户的资源信息。
     */
    private RouteRule requireOwned(Long id, Long tenantId) {
        RouteRule rule = ruleRepository.findById(id);
        if (rule == null || !Objects.equals(rule.getTenantId(), tenantId)) {
            throw new RouterException("RESOURCE_NOT_FOUND", "规则不存在", 404);
        }
        return rule;
    }

    /**
     * 所有显式目标 Key 必须存在、启用且属于当前租户。
     */
    private void validateTargetKeys(Long tenantId, List<Long> targetKeyIds) {
        if (targetKeyIds == null || targetKeyIds.isEmpty()) return;
        Set<Long> requestedIds = new LinkedHashSet<>(targetKeyIds);
        List<ApiKeyConfig> keys = keyRepository.findByIds(new ArrayList<>(requestedIds));
        boolean valid = keys.size() == requestedIds.size()
                && keys.stream().allMatch(key -> Objects.equals(key.getTenantId(), tenantId));
        if (!valid) {
            throw new RouterException("INVALID_TARGET_KEYS", "目标 API Key 不存在或不属于当前租户", 400);
        }
    }

    /**
     * 将路由规则领域对象转换为响应 Map
     * <p>
     * 包含规则基本信息以及关联的目标 API Key 列表。
     * </p>
     *
     * @param rule 路由规则领域对象
     * @return 响应 Map
     */
    private Map<String, Object> toResponse(RouteRule rule) {
        Map<Long, ApiKeyConfig> keysById = Map.of();
        if (rule != null && rule.getTargetKeyIds() != null && !rule.getTargetKeyIds().isEmpty()) {
            keysById = keyRepository.findByIds(rule.getTargetKeyIds()).stream()
                    .filter(key -> Objects.equals(key.getTenantId(), rule.getTenantId()))
                    .collect(Collectors.toMap(ApiKeyConfig::getId, key -> key));
        }
        return toResponse(rule, keysById);
    }

    private Map<String, Object> toResponse(RouteRule rule, Map<Long, ApiKeyConfig> keysById) {
        if (rule == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rule.getId());
        m.put("rule_name", rule.getRuleName());
        m.put("match_type", rule.getMatchType());
        m.put("match_pattern", rule.getMatchPattern());
        m.put("target_key_ids", rule.getTargetKeyIds());
        // 查询并附加目标 Key 的详细信息
        if (rule.getTargetKeyIds() != null && !rule.getTargetKeyIds().isEmpty()) {
            List<Map<String, Object>> targetKeys = rule.getTargetKeyIds().stream()
                    .map(keysById::get)
                    .filter(Objects::nonNull)
                    .map(k -> {
                Map<String, Object> tk = new LinkedHashMap<>();
                tk.put("id", k.getId());
                tk.put("name", k.getName());
                tk.put("provider", k.getProvider());
                return tk;
            }).collect(Collectors.toList());
            m.put("target_keys", targetKeys);
        }
        m.put("strategy", rule.getStrategy());
        m.put("intent_model", rule.getIntentModel());
        m.put("intent_weights", rule.getIntentWeights());
        m.put("fallback_enabled", rule.getFallbackEnabled());
        m.put("max_fallback", rule.getMaxFallback());
        m.put("priority", rule.getPriority());
        m.put("enabled", rule.getEnabled());
        m.put("description", rule.getDescription());
        m.put("agent_type", rule.getAgentType());
        m.put("created_at", rule.getCreatedAt());
        return m;
    }
}
