package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机路由策略：从候选 Key 中均匀随机选择一个。
 */
@Component
public class WeightStrategy implements RouteStrategy {

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        int totalWeight = candidates.size();
        if (totalWeight <= 0) return candidates.get(0);

        /* 在 [0, totalWeight) 范围内生成一个随机数，按累计权重区间定位选中 Key */
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (ApiKeyConfig c : candidates) {
            cumulative += 1;
            if (r < cumulative) return c;
        }
        return candidates.get(candidates.size() - 1);
    }

    @Override
    public String name() { return "weight"; }
}
