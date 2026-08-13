package com.miniapi.router.saas.service;

import com.miniapi.router.core.api.RouterCoreManagement;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.domain.ModelConfig;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ApiKeyConfigServiceSafetyTest {
    private ApiKeyConfigRepository keys;
    private ModelConfigRepository models;
    private RouteRuleRepository rules;
    private ApiKeyConfigService service;

    @BeforeEach void setUp() {
        keys=mock(ApiKeyConfigRepository.class);models=mock(ModelConfigRepository.class);rules=mock(RouteRuleRepository.class);
        service=new ApiKeyConfigService(keys,mock(ApiKeyConfigMapper.class),mock(CryptoUtils.class),models,rules,mock(RouterCoreManagement.class));
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void listsOnlyCurrentTenantModelsWithOwnedUpstreamNames() {
        ModelConfig model=new ModelConfig();model.setId(12L);model.setTenantId(10L);model.setApiKeyId(7L);model.setDisplayName("smart-model");model.setRealName("vendor-model");
        ApiKeyConfig key=new ApiKeyConfig();key.setId(7L);key.setTenantId(10L);key.setName("Primary upstream");key.setProvider("openai");
        when(models.findByTenantId(10L)).thenReturn(List.of(model));when(keys.findByIds(List.of(7L))).thenReturn(List.of(key));

        var result=service.listModels();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("display_name","smart-model").containsEntry("upstream_name","Primary upstream");
            assertThat(item).doesNotContainKey("api_key");
        });
        verify(models).findByTenantId(10L);
    }

    @Test void deletingReferencedUpstreamReturnsSafeConflictWithoutDeletingModels() {
        ApiKeyConfig key=new ApiKeyConfig();key.setId(7L);key.setTenantId(10L);when(keys.findById(7L)).thenReturn(key);
        RouteRule rule=new RouteRule();rule.setId(3L);rule.setRuleName("Primary route");rule.setTargetKeyIds(List.of(7L));
        when(rules.findByTenantId(10L)).thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOfSatisfying(RouterException.class,error -> {
            assertThat(error.getHttpStatus()).isEqualTo(409);
            assertThat(error.getErrorCode()).isEqualTo("RESOURCE_IN_USE");
            assertThat(error.getMessage()).contains("Primary route").doesNotContain("api_key");
        });
        verify(models,never()).deleteByApiKeyId(7L);
        verify(keys,never()).delete(7L,10L);
    }
}
