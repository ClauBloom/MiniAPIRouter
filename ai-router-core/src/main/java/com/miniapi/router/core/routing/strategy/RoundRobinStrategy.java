package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询路由策略：依次轮流选择候选 API Key，保证每个 Key 被均匀分配到。
 * 使用 AtomicInteger 维护全局计数器，线程安全。
 */
@Component
public class RoundRobinStrategy implements RouteStrategy {

    /** 全局自增计数器，线程安全 */
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        /* 用计数器对候选列表大小取模，实现轮询效果 */
        int idx = Math.abs(counter.getAndIncrement()) % candidates.size();
        return candidates.get(idx);
    }

    @Override
    public String name() { return "round_robin"; }
}
