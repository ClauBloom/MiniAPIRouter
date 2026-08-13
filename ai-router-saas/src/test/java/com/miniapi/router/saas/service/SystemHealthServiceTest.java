package com.miniapi.router.saas.service;

import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SystemHealthServiceTest {
    @Test void healthReflectsRealDependencyStateWithoutClaimingAbsentServices() {
        TenantMapper tenantMapper = mock(TenantMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(tenantMapper.selectCount(null)).thenReturn(1L);
        when(redis.hasKey(anyString())).thenReturn(Boolean.TRUE);
        SystemHealthService service = new SystemHealthService(tenantMapper, redis);

        Map<String,Object> health=service.health();

        assertThat(health).containsEntry("status","UP").containsEntry("database","UP").containsEntry("redis","UP");
        assertThat(health.toString()).doesNotContain("elasticsearch").doesNotContain("minio");
    }

    @Test void healthReportsDownRedis() {
        TenantMapper tenantMapper = mock(TenantMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(tenantMapper.selectCount(null)).thenReturn(1L);
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));
        SystemHealthService service = new SystemHealthService(tenantMapper, redis);

        Map<String,Object> health=service.health();

        assertThat(health).containsEntry("redis","DOWN").containsEntry("status","DOWN");
    }
}
