package com.miniapi.router.standalone.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.standalone.entity.RouteRuleDO;
import com.miniapi.router.standalone.mapper.RouteRuleMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 SQLite 的路由规则仓储实现。
 * <p>
 * 实现 RouteRuleRepository SPI 接口，使用 MyBatis-Plus Mapper 操作 SQLite 数据库。
 * 内置 Caffeine 缓存，缓存每个租户的启用路由规则列表，减少数据库查询。
 * 任何增删改操作都会清除对应租户的缓存。
 * </p>
 */
@Component
public class SqliteRouteRuleRepository implements RouteRuleRepository {

    private final RouteRuleMapper mapper;                       // 路由规则 Mapper
    private final Cache<Long, List<RouteRule>> enabledRulesCache; // 租户 ID -> 启用规则列表的缓存

    public SqliteRouteRuleRepository(RouteRuleMapper mapper) {
        this.mapper = mapper;
        this.enabledRulesCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES) // 缓存 5 分钟过期
                .maximumSize(50) // 最大缓存租户数
                .build();
    }

    /**
     * 根据 ID 查找路由规则。
     *
     * @param id 规则 ID
     * @return 路由规则，不存在返回 null
     */
    @Override
    public RouteRule findById(Long id) {
        RouteRuleDO dO = mapper.selectById(id);
        return dO != null ? toDomain(dO) : null;
    }

    /**
     * 查找指定租户的所有路由规则。
     *
     * @param tenantId 租户 ID
     * @return 路由规则列表
     */
    @Override
    public List<RouteRule> findByTenantId(Long tenantId) {
        List<RouteRuleDO> list = mapper.selectList(
                new LambdaQueryWrapper<RouteRuleDO>().eq(RouteRuleDO::getTenantId, tenantId));
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * 查找指定租户的已启用路由规则（优先从缓存读取）。
     * 按优先级升序排列。
     *
     * @param tenantId 租户 ID
     * @return 已启用的路由规则列表
     */
    @Override
    public List<RouteRule> findEnabledRules(Long tenantId) {
        List<RouteRule> cached = enabledRulesCache.getIfPresent(tenantId);
        if (cached != null) return cached;
        List<RouteRuleDO> list = mapper.selectList(
                new LambdaQueryWrapper<RouteRuleDO>()
                        .eq(RouteRuleDO::getTenantId, tenantId)
                        .eq(RouteRuleDO::getEnabled, 1) // 只查已启用的
                        .orderByAsc(RouteRuleDO::getPriority)); // 按优先级升序
        List<RouteRule> rules = list.stream().map(this::toDomain).collect(Collectors.toList());
        enabledRulesCache.put(tenantId, rules);
        return rules;
    }

    /**
     * 保存新的路由规则。保存后清除缓存。
     *
     * @param rule 路由规则
     * @return 保存后的路由规则（包含生成的 ID）
     */
    @Override
    public RouteRule save(RouteRule rule) {
        RouteRuleDO dO = toDO(rule);
        mapper.insert(dO);
        rule.setId(dO.getId());
        enabledRulesCache.invalidate(rule.getTenantId()); // 清除缓存
        return rule;
    }

    /**
     * 更新路由规则。更新后清除缓存。
     *
     * @param rule 路由规则
     */
    @Override
    public void update(RouteRule rule) {
        RouteRuleDO dO = toDO(rule);
        mapper.updateById(dO);
        enabledRulesCache.invalidate(rule.getTenantId());
    }

    /**
     * 删除路由规则（逻辑删除）。删除后清除缓存。
     *
     * @param id       规则 ID
     * @param tenantId 租户 ID
     */
    @Override
    public void delete(Long id, Long tenantId) {
        mapper.deleteById(id);
        enabledRulesCache.invalidate(tenantId);
    }

    /**
     * 更新路由规则的启用/禁用状态。更新后清除缓存。
     *
     * @param id       规则 ID
     * @param tenantId 租户 ID
     * @param enabled  是否启用
     */
    @Override
    public void updateEnabled(Long id, Long tenantId, boolean enabled) {
        RouteRuleDO dO = new RouteRuleDO();
        dO.setId(id);
        dO.setEnabled(enabled ? 1 : 0); // Boolean 转 Integer
        mapper.updateById(dO);
        enabledRulesCache.invalidate(tenantId);
    }

    /**
     * 将 DO 转换为域对象（Integer 转 Boolean）。
     *
     * @param dO 数据对象
     * @return 域对象
     */
    private RouteRule toDomain(RouteRuleDO dO) {
        RouteRule r = new RouteRule();
        r.setId(dO.getId());
        r.setTenantId(dO.getTenantId());
        r.setRuleName(dO.getRuleName());
        r.setMatchType(dO.getMatchType());
        r.setMatchPattern(dO.getMatchPattern());
        r.setTargetKeyIds(dO.getTargetKeyIds());
        r.setStrategy(dO.getStrategy());
        r.setIntentModel(dO.getIntentModel());
        r.setIntentWeights(dO.getIntentWeights());
        // Integer 转 Boolean
        r.setFallbackEnabled(dO.getFallbackEnabled() != null && dO.getFallbackEnabled() == 1);
        r.setMaxFallback(dO.getMaxFallback());
        r.setPriority(dO.getPriority());
        r.setEnabled(dO.getEnabled() != null && dO.getEnabled() == 1);
        r.setDescription(dO.getDescription());
        r.setAgentType(dO.getAgentType());
        r.setCreatedAt(dO.getCreatedAt());
        r.setUpdatedAt(dO.getUpdatedAt());
        return r;
    }

    /**
     * 将域对象转换为 DO（Boolean 转 Integer）。
     *
     * @param r 域对象
     * @return 数据对象
     */
    private RouteRuleDO toDO(RouteRule r) {
        RouteRuleDO dO = new RouteRuleDO();
        dO.setId(r.getId());
        dO.setTenantId(r.getTenantId());
        dO.setRuleName(r.getRuleName());
        dO.setMatchType(r.getMatchType());
        dO.setMatchPattern(r.getMatchPattern());
        dO.setTargetKeyIds(r.getTargetKeyIds());
        dO.setStrategy(r.getStrategy());
        dO.setIntentModel(r.getIntentModel());
        dO.setIntentWeights(r.getIntentWeights());
        // Boolean 转 Integer
        dO.setFallbackEnabled(Boolean.TRUE.equals(r.getFallbackEnabled()) ? 1 : 0);
        dO.setMaxFallback(r.getMaxFallback());
        dO.setPriority(r.getPriority());
        dO.setEnabled(Boolean.TRUE.equals(r.getEnabled()) ? 1 : 0);
        dO.setDescription(r.getDescription());
        dO.setAgentType(r.getAgentType());
        return dO;
    }
}
