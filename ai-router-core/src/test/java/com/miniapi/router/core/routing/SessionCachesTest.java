package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FailureTracker 与 SessionRouteMemory 迁移到 Caffeine 后的行为回归测试 */
class SessionCachesTest {

    @Test
    void failureTrackerCountsAndTriggersFallbackAtThreshold() {
        FailureTracker tracker = new FailureTracker();
        assertThat(tracker.getFailureCount("s1")).isZero();
        assertThat(tracker.shouldFallback("s1")).isFalse();

        tracker.incrementFailure("s1");
        tracker.incrementFailure("s1");
        assertThat(tracker.getFailureCount("s1")).isEqualTo(2);
        assertThat(tracker.shouldFallback("s1")).isFalse();

        tracker.incrementFailure("s1");
        assertThat(tracker.shouldFallback("s1")).isTrue();

        tracker.resetFailures("s1");
        assertThat(tracker.getFailureCount("s1")).isZero();
        assertThat(tracker.shouldFallback("s1")).isFalse();
    }

    @Test
    void failureTrackerClearAllRemovesAllSessions() {
        FailureTracker tracker = new FailureTracker();
        tracker.incrementFailure("a");
        tracker.incrementFailure("b");
        tracker.clearAll();
        assertThat(tracker.getFailureCount("a")).isZero();
        assertThat(tracker.getFailureCount("b")).isZero();
    }

    @Test
    void sessionRouteMemoryRecordsAndRecallsLastSuccess() {
        SessionRouteMemory memory = new SessionRouteMemory();
        assertThat(memory.getLastSuccess("s1")).isNull();

        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(7L);
        memory.recordSuccess("s1", key, "deepseek-v4", "coding", 55);

        SessionRouteMemory.CachedResult cached = memory.getLastSuccess("s1");
        assertThat(cached).isNotNull();
        assertThat(cached.keyId()).isEqualTo(7L);
        assertThat(cached.selectedModel()).isEqualTo("deepseek-v4");
        assertThat(cached.intent()).isEqualTo("coding");
        assertThat(cached.score()).isEqualTo(55);

        memory.clearAll();
        assertThat(memory.getLastSuccess("s1")).isNull();
    }
}
