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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private SysUserMapper userMapper;
    private UserService service;
    private RefreshSessionService refreshSessions;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        refreshSessions = mock(RefreshSessionService.class);
        auditLogService = mock(AuditLogService.class);
        service = new UserService(userMapper, mock(PasswordEncoder.class), refreshSessions, auditLogService);
        TenantContext.setTenantId(10L);
        TenantContext.setRole("tenant_admin");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void superAdminCreatesTenantAdministratorForExplicitTenant() {
        TenantContext.setTenantId(0L);
        TenantContext.setRole("super_admin");
        TenantContext.setUserId(1L);

        service.create(Map.of("tenant_id", 25L, "username", "tenant-owner",
                "password", "secret123", "role", "tenant_admin"));

        var captor = org.mockito.ArgumentCaptor.forClass(SysUserDO.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(25L);
        assertThat(captor.getValue().getRole()).isEqualTo("tenant_admin");
    }

    @Test
    void disablingUserRevokesRefreshSessions() {
        TenantContext.setTenantId(0L);
        TenantContext.setRole("super_admin");
        TenantContext.setUserId(1L);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 25L, "tenant_admin"));

        service.changeStatus(7L, 0);

        verify(refreshSessions).revokeAllForUser(7L);
        verify(auditLogService).record(eq("USER_STATUS_CHANGE"), eq("user"), eq(7L), eq(25L), any());
    }

    @Test
    void resetPasswordReturnsOneTimeValueAndRevokesSessions() {
        TenantContext.setTenantId(0L);
        TenantContext.setRole("super_admin");
        TenantContext.setUserId(1L);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        service = new UserService(userMapper, encoder, refreshSessions, auditLogService);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 25L, "tenant_admin"));
        when(encoder.encode(any(String.class))).thenReturn("bcrypt-hash");

        Map<String, Object> result = service.resetPassword(7L);

        assertThat(result.get("temporary_password")).asString().hasSizeGreaterThanOrEqualTo(16);
        verify(refreshSessions).revokeAllForUser(7L);
        verify(userMapper).updateById(argThat((SysUserDO user) -> "bcrypt-hash".equals(user.getPassword())));
        assertThat(result).doesNotContainKey("password_hash");
    }

    @Test
    void userCannotDisableOwnActiveAccount() {
        TenantContext.setTenantId(0L);
        TenantContext.setRole("super_admin");
        TenantContext.setUserId(7L);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 0L, "super_admin"));

        assertThatThrownBy(() -> service.changeStatus(7L, 0))
                .isInstanceOfSatisfying(RouterException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("SELF_ACCOUNT_CHANGE_FORBIDDEN"));
        verify(userMapper, never()).updateById(any(SysUserDO.class));
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
