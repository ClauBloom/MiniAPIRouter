package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.RouteRule;
import java.util.List;

/**
 * 路由规则存储仓库接口（SPI 扩展点）。
 * <p>
 * 提供路由规则的持久化访问能力。路由规则定义了请求如何根据意图分类、
 * 优先级、权重等条件被分发到不同的上游 AI 模型/API。
 * </p>
 */
public interface RouteRuleRepository {

    /** 根据主键 ID 查询单条路由规则 */
    RouteRule findById(Long id);

    /** 查询指定租户下所有路由规则 */
    List<RouteRule> findByTenantId(Long tenantId);

    /** 查询指定租户下所有已启用的路由规则（用于路由决策时加载） */
    List<RouteRule> findEnabledRules(Long tenantId);

    /** 新增或保存一条路由规则，返回持久化后的对象 */
    RouteRule save(RouteRule rule);

    /** 更新已有路由规则 */
    void update(RouteRule rule);

    /** 删除指定租户下指定 ID 的路由规则 */
    void delete(Long id, Long tenantId);

    /** 启用或禁用指定路由规则 */
    void updateEnabled(Long id, Long tenantId, boolean enabled);
}
