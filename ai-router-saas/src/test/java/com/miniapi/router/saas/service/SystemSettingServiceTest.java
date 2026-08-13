package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.SystemSettingDO;
import com.miniapi.router.saas.mapper.SystemSettingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemSettingServiceTest {
    private SystemSettingMapper mapper;
    private SystemSettingService service;

    @BeforeEach void setUp() {
        mapper=mock(SystemSettingMapper.class);
        service=new SystemSettingService(mapper);
        TenantContext.setUserId(1L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test
    @SuppressWarnings("unchecked")
    void updateOnlyAcceptsWhitelistedKeys() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateConfig(Map.of("jwt_secret","leak")))
                .isInstanceOfSatisfying(RouterException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("INVALID_SETTING"));
        verify(mapper, never()).insert(any(SystemSettingDO.class));

        service.updateConfig(Map.of("log_retention_days", 30));
        var captor = org.mockito.ArgumentCaptor.forClass(SystemSettingDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo("log_retention_days");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getConfigReturnsWhitelistWithDefaultsForUnsetKeys() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        Map<String,Object> config=service.getConfig();
        assertThat(config).containsKey("log_retention_days").containsKey("default_retry_count");
        assertThat(config).doesNotContainKey("jwt_secret");
    }
}
