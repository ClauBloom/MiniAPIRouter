package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProxyApiKeyIdempotencyTest {
    @AfterEach void clear(){TenantContext.clear();}

    @Test
    @SuppressWarnings("unchecked")
    void sameIdempotencyKeyReusesFirstKeyAndDoesNotOverwriteRedis() {
        TenantContext.setTenantId(10L);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        TenantDO tenant = new TenantDO(); tenant.setId(10L); tenant.setTenantCode("demo");
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        AtomicReference<String> idemValue = new AtomicReference<>();
        doAnswer(inv -> {
            if (((String) inv.getArgument(0)).startsWith("proxykey:idem:")) {
                idemValue.set(inv.getArgument(1));
            }
            return null;
        }).when(ops).set(anyString(), anyString(), anyLong(), any());
        when(ops.get(anyString())).thenAnswer(inv -> idemValue.get());
        ProxyApiKeyController controller = new ProxyApiKeyController(tenantMapper, redis);

        ApiResponse<Object> first = controller.generate("idem-1");
        ApiResponse<Object> second = controller.generate("idem-1");

        Map<String,Object> firstData = (Map<String,Object>) first.getData();
        Map<String,Object> secondData = (Map<String,Object>) second.getData();
        assertThat(secondData.get("api_key")).isEqualTo(firstData.get("api_key"));
        verify(ops, times(1)).set(anyString(), anyString(), eq(365L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void distinctIdempotencyKeysProduceDistinctKeys() {
        TenantContext.setTenantId(10L);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        TenantDO tenant = new TenantDO(); tenant.setId(10L); tenant.setTenantCode("demo");
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String,String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        ProxyApiKeyController controller = new ProxyApiKeyController(tenantMapper, redis);

        ApiResponse<Object> first = controller.generate("idem-a");
        ApiResponse<Object> second = controller.generate("idem-b");

        assertThat(((Map<String,Object>) second.getData()).get("api_key"))
                .isNotEqualTo(((Map<String,Object>) first.getData()).get("api_key"));
    }
}
