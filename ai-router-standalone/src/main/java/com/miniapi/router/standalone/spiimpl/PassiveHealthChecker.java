package com.miniapi.router.standalone.spiimpl;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.HealthChecker;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 被动健康检查器实现。
 * <p>
 * 实现 HealthChecker SPI 接口，采用被动健康检查策略：
 * 不主动发起健康检查请求，而是根据请求失败次数推断健康状态。
 * 连续失败达到阈值（3次）标记为 down，否则标记为 degraded。
 * </p>
 */
@Component
public class PassiveHealthChecker implements HealthChecker {

    private static final int FAILURE_THRESHOLD = 3; // 标记为 down 的失败次数阈值
    private final ApiKeyConfigRepository keyRepository; // API Key 仓储（用于更新健康状态）
    private final Map<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>(); // 各 Key 的失败次数计数

    public PassiveHealthChecker(ApiKeyConfigRepository keyRepository) {
        this.keyRepository = keyRepository;
    }

    /**
     * 主动健康检查（被动模式不实现，空方法）。
     *
     * @param config API Key 配置
     */
    @Override
    public void check(ApiKeyConfig config) {
    }

    /**
     * 获取指定 Key 的健康状态。
     * 根据失败次数判断：0次=healthy，达到阈值=down，其余=degraded。
     *
     * @param keyId API Key ID
     * @return 健康状态字符串（healthy/degraded/down）
     */
    @Override
    public String getStatus(Long keyId) {
        AtomicInteger count = failureCounts.get(keyId);
        if (count == null || count.get() == 0) return "healthy";
        if (count.get() >= FAILURE_THRESHOLD) return "down";
        return "degraded";
    }

    /**
     * 标记 Key 为不可用状态。
     * 递增失败计数，达到阈值时更新数据库状态为 down，否则为 degraded。
     *
     * @param keyId  API Key ID
     * @param reason 不可用原因
     */
    @Override
    public void markDown(Long keyId, String reason) {
        AtomicInteger count = failureCounts.computeIfAbsent(keyId, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        if (newCount >= FAILURE_THRESHOLD) {
            keyRepository.updateHealthStatus(keyId, "down");
        } else {
            keyRepository.updateHealthStatus(keyId, "degraded");
        }
    }

    /**
     * 标记 Key 为健康状态。
     * 重置失败计数并更新数据库状态为 healthy。
     *
     * @param keyId API Key ID
     */
    @Override
    public void markHealthy(Long keyId) {
        AtomicInteger count = failureCounts.get(keyId);
        if (count != null) count.set(0);
        keyRepository.updateHealthStatus(keyId, "healthy");
    }
}
