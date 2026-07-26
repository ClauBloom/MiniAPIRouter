package com.miniapi.router.saas.service;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private SysUserMapper userMapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        service = new UserService(userMapper, mock(PasswordEncoder.class));
        TenantContext.setTenantId(10L);
        TenantContext.setRole("tenant_admin");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantAdminCannotCreateSuperAdmin() {
        Map<String, Object> body = Map.of(
                "username", "elevated",
                "password", "secret123",
                "role", "super_admin");

        assertForbidden(() -> service.create(body));

        verify(userMapper, never()).insert(any(SysUserDO.class));
    }

    @Test
    void tenantAdminCannotUpdateUserFromAnotherTenant() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 20L, "user"));

        assertNotFound(() -> service.update(7L, Map.of("nickname", "changed")));

        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    void tenantAdminCannotDeleteUserFromAnotherTenant() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 20L, "user"));

        assertNotFound(() -> service.delete(7L));

        verify(userMapper, never()).deleteById(7L);
    }

    @Test
    void tenantAdminCannotPromoteUserToSuperAdmin() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 10L, "user"));

        assertForbidden(() -> service.update(7L, Map.of("role", "super_admin")));

        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    void tenantAdminCannotManageExistingSuperAdmin() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 10L, "super_admin"));

        assertForbidden(() -> service.delete(7L));

        verify(userMapper, never()).deleteById(7L);
    }

    private void assertForbidden(Runnable action) {
        assertRouterError(action, "FORBIDDEN", 403);
    }

    private void assertNotFound(Runnable action) {
        assertRouterError(action, "RESOURCE_NOT_FOUND", 404);
    }

    private void assertRouterError(Runnable action, String errorCode, int status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(RouterException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(errorCode);
                    assertThat(error.getHttpStatus()).isEqualTo(status);
                });
    }

    private SysUserDO user(Long id, Long tenantId, String role) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setRole(role);
        return user;
    }
}
