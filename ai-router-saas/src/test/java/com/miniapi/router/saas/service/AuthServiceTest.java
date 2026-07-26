package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import com.miniapi.router.saas.security.JwtTokenProvider;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private SysUserMapper userMapper;
    private TenantMapper tenantMapper;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, SysUserDO.class);
    }

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        tenantMapper = mock(TenantMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        service = new AuthService(userMapper, tenantMapper, jwtTokenProvider, passwordEncoder);

        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateToken(anyLong(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn("jwt");
    }

    @Test
    void rejectsUnknownTenantCodeEvenWhenUsernameExistsElsewhere() {
        when(tenantMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(user(1L, 99L, "same-name"));

        assertThatThrownBy(() -> service.login("same-name", "password", "missing"))
                .isInstanceOfSatisfying(RouterException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("UNAUTHORIZED");
                    assertThat(error.getHttpStatus()).isEqualTo(401);
                });
    }

    @Test
    void scopesTenantLoginByResolvedTenantId() {
        TenantDO tenant = tenant(42L, "demo");
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        when(tenantMapper.selectById(42L)).thenReturn(tenant);
        when(userMapper.selectOne(any())).thenReturn(user(2L, 42L, "demo_admin"));

        service.login("demo_admin", "password", "demo");

        Collection<Object> values = capturedUserQueryValues();
        assertThat(values).contains("demo_admin", 42L);
    }

    @Test
    void scopesLoginWithoutTenantCodeToPlatformTenant() {
        when(userMapper.selectOne(any())).thenReturn(user(1L, 0L, "admin"));

        service.login("admin", "password", null);

        Collection<Object> values = capturedUserQueryValues();
        assertThat(values).contains("admin", 0L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Collection<Object> capturedUserQueryValues() {
        ArgumentCaptor<LambdaQueryWrapper<SysUserDO>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectOne(captor.capture());
        LambdaQueryWrapper<SysUserDO> wrapper = captor.getValue();
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values();
    }

    private SysUserDO user(Long id, Long tenantId, String username) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPassword("hash");
        user.setRole(tenantId == 0 ? "super_admin" : "tenant_admin");
        user.setStatus(1);
        return user;
    }

    private TenantDO tenant(Long id, String code) {
        TenantDO tenant = new TenantDO();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setTenantName("Demo");
        tenant.setStatus(1);
        return tenant;
    }
}
