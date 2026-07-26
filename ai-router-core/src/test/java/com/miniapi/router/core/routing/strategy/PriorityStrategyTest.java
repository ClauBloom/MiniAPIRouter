package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityStrategyTest {

    private final PriorityStrategy strategy = new PriorityStrategy();

    @Test
    void distributesSelectionsAcrossKeysInBestPriorityTier() {
        ApiKeyConfig first = key(1L, 5);
        ApiKeyConfig second = key(2L, 5);
        ApiKeyConfig lowerTier = key(3L, 10);
        Set<Long> selected = new HashSet<>();

        for (int i = 0; i < 2_000; i++) {
            ApiKeyConfig choice = strategy.select(List.of(first, second, lowerTier));
            selected.add(choice.getId());
            assertThat(choice.getPriority()).isEqualTo(5);
        }

        assertThat(selected).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void treatsNullPriorityAsZeroAndHandlesBoundaryInputs() {
        ApiKeyConfig implicitTop = key(1L, null);
        ApiKeyConfig lowerTier = key(2L, 1);

        assertThat(strategy.select(List.of(implicitTop, lowerTier))).isSameAs(implicitTop);
        assertThat(strategy.select(List.of(implicitTop))).isSameAs(implicitTop);
        assertThat(strategy.select(List.of())).isNull();
        assertThat(strategy.select(null)).isNull();
    }

    private ApiKeyConfig key(Long id, Integer priority) {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(id);
        key.setPriority(priority);
        return key;
    }
}
