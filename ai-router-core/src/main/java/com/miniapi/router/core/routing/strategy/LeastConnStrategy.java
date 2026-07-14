package com.miniapi.router.core.routing.strategy;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最少连接数路由策略：选择当前活跃连接数最少的候选 API Key，
 * 以实现负载均衡。连接数通过内部 ConcurrentHashMap 维护，
 * 每次路由前调用 acquire，请求结束后调用 release。
 */
@Component
public class LeastConnStrategy implements RouteStrategy {

    /** 记录每个 API Key 当前活跃连接数的并发安全映射表 */
    private final Map<Long, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    @Override
    public ApiKeyConfig select(List<ApiKeyConfig> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        /* 选择当前活跃连接数最少的 Key */
        return candidates.stream()
                .min(Comparator.comparingInt(k -> getConnCount(k.getId())))
                .orElse(candidates.get(0));
    }

    /** 增加指定 Key 的活跃连接计数 */
    public void acquire(Long keyId) {
        activeConnections.computeIfAbsent(keyId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /** 减少指定 Key 的活跃连接计数 */
    public void release(Long keyId) {
        AtomicInteger count = activeConnections.get(keyId);
        if (count != null) count.decrementAndGet();
    }

    /** 获取指定 Key 的当前活跃连接数 */
    private int getConnCount(Long keyId) {
        AtomicInteger count = activeConnections.get(keyId);
        return count != null ? count.get() : 0;
    }

    @Override
    public String name() { return "least_conn"; }
}
