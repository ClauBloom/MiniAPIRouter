package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProxyApiKeyControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsOnlyMaskedLegacyKeysAndUsesScan() {
        TenantContext.setTenantId(10L);
        TenantMapper tenantMapper = mock(TenantMapper.class);
        TenantDO tenant = new TenantDO();
        tenant.setId(10L);
        tenant.setTenantCode("demo");
        when(tenantMapper.selectById(10L)).thenReturn(tenant);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        Cursor<String> emptyV2 = mock(Cursor.class);
        Cursor<String> legacy = mock(Cursor.class);
        when(emptyV2.hasNext()).thenReturn(false);
        when(legacy.hasNext()).thenReturn(true, false);
        when(legacy.next()).thenReturn("proxykey:demo:0123456789abcdef");
        when(redis.scan(any(ScanOptions.class))).thenReturn(emptyV2, legacy);

        ApiResponse<Object> response = new ProxyApiKeyController(tenantMapper, redis).list();

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getData();
        assertThat(data).hasSize(1);
        assertThat(data.getFirst())
                .containsEntry("api_key_masked", "sk-miniapi-demo-...cdef")
                .doesNotContainKey("api_key");
        verify(redis, never()).keys(anyString());
        verify(emptyV2).close();
        verify(legacy).close();
    }
}
