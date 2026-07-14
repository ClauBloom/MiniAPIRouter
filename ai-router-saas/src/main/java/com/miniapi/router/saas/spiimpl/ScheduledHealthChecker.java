package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.HealthChecker;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时健康检查器
 * <p>
 * 实现 {@link HealthChecker} SPI 接口，定时对已启用的 API Key 配置进行健康探测。
 * 通过向上游提供商的 /v1/models 端点发送 HTTP 请求来检测可用性。
 * </p>
 * <p>
 * 健康状态分为三级：
 * <ul>
 *   <li>healthy - 探测成功，连续失败次数为 0</li>
 *   <li>degraded - 探测失败但未达到阈值（连续失败次数 < 3）</li>
 *   <li>down - 连续失败次数达到阈值（>= 3）</li>
 * </ul>
 * </p>
 */
@Component
public class ScheduledHealthChecker implements HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(ScheduledHealthChecker.class);
    private static final int FAILURE_THRESHOLD = 3;          // 连续失败次数阈值，达到后标记为 down
    private static final int PROBE_TIMEOUT_SECONDS = 10;      // 探测超时时间（秒）

    private final ApiKeyConfigMapper mapper;        // API Key 配置 Mapper
    private final ApiKeyConfigRepository keyRepository;  // API Key 配置仓库，用于更新健康状态
    private final CryptoUtils cryptoUtils;          // 加密工具类，用于解密 API Key
    // 各 Key 的连续失败计数器（线程安全）
    private final Map<Long, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final HttpClient httpClient;            // HTTP 客户端，用于发送探测请求

    /**
     * 构造函数
     * <p>
     * 初始化 HTTP 客户端，设置连接超时时间。
     * </p>
     *
     * @param mapper         API Key 配置 Mapper
     * @param keyRepository  API Key 配置仓库
     * @param cryptoUtils    加密工具类
     */
    public ScheduledHealthChecker(ApiKeyConfigMapper mapper, ApiKeyConfigRepository keyRepository,
                                  CryptoUtils cryptoUtils) {
        this.mapper = mapper;
        this.keyRepository = keyRepository;
        this.cryptoUtils = cryptoUtils;
        // 构建带连接超时的 HTTP 客户端
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * 定时健康检查任务
     * <p>
     * 每 60 秒执行一次，查询所有已启用且未删除的 API Key 配置并逐一探测。
     * </p>
     */
    @Scheduled(fixedDelay = 60000)
    public void scheduledCheck() {
        // 查询所有已启用且未删除的 Key 配置
        LambdaQueryWrapper<ApiKeyConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyConfigDO::getStatus, 1).eq(ApiKeyConfigDO::getDeleted, 0);
        List<ApiKeyConfigDO> keys = mapper.selectList(wrapper);
        log.info("[HealthCheck] Probing {} active key configs", keys.size());
        // 逐一探测
        for (ApiKeyConfigDO dO : keys) {
            try {
                probe(dO);
            } catch (Exception e) {
                log.warn("[HealthCheck] Error probing key {}: {}", dO.getId(), e.getMessage());
            }
        }
    }

    /**
     * 探测单个 API Key 的健康状态
     * <p>
     * 向上游提供商的 /v1/models 端点发送 GET 请求，根据响应状态码判断健康状态。
     * 支持 Anthropic 和 OpenAI 两种认证方式。
     * </p>
     *
     * @param dO API Key 配置 DO 对象
     */
    private void probe(ApiKeyConfigDO dO) {
        // 解密 API Key
        String apiKey = cryptoUtils.decrypt(dO.getApiKeyEnc());
        String baseUrl = dO.getBaseUrl();
        String protocol = dO.getProtocol() != null ? dO.getProtocol() : "openai";
        String probePath = "anthropic".equalsIgnoreCase(protocol) ? "/v1/models" : "/v1/models";

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + probePath))
                    .timeout(Duration.ofSeconds(PROBE_TIMEOUT_SECONDS))
                    .GET();

            // 根据协议设置不同的认证头
            if ("anthropic".equalsIgnoreCase(protocol)) {
                reqBuilder.header("x-api-key", apiKey);
                reqBuilder.header("anthropic-version", "2023-06-01");
            } else {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            // 发送请求（忽略响应体，仅关注状态码）
            HttpResponse<Void> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            // 2xx 和 4xx 视为健康（4xx 表示认证问题但服务可达），5xx 视为不可用
            if (status >= 200 && status < 500) {
                markHealthy(dO.getId());
            } else {
                log.warn("[HealthCheck] Key {} probe returned status {}", dO.getId(), status);
                markDown(dO.getId(), "HTTP " + status);
            }
        } catch (Exception e) {
            log.warn("[HealthCheck] Key {} probe failed: {}", dO.getId(), e.getMessage());
            markDown(dO.getId(), e.getClass().getSimpleName());
        }
    }

    /**
     * 检查指定 API Key 配置的健康状态
     * <p>
     * 从数据库加载最新的 Key 配置后执行探测。
     * </p>
     *
     * @param config API Key 配置领域对象
     */
    @Override
    public void check(ApiKeyConfig config) {
        if (config == null) return;
        ApiKeyConfigDO dO = mapper.selectById(config.getId());
        if (dO != null) probe(dO);
    }

    /**
     * 获取指定 Key 的当前健康状态
     *
     * @param keyId Key 配置ID
     * @return 健康状态字符串（healthy、degraded 或 down）
     */
    @Override
    public String getStatus(Long keyId) {
        AtomicInteger count = failureCounts.get(keyId);
        if (count == null || count.get() == 0) return "healthy";
        if (count.get() >= FAILURE_THRESHOLD) return "down";
        return "degraded";
    }

    /**
     * 标记 Key 为不可用
     * <p>
     * 递增连续失败计数器，达到阈值时更新状态为 down，否则为 degraded。
     * </p>
     *
     * @param keyId  Key 配置ID
     * @param reason 不可用原因
     */
    @Override
    public void markDown(Long keyId, String reason) {
        AtomicInteger count = failureCounts.computeIfAbsent(keyId, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        if (newCount >= FAILURE_THRESHOLD) {
            // 达到阈值，标记为 down
            keyRepository.updateHealthStatus(keyId, "down");
        } else {
            // 未达阈值，标记为 degraded
            keyRepository.updateHealthStatus(keyId, "degraded");
        }
    }

    /**
     * 标记 Key 为健康
     * <p>
     * 重置连续失败计数器并更新状态为 healthy。
     * </p>
     *
     * @param keyId Key 配置ID
     */
    @Override
    public void markHealthy(Long keyId) {
        AtomicInteger count = failureCounts.get(keyId);
        if (count != null) count.set(0);
        keyRepository.updateHealthStatus(keyId, "healthy");
    }
}
