package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 优先级路由策略：选择优先级值最小的候选 API Key。
 * 优先级值来自 ApiKeyConfig.getPriority()，值越小优先级越高。
 * 若优先级相同，则取第一个匹配项。
 */
@Component
public class PriorityStrategy implements RouteStrategy {

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        /* 按 priority 升序排序取最小值，即优先级最高的 Key */
        return candidates.stream()
                .min(Comparator.comparingInt(k -> k.getPriority() != null ? k.getPriority() : 0))
                .orElse(candidates.get(0));
    }

    @Override
    public String name() { return "priority"; }
}
