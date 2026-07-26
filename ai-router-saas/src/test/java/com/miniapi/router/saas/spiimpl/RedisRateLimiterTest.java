package com.miniapi.router.saas.spiimpl;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimiterTest {

    @Test
    void tryAcquireUsesOneAtomicScriptCall() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("rate_limit:tenant:9")), eq("60")))
                .thenReturn(1L);

        boolean acquired = new RedisRateLimiter(redis).tryAcquire("tenant:9", 2, 60);

        assertThat(acquired).isTrue();
        verify(redis).execute(any(RedisScript.class), eq(List.of("rate_limit:tenant:9")), eq("60"));
        verify(redis, never()).opsForValue();
    }

    @Test
    void tryAcquireRejectsCountAboveLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("rate_limit:tenant:9")), eq("1")))
                .thenReturn(3L);

        assertThat(new RedisRateLimiter(redis).tryAcquire("tenant:9", 2, 1)).isFalse();
    }
}
