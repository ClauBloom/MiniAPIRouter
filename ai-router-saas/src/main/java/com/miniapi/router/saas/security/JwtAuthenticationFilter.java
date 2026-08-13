package com.miniapi.router.saas.security;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

/** Validates access tokens against current user and tenant state. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final SysUserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final PermissionCatalog permissionCatalog;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SysUserMapper userMapper,
                                   TenantMapper tenantMapper, PermissionCatalog permissionCatalog) {
        this.tokenProvider = tokenProvider;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.permissionCatalog = permissionCatalog;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && tokenProvider.validateToken(token)) {
            authenticate(token);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clearIdentity();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token) {
        Claims claims = tokenProvider.parseToken(token);
        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (RuntimeException exception) {
            return;
        }
        String claimedRole = claims.get("role", String.class);
        Long claimedTenantId = numericClaim(claims.get("tenant_id"));
        SysUserDO user = userMapper.selectById(userId);
        if (!activeUserMatchesClaims(user, claimedRole, claimedTenantId)) {
            return;
        }
        if (!activeTenant(user.getTenantId())) {
            return;
        }

        SaasPrincipal principal = new SaasPrincipal(user.getId(), user.getUsername(),
                user.getTenantId(), user.getRole(), permissionCatalog.permissionsFor(user.getRole()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.setAuthenticatedTenantId(user.getTenantId());
        TenantContext.setTenantId(user.getTenantId());
        TenantContext.setUserId(user.getId());
        TenantContext.setRole(user.getRole());
    }

    private boolean activeUserMatchesClaims(SysUserDO user, String role, Long tenantId) {
        return user != null && !Integer.valueOf(0).equals(user.getStatus())
                && Objects.equals(user.getRole(), role)
                && Objects.equals(user.getTenantId(), tenantId);
    }

    private boolean activeTenant(Long tenantId) {
        if (tenantId == null || tenantId == 0L) {
            return true;
        }
        TenantDO tenant = tenantMapper.selectById(tenantId);
        return tenant != null && !Integer.valueOf(0).equals(tenant.getStatus())
                && (tenant.getExpiresAt() == null || tenant.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    private Long numericClaim(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        return token.startsWith("sk-miniapi") ? null : token;
    }
}
