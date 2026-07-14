package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import java.util.List;

/**
 * 路由策略接口，定义了从候选 API Key 列表中选择一个目标 Key 的契约。
 * 所有具体的路由算法（加权、轮询、优先级等）均应实现此接口。
 */
public interface RouteStrategy {
    /**
     * 从候选 API Key 列表中选出一个
     * @param candidates 候选的 API Key 配置列表
     * @return 选中的 API Key，若列表为空则返回 null
     */
    ApiKeyConfig select(List<ApiKeyConfig> candidates);

    /**
     * 返回策略名称，用于日志记录和路由追踪
     */
    String name();
}
