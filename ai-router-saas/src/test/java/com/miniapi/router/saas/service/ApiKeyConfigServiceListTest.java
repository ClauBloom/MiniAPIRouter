package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyConfigServiceListTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listLoadsModelMappingsOnlyForKeysOnCurrentPage() {
        TenantContext.setTenantId(10L);
        ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
        ModelConfigRepository models = mock(ModelConfigRepository.class);
        CryptoUtils cryptoUtils = mock(CryptoUtils.class);
        ApiKeyConfigDO key = new ApiKeyConfigDO();
        key.setId(7L);
        key.setTenantId(10L);
        key.setApiKeyEnc("encrypted");
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<ApiKeyConfigDO> page = invocation.getArgument(0);
            page.setRecords(List.of(key));
            page.setTotal(1);
            return page;
        });
        when(models.findByApiKeyIds(List.of(7L))).thenReturn(List.of());
        when(cryptoUtils.decrypt("encrypted")).thenReturn("plain-key");
        when(cryptoUtils.mask("plain-key")).thenReturn("mask");
        ApiKeyConfigService service = new ApiKeyConfigService(
                mock(ApiKeyConfigRepository.class), mapper, cryptoUtils, models);

        service.list(1, 20, null, null, null);

        verify(models).findByApiKeyIds(List.of(7L));
        verify(models, never()).findByTenantId(10L);
    }
}
