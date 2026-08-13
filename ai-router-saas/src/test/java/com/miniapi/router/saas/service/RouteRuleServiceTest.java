package com.miniapi.router.saas.service;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.RouteRuleRequest;
import com.miniapi.router.saas.mapper.RouteRuleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

class RouteRuleServiceTest {

    private RouteRuleRepository ruleRepository;
    private ApiKeyConfigRepository keyRepository;
    private RouteRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(RouteRuleRepository.class);
        keyRepository = mock(ApiKeyConfigRepository.class);
        service = new RouteRuleService(
                ruleRepository,
                keyRepository,
                mock(RouteRuleMapper.class));
        TenantContext.setTenantId(10L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findByIdHidesRuleOwnedByAnotherTenant() {
        when(ruleRepository.findById(7L)).thenReturn(rule(7L, 20L));

        assertNotFound(() -> service.findById(7L));
    }

    @Test
    void deleteDoesNotTouchRuleOwnedByAnotherTenant() {
        when(ruleRepository.findById(7L)).thenReturn(rule(7L, 20L));

        assertNotFound(() -> service.delete(7L));

        verify(ruleRepository, never()).delete(7L, 10L);
    }

    @Test
    void updateEnabledDoesNotTouchRuleOwnedByAnotherTenant() {
        when(ruleRepository.findById(7L)).thenReturn(rule(7L, 20L));

        assertNotFound(() -> service.updateEnabled(7L, true));

        verify(ruleRepository, never()).updateEnabled(7L, 10L, true);
    }

    @Test
    void createRejectsDisabledTargetKey() {
        ApiKeyConfig disabled = new ApiKeyConfig();
        disabled.setId(99L);
        disabled.setTenantId(10L);
        disabled.setStatus(0);
        when(keyRepository.findByIds(List.of(99L))).thenReturn(List.of(disabled));
        RouteRuleRequest request = new RouteRuleRequest();
        request.setTargetKeyIds(List.of(99L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RouterException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("INVALID_TARGET_KEYS"));
        verify(ruleRepository, never()).save(any(RouteRule.class));
    }

    @Test
    void createRejectsTargetKeyOwnedByAnotherTenant() {
        ApiKeyConfig foreignKey = new ApiKeyConfig();
        foreignKey.setId(99L);
        foreignKey.setTenantId(20L);
        foreignKey.setStatus(1);
        when(keyRepository.findByIds(List.of(99L))).thenReturn(List.of(foreignKey));
        RouteRuleRequest request = new RouteRuleRequest();
        request.setTargetKeyIds(List.of(99L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(RouterException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("INVALID_TARGET_KEYS");
                    assertThat(error.getHttpStatus()).isEqualTo(400);
                });

        verify(ruleRepository, never()).save(any(RouteRule.class));
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(RouterException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
                    assertThat(error.getHttpStatus()).isEqualTo(404);
                });
    }

    private RouteRule rule(Long id, Long tenantId) {
        RouteRule rule = new RouteRule();
        rule.setId(id);
        rule.setTenantId(tenantId);
        return rule;
    }
}
