package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.saas.entity.RouteRuleDO;
import com.miniapi.router.saas.mapper.RouteRuleMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MyBatis 路由规则仓库
 * <p>
 * 实现 {@link RouteRuleRepository} SPI 接口，基于 MyBatis-Plus 和 Redis 缓存提供路由规则的数据访问层。
 * 启用状态的规则列表通过 Redis 缓存（按租户ID维度缓存），TTL 为 5 分钟，写操作时自动失效缓存。
 * </p>
 */
@Component
public class MybatisRouteRuleRepository implements RouteRuleRepository {

    private static final String CACHE_PREFIX = "rules:enabled:";  // Redis 缓存键前缀
    private static final long CACHE_TTL_MINUTES = 5;               // 缓存过期时间（分钟）

    private final RouteRuleMapper mapper;     // MyBatis-Plus Mapper
    private final StringRedisTemplate redis;  // Redis 模板

    public MybatisRouteRuleRepository(RouteRuleMapper mapper, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    /**
     * 根据ID查询路由规则
     *
     * @param id 规则ID
     * @return 路由规则领域对象，若不存在则返回 null
     */
    @Override
    public RouteRule findById(Long id) {
        RouteRuleDO dO = mapper.selectById(id);
        return dO != null ? toDomain(dO) : null;
    }

    /**
     * 根据租户ID查询所有路由规则
     *
     * @param tenantId 租户ID
     * @return 路由规则列表
     */
    @Override
    public List<RouteRule> findByTenantId(Long tenantId) {
        List<RouteRuleDO> list = mapper.selectList(
                new LambdaQueryWrapper<RouteRuleDO>().eq(RouteRuleDO::getTenantId, tenantId));
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * 查询指定租户的已启用路由规则
     * <p>
     * 优先从 Redis 缓存获取，缓存未命中时查询数据库（仅查询 enabled=1 的规则，按优先级升序排列）并写入缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 已启用的路由规则列表（按优先级升序排列）
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<RouteRule> findEnabledRules(Long tenantId) {
        String cacheKey = CACHE_PREFIX + tenantId;
        // 尝试从缓存获取
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return JsonUtils.fromJson(cached, new com.fasterxml.jackson.core.type.TypeReference<List<RouteRule>>() {});
            } catch (Exception ignored) {
                // 缓存反序列化失败，忽略并继续查询数据库
            }
        }
        // 缓存未命中，查询数据库：仅查询已启用的规则，按优先级升序排列
        List<RouteRuleDO> list = mapper.selectList(
                new LambdaQueryWrapper<RouteRuleDO>()
                        .eq(RouteRuleDO::getTenantId, tenantId)
                        .eq(RouteRuleDO::getEnabled, 1)
                        .orderByAsc(RouteRuleDO::getPriority));
        List<RouteRule> rules = list.stream().map(this::toDomain).collect(Collectors.toList());
        // 写入缓存
        redis.opsForValue().set(cacheKey, JsonUtils.toJson(rules), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return rules;
    }

    /**
     * 保存路由规则
     *
     * @param rule 路由规则领域对象
     * @return 保存后的规则（包含生成的ID）
     */
    @Override
    public RouteRule save(RouteRule rule) {
        RouteRuleDO dO = toDO(rule);
        mapper.insert(dO);
        rule.setId(dO.getId());
        // 清除该租户的规则缓存
        evict(rule.getTenantId());
        return rule;
    }

    /**
     * 更新路由规则
     *
     * @param rule 路由规则领域对象
     */
    @Override
    public void update(RouteRule rule) {
        RouteRuleDO dO = toDO(rule);
        mapper.updateById(dO);
        evict(rule.getTenantId());
    }

    /**
     * 删除路由规则
     *
     * @param id       规则ID
     * @param tenantId 租户ID
     */
    @Override
    public void delete(Long id, Long tenantId) {
        mapper.deleteById(id);
        evict(tenantId);
    }

    /**
     * 更新路由规则启用状态
     *
     * @param id       规则ID
     * @param tenantId 租户ID
     * @param enabled  是否启用
     */
    @Override
    public void updateEnabled(Long id, Long tenantId, boolean enabled) {
        RouteRuleDO dO = new RouteRuleDO();
        dO.setId(id);
        dO.setEnabled(enabled ? 1 : 0);
        mapper.updateById(dO);
        evict(tenantId);
    }

    /**
     * 清除指定租户的已启用规则缓存
     *
     * @param tenantId 租户ID
     */
    private void evict(Long tenantId) {
        if (tenantId != null) {
            redis.delete(CACHE_PREFIX + tenantId);
        }
    }

    /**
     * 将 DO 对象转换为领域对象
     * <p>
     * 将数据库中的整型布尔字段（0/1）转换为 Java 布尔类型。
     * </p>
     *
     * @param dO 路由规则 DO 对象
     * @return 路由规则领域对象
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
        // 整型转布尔：1=true，0=false
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
     * 将领域对象转换为 DO 对象
     * <p>
     * 将 Java 布尔类型转换为数据库中的整型字段（0/1）。
     * </p>
     *
     * @param r 路由规则领域对象
     * @return 路由规则 DO 对象
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
        // 布尔转整型
        dO.setFallbackEnabled(Boolean.TRUE.equals(r.getFallbackEnabled()) ? 1 : 0);
        dO.setMaxFallback(r.getMaxFallback());
        dO.setPriority(r.getPriority());
        dO.setEnabled(Boolean.TRUE.equals(r.getEnabled()) ? 1 : 0);
        dO.setDescription(r.getDescription());
        dO.setAgentType(r.getAgentType());
        return dO;
    }
}
