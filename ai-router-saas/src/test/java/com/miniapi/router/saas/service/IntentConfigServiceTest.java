package com.miniapi.router.saas.service;

import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.IntentConfigRequest;
import com.miniapi.router.saas.entity.IntentConfigDO;
import com.miniapi.router.saas.mapper.IntentConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IntentConfigServiceTest {
    private IntentConfigMapper mapper;
    private ModelConfigRepository models;
    private IntentConfigService service;

    @BeforeEach void setUp() {
        mapper=mock(IntentConfigMapper.class);models=mock(ModelConfigRepository.class);
        service=new IntentConfigService(mapper,models);
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void rejectsWeightsReferencingModelsOutsideTenantCatalog() {
        when(models.findByTenantId(10L)).thenReturn(List.of(model("coding_review")));

        assertThatThrownBy(() -> service.create(new IntentConfigRequest("review", "review", null,
                null, Map.of("coding_review", 60, "foreign-model", 40), 0, true, false, true)))
                .isInstanceOfSatisfying(RouterException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("INVALID_INTENT_MODELS"));
        verify(mapper,never()).insert(any(IntentConfigDO.class));
    }

    @Test void createPersistsValidWeightsWithinTenant() {
        when(models.findByTenantId(10L)).thenReturn(List.of(model("coding_review"), model("math")));

        var result=service.create(new IntentConfigRequest("review","review","desc",
                null, Map.of("coding_review",60,"math",40), 0, true, false, true));

        var captor=org.mockito.ArgumentCaptor.forClass(IntentConfigDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(10L);
        assertThat(result).containsEntry("label","review");
    }

    private ModelConfig model(String displayName) {
        ModelConfig m=new ModelConfig();m.setTenantId(10L);m.setDisplayName(displayName);m.setApiKeyId(7L);return m;
    }
}
