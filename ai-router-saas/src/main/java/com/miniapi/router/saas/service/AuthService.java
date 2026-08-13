package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.dto.response.AuthSessionResponse;
import com.miniapi.router.saas.dto.response.CurrentUserResponse;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import com.miniapi.router.saas.security.JwtTokenProvider;
import com.miniapi.router.saas.security.PermissionCatalog;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Authentication and revocable management-session lifecycle. */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessions;
    private final PermissionCatalog permissions;

    public AuthService(SysUserMapper userMapper, TenantMapper tenantMapper,
                       JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder,
                       RefreshSessionService refreshSessions, PermissionCatalog permissions) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessions = refreshSessions;
        this.permissions = permissions;
    }

    @Transactional
    public SessionResult login(String username, String password, String tenantCode,
                               String userAgent, String ipAddress) {
        TenantDO requestedTenant = resolveRequestedTenant(tenantCode);
        Long requestedTenantId = requestedTenant == null ? 0L : requestedTenant.getId();
        SysUserDO user = userMapper.selectOne(new LambdaQueryWrapper<SysUserDO>()
                .eq(SysUserDO::getUsername, username)
                .eq(SysUserDO::getTenantId, requestedTenantId));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw unauthorized();
        }
        requireActiveUser(user);
        TenantDO tenant = requireActiveTenant(user.getTenantId(), requestedTenant);

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(safeIp(ipAddress));
        userMapper.updateById(user);

        RefreshSessionService.IssuedSession refresh = refreshSessions.create(
                user.getId(), user.getTenantId(), userAgent, ipAddress);
        return session(user, tenant, refresh.rawToken());
    }

    @Transactional
    public SessionResult refresh(String rawToken, String userAgent, String ipAddress) {
        RefreshSessionService.IssuedSession refresh = refreshSessions.rotate(rawToken, userAgent, ipAddress);
        SysUserDO user = userMapper.selectById(refresh.userId());
        requireActiveUser(user);
        TenantDO tenant = requireActiveTenant(user.getTenantId(), null);
        return session(user, tenant, refresh.rawToken());
    }

    public void logout(String rawToken) {
        refreshSessions.revoke(rawToken);
    }

    public CurrentUserResponse currentUser(Long userId) {
        SysUserDO user = userMapper.selectById(userId);
        requireActiveUser(user);
        TenantDO tenant = requireActiveTenant(user.getTenantId(), null);
        return toCurrentUser(user, tenant);
    }

    private SessionResult session(SysUserDO user, TenantDO tenant, String rawRefreshToken) {
        String tenantName = tenant == null ? "" : tenant.getTenantName();
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(),
                user.getRole(), user.getTenantId(), tenantName);
        CurrentUserResponse currentUser = toCurrentUser(user, tenant);
        return new SessionResult(new AuthSessionResponse(
                token, jwtTokenProvider.getExpirationMs() / 1000, currentUser), rawRefreshToken);
    }

    private CurrentUserResponse toCurrentUser(SysUserDO user, TenantDO tenant) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getNickname(),
                user.getRole(), user.getTenantId(), tenant == null ? "" : tenant.getTenantName(),
                permissions.permissionsFor(user.getRole()));
    }

    private TenantDO resolveRequestedTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        TenantDO tenant = tenantMapper.selectOne(new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getTenantCode, tenantCode.trim()));
        if (tenant == null) {
            throw unauthorized();
        }
        return tenant;
    }

    private void requireActiveUser(SysUserDO user) {
        if (user == null) {
            throw unauthorized();
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new RouterException("USER_DISABLED", "User is disabled", 401);
        }
    }

    private TenantDO requireActiveTenant(Long tenantId, TenantDO knownTenant) {
        if (tenantId == null || tenantId == 0L) {
            return null;
        }
        TenantDO tenant = knownTenant != null ? knownTenant : tenantMapper.selectById(tenantId);
        if (tenant == null || Integer.valueOf(0).equals(tenant.getStatus())) {
            throw new RouterException("TENANT_DISABLED", "Tenant is disabled", 403);
        }
        if (tenant.getExpiresAt() != null && tenant.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RouterException("TENANT_EXPIRED", "Tenant is expired", 403);
        }
        return tenant;
    }

    private RouterException unauthorized() {
        return new RouterException("UNAUTHORIZED", "Invalid username or password", 401);
    }

    private String safeIp(String ipAddress) {
        if (ipAddress == null) return null;
        return ipAddress.substring(0, Math.min(64, ipAddress.length()));
    }

    public record SessionResult(AuthSessionResponse response, String rawRefreshToken) {
    }
}
