package com.miniapi.router.saas.config;

import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataInitializerTest {

    @Test
    void missingBootstrapPasswordFailsBeforeCreatingPlatformAdmin() {
        TenantMapper tenantMapper = mock(TenantMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectCount(any())).thenReturn(0L);
        DataInitializer initializer = new DataInitializer(
                tenantMapper, userMapper, mock(PasswordEncoder.class));
        ReflectionTestUtils.setField(initializer, "adminDefaultPassword", "");
        ReflectionTestUtils.setField(initializer, "demoAdminDefaultPassword", "");

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAAS_ADMIN_DEFAULT_PASSWORD");
    }
}
