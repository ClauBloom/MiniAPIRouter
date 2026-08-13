package com.miniapi.router.saas.service;

import com.miniapi.router.core.api.RouterCoreManagement;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApiKeyConfigHealthCheckTest {
    private ApiKeyConfigRepository keys;
    private RouterCoreManagement core;
    private ApiKeyConfigService service;

    @BeforeEach void setUp() {
        keys=mock(ApiKeyConfigRepository.class);
        core=mock(RouterCoreManagement.class);
        service=new ApiKeyConfigService(keys,mock(ApiKeyConfigMapper.class),mock(CryptoUtils.class),
                mock(ModelConfigRepository.class),mock(RouteRuleRepository.class),core);
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void healthCheckProbesUpstreamAndReturnsSafeResultWithoutKey() {
        ApiKeyConfig key=new ApiKeyConfig();key.setId(7L);key.setTenantId(10L);key.setApiKey("sk-secret");
        when(keys.findById(7L)).thenReturn(key);
        when(core.checkHealth(key)).thenReturn(new RouterCoreManagement.HealthCheckResult("healthy","HTTP 200"));

        Map<String,Object> result=service.healthCheck(7L);

        assertThat(result).containsEntry("status","healthy").containsEntry("detail","HTTP 200");
        assertThat(result.toString()).doesNotContain("sk-secret");
        verify(keys).updateHealthStatus(7L,"healthy");
    }

    @Test void concurrentHealthCheckForSameUpstreamIsRejected() throws Exception {
        ApiKeyConfig key=new ApiKeyConfig();key.setId(7L);key.setTenantId(10L);
        when(keys.findById(7L)).thenReturn(key);
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        when(core.checkHealth(key)).thenAnswer(invocation -> { entered.countDown(); release.await(); return new RouterCoreManagement.HealthCheckResult("healthy","HTTP 200"); });

        AtomicReference<Map<String,Object>> first=new AtomicReference<>();
        Thread worker=new Thread(() -> {
            TenantContext.setTenantId(10L);
            try { first.set(service.healthCheck(7L)); } finally { TenantContext.clear(); }
        });
        worker.start();
        entered.await();

        assertThatThrownBy(() -> service.healthCheck(7L))
                .isInstanceOfSatisfying(RouterException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("HEALTH_CHECK_IN_PROGRESS"));
        release.countDown();
        worker.join(2000);
        assertThat(first.get()).containsEntry("status","healthy");
    }
}
