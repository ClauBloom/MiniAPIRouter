package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 意图加权路由策略：基于意图分析结果提供的权重映射表进行加权随机选择。
 * 权重来自意图评估时动态生成的 weightMap，
 * 若 weightMap 中没有某 Key 的权重，则默认使用 1。
 */
public class IntentWeightStrategy implements RouteStrategy {

    /** 意图评估后的 Key ID -> 权重映射表，由 RoutePipeline 传入 */
    private final Map<Long, Integer> weightMap;

    public IntentWeightStrategy(Map<Long, Integer> weightMap) {
        this.weightMap = weightMap != null ? weightMap : Map.of();
    }

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        /* 根据 weightMap 构建每个候选 Key 的权重数组 */
        int[] weights = new int[candidates.size()];
        int totalWeight = 0;
        for (int i = 0; i < candidates.size(); i++) {
            ApiKeyConfig c = candidates.get(i);
            int w = weightMap.getOrDefault(c.getId(), 1);
            w = Math.max(1, w); // 确保权重至少为 1
            weights[i] = w;
            totalWeight += w;
        }

        if (totalWeight <= 0) return candidates.get(0);

        /* 按权重进行随机选择 */
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (r < cumulative) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    @Override
    public String name() { return "intent_weight"; }
}
