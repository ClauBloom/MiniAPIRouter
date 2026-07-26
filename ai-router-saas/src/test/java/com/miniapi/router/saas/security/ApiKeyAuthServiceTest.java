package com.miniapi.router.saas.security;

import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void authenticationLooksUpHashedV2Index() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("apikey:tenant:demo")).thenReturn("10");
        when(redis.hasKey(anyString())).thenReturn(true);
        ApiKeyAuthService service = new ApiKeyAuthService(mock(TenantMapper.class), redis);

        ApiKeyAuthService.AuthResult result = service.authenticate("sk-miniapi-demo-secretpart");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(redis).hasKey(key.capture());
        assertThat(key.getValue()).startsWith("proxykey:v2:demo:");
        assertThat(key.getValue()).doesNotContain("secretpart");
    }

    @Test
    @SuppressWarnings("unchecked")
    void authenticationSupportsHyphenatedTenantCode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("apikey:tenant:acme-dev")).thenReturn("11");
        when(redis.hasKey(anyString())).thenReturn(true);
        ApiKeyAuthService service = new ApiKeyAuthService(mock(TenantMapper.class), redis);

        ApiKeyAuthService.AuthResult result = service.authenticate("sk-miniapi-acme-dev-secretpart");

        assertThat(result.success()).isTrue();
        assertThat(result.tenantId()).isEqualTo(11L);
        verify(values).get("apikey:tenant:acme-dev");
    }
}
