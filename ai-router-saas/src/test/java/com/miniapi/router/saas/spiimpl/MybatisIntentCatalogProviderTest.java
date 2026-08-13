package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.saas.entity.IntentConfigDO;
import com.miniapi.router.saas.mapper.IntentConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisIntentCatalogProviderTest {
    @Test
    @SuppressWarnings("unchecked")
    void findByLabelMapsEnabledWeightedIntentForRouting() {
        IntentConfigMapper mapper=mock(IntentConfigMapper.class);
        IntentConfigDO dO=new IntentConfigDO();
        dO.setId(5L);dO.setTenantId(10L);dO.setLabel("coding_review");dO.setName("Code review");
        dO.setTargetModels(List.of("qwen-max"));dO.setModelWeights(Map.of("qwen-max",80));dO.setEnabled(1);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(dO);
        MybatisIntentCatalogProvider provider=new MybatisIntentCatalogProvider(mapper);

        IntentConfig config=provider.findByLabel(10L,"coding_review");

        assertThat(config.getLabel()).isEqualTo("coding_review");
        assertThat(config.getModelWeights()).containsEntry("qwen-max",80);
        assertThat(config.getEnabled()).isTrue();
        verify(mapper).selectOne(any(Wrapper.class));
    }
}
