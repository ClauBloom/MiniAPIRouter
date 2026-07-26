package com.miniapi.router.core.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上游 Key 冷却追踪器：基于请求失败反馈的被动健康保护（passive failover feedback）。
 * <p>
 * 当某个上游 Key 在冷却窗口内连续发生可回退错误（限流/5xx/超时）达到阈值时，
 * 进入"冷却"状态；{@link RoutePipeline} 在候选过滤时会暂时避开冷却中的 Key，
 * 防止持续把流量打向故障上游。
 * </p>
 * <p>
 * 设计要点（参考临时封禁 + TTL 自动恢复的网关实践）：
 * <ul>
 *   <li><b>自动恢复</b>：冷却状态由 Caffeine TTL 驱动，窗口内无新失败即自动过期恢复，
 *       无需定时任务，也无需人工干预；</li>
 *   <li><b>无持久化</b>：纯内存计数，不写数据库；进程重启即全量恢复（被动重新探测）；</li>
 *   <li><b>只统计可回退错误</b>：请求级确定性错误（400 等）不计入，
 *       避免把客户端请求问题归咎于上游 Key；</li>
 *   <li><b>fail-open</b>：调用方在所有候选均冷却时应忽略冷却状态照常路由，
 *       保证冷却机制只做"优先避开"，绝不额外制造 503。</li>
 * </ul>
 * </p>
 */
@Component
public class UpstreamCooldownTracker {

    private static final Logger log = LoggerFactory.getLogger(UpstreamCooldownTracker.class);

    /** 进入冷却所需的窗口内连续失败次数 */
    static final int FAILURE_THRESHOLD = 3;

    /** 冷却窗口时长（毫秒）：最后一次失败后经过该时长无新失败即自动恢复 */
    static final long COOLDOWN_MS = 60_000;

    /** Key ID -> 窗口内失败计数；写操作刷新 TTL，窗口静默后条目自动过期 */
    private final Cache<Long, AtomicInteger> failures;

    public UpstreamCooldownTracker() {
        this(Ticker.systemTicker());
    }

    /** 供测试注入可控时钟 */
    UpstreamCooldownTracker(Ticker ticker) {
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(COOLDOWN_MS, TimeUnit.MILLISECONDS)
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
    }

    /**
     * 记录一次可回退的上游失败。达到阈值时该 Key 进入冷却。
     *
     * @param keyId 上游 API Key ID
     */
    public void recordFailure(Long keyId) {
        if (keyId == null) {
            return;
        }
        /* compute 视为写操作，会刷新 expireAfterWrite TTL：持续失败则持续冷却 */
        AtomicInteger count = failures.asMap().compute(keyId,
                (k, v) -> v == null ? new AtomicInteger(1) : incremented(v));
        if (count.get() == FAILURE_THRESHOLD) {
            log.warn("[Cooldown] key_id={} reached {} consecutive upstream failures, "
                    + "cooling down for {}s", keyId, FAILURE_THRESHOLD, COOLDOWN_MS / 1000);
        }
    }

    /**
     * 记录一次上游成功，清除该 Key 的失败计数（立即解除冷却）。
     *
     * @param keyId 上游 API Key ID
     */
    public void recordSuccess(Long keyId) {
        if (keyId == null) {
            return;
        }
        failures.invalidate(keyId);
    }

    /**
     * 判断指定 Key 是否处于冷却状态。
     *
     * @param keyId 上游 API Key ID
     * @return true 表示窗口内失败已达阈值，应暂时避开该 Key
     */
    public boolean isCoolingDown(Long keyId) {
        if (keyId == null) {
            return false;
        }
        AtomicInteger count = failures.getIfPresent(keyId);
        return count != null && count.get() >= FAILURE_THRESHOLD;
    }

    /** 清空所有冷却状态（配置变更时调用） */
    public void clearAll() {
        failures.invalidateAll();
    }

    private static AtomicInteger incremented(AtomicInteger value) {
        value.incrementAndGet();
        return value;
    }
}
