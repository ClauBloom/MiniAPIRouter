package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机路由策略：根据每个 API Key 配置的权重值进行加权随机选择，
 * 权重越大的 Key 被选中的概率越高。
 * 权重值来自 ApiKeyConfig.getWeight()，默认为 1。
 */
@Component
public class WeightStrategy implements RouteStrategy {

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        /* 计算所有候选 Key 的总权重，权重为 null 时默认取 1 */
        int totalWeight = candidates.stream().mapToInt(k -> k.getWeight() != null ? k.getWeight() : 1).sum();
        if (totalWeight <= 0) return candidates.get(0);

        /* 在 [0, totalWeight) 范围内生成一个随机数，按累计权重区间定位选中 Key */
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (ApiKeyConfig c : candidates) {
            cumulative += (c.getWeight() != null ? c.getWeight() : 1);
            if (r < cumulative) return c;
        }
        return candidates.get(candidates.size() - 1);
    }

    @Override
    public String name() { return "weight"; }
}
