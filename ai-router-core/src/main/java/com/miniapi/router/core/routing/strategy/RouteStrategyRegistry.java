package com.miniapi.router.core.routing.strategy;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由策略注册表。管线路由只依赖策略抽象，不感知任何具体算法。
 */
@Component
public class RouteStrategyRegistry {

    private static final String DEFAULT_STRATEGY = "weight";
    private final Map<String, RouteStrategy> strategies;

    public RouteStrategyRegistry(List<RouteStrategy> strategies) {
        Map<String, RouteStrategy> indexed = new LinkedHashMap<>();
        for (RouteStrategy strategy : strategies) {
            RouteStrategy previous = indexed.putIfAbsent(strategy.name(), strategy);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate route strategy: " + strategy.name());
            }
        }
        if (!indexed.containsKey(DEFAULT_STRATEGY)) {
            throw new IllegalArgumentException("Missing default route strategy: " + DEFAULT_STRATEGY);
        }
        this.strategies = Map.copyOf(indexed);
    }

    public RouteStrategy resolve(String name) {
        return strategies.getOrDefault(name == null ? DEFAULT_STRATEGY : name,
                strategies.get(DEFAULT_STRATEGY));
    }
}
