package com.miniapi.router.core.routing;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamCooldownTrackerTest {

    /** 可手动推进的时钟，用于验证 TTL 自动恢复 */
    private static final class FakeTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    @Test
    void keyEntersCooldownAfterThresholdFailures() {
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker();

        tracker.recordFailure(1L);
        tracker.recordFailure(1L);
        assertThat(tracker.isCoolingDown(1L)).isFalse();

        tracker.recordFailure(1L);
        assertThat(tracker.isCoolingDown(1L)).isTrue();
        // 其他 Key 不受影响
        assertThat(tracker.isCoolingDown(2L)).isFalse();
    }

    @Test
    void successClearsCooldownImmediately() {
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker();
        for (int i = 0; i < UpstreamCooldownTracker.FAILURE_THRESHOLD; i++) {
            tracker.recordFailure(1L);
        }
        assertThat(tracker.isCoolingDown(1L)).isTrue();

        tracker.recordSuccess(1L);
        assertThat(tracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void cooldownExpiresAutomaticallyAfterQuietWindow() {
        FakeTicker ticker = new FakeTicker();
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker(ticker);
        for (int i = 0; i < UpstreamCooldownTracker.FAILURE_THRESHOLD; i++) {
            tracker.recordFailure(1L);
        }
        assertThat(tracker.isCoolingDown(1L)).isTrue();

        // 冷却窗口内仍处于冷却
        ticker.advance(Duration.ofMillis(UpstreamCooldownTracker.COOLDOWN_MS - 1000));
        assertThat(tracker.isCoolingDown(1L)).isTrue();

        // 窗口静默期过后自动恢复
        ticker.advance(Duration.ofMillis(2000));
        assertThat(tracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void continuedFailuresKeepRefreshingCooldown() {
        FakeTicker ticker = new FakeTicker();
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker(ticker);
        for (int i = 0; i < UpstreamCooldownTracker.FAILURE_THRESHOLD; i++) {
            tracker.recordFailure(1L);
        }

        // 半个窗口后又失败一次：TTL 刷新，冷却延续
        ticker.advance(Duration.ofMillis(UpstreamCooldownTracker.COOLDOWN_MS / 2));
        tracker.recordFailure(1L);
        ticker.advance(Duration.ofMillis(UpstreamCooldownTracker.COOLDOWN_MS / 2 + 1000));
        assertThat(tracker.isCoolingDown(1L)).isTrue();
    }

    @Test
    void clearAllResetsEverything() {
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker();
        for (int i = 0; i < UpstreamCooldownTracker.FAILURE_THRESHOLD; i++) {
            tracker.recordFailure(1L);
        }
        tracker.clearAll();
        assertThat(tracker.isCoolingDown(1L)).isFalse();
    }

    @Test
    void nullKeyIdIsIgnored() {
        UpstreamCooldownTracker tracker = new UpstreamCooldownTracker();
        tracker.recordFailure(null);
        tracker.recordSuccess(null);
        assertThat(tracker.isCoolingDown(null)).isFalse();
    }
}
