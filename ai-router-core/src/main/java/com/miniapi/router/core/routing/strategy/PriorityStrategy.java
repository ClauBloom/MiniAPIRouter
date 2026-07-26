package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 优先级路由策略：在优先级值最小的候选 API Key 层级内均匀分配流量。
 * 优先级值来自 ApiKeyConfig.getPriority()，值越小优先级越高。
 * 若多个 Key 优先级相同，使用蓄水池抽样以 O(n) 时间、O(1) 空间均匀选择。
 */
@Component
public class PriorityStrategy implements RouteStrategy {

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        ApiKeyConfig selected = null;
        int bestPriority = Integer.MAX_VALUE;
        int tierSize = 0;

        for (ApiKeyConfig candidate : candidates) {
            int priority = candidate.getPriority() != null ? candidate.getPriority() : 0;
            if (priority < bestPriority) {
                bestPriority = priority;
                selected = candidate;
                tierSize = 1;
            } else if (priority == bestPriority) {
                tierSize++;
                if (ThreadLocalRandom.current().nextInt(tierSize) == 0) {
                    selected = candidate;
                }
            }
        }
        return selected;
    }

    @Override
    public String name() { return "priority"; }
}
