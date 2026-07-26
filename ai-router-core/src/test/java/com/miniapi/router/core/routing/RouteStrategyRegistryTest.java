package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.routing.strategy.RouteStrategy;
import com.miniapi.router.core.routing.strategy.RouteStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteStrategyRegistryTest {

    @Test
    void resolvesRegisteredStrategyAndFallsBackToWeight() {
        RouteStrategy weight = strategy("weight");
        RouteStrategy custom = strategy("custom");
        RouteStrategyRegistry registry = new RouteStrategyRegistry(List.of(weight, custom));

        assertThat(registry.resolve("custom")).isSameAs(custom);
        assertThat(registry.resolve("missing")).isSameAs(weight);
        assertThat(registry.resolve(null)).isSameAs(weight);
    }

    @Test
    void rejectsDuplicateNames() {
        assertThatThrownBy(() -> new RouteStrategyRegistry(List.of(strategy("weight"), strategy("weight"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate route strategy");
    }

    private RouteStrategy strategy(String name) {
        return new RouteStrategy() {
            @Override
            public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
                return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
