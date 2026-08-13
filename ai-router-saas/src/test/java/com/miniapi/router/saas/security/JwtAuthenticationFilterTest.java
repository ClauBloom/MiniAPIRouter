package com.miniapi.router.saas.security;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokens;
    private SysUserMapper users;
    private TenantMapper tenants;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokens = mock(JwtTokenProvider.class);
        users = mock(SysUserMapper.class);
        tenants = mock(TenantMapper.class);
        filter = new JwtAuthenticationFilter(tokens, users, tenants, new PermissionCatalog());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void authenticatesAgainstCurrentDatabaseRoleAndStatus() throws Exception {
        Claims claims = mock(Claims.class);
        when(tokens.validateToken("jwt")).thenReturn(true);
        when(tokens.parseToken("jwt")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("role", String.class)).thenReturn("tenant_admin");
        when(claims.get("tenant_id")).thenReturn(42);

        SysUserDO user = user(7L, 42L, "tenant_admin", 1);
        when(users.selectById(7L)).thenReturn(user);
        when(tenants.selectById(42L)).thenReturn(tenant(42L, 1));

        MockHttpServletRequest request = requestWithToken();
        AtomicReference<Object> principal = new AtomicReference<>();
        AtomicReference<Long> tenantInside = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            principal.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            tenantInside.set(TenantContext.getTenantId());
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(principal.get()).isInstanceOf(SaasPrincipal.class);
        SaasPrincipal authenticated = (SaasPrincipal) principal.get();
        assertThat(authenticated.permissions()).contains("tenant:routing:write");
        assertThat(tenantInside.get()).isEqualTo(42L);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void rejectsStillValidJwtAfterUserIsDisabled() throws Exception {
        Claims claims = mock(Claims.class);
        when(tokens.validateToken("jwt")).thenReturn(true);
        when(tokens.parseToken("jwt")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("role", String.class)).thenReturn("tenant_admin");
        when(claims.get("tenant_id")).thenReturn(42);
        when(users.selectById(7L)).thenReturn(user(7L, 42L, "tenant_admin", 0));

        AtomicReference<Object> authentication = new AtomicReference<>();
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(),
                (req, res) -> authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authentication.get()).isNull();
    }

    @Test
    void rejectsJwtWhenDatabaseRoleNoLongerMatchesClaim() throws Exception {
        Claims claims = mock(Claims.class);
        when(tokens.validateToken("jwt")).thenReturn(true);
        when(tokens.parseToken("jwt")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("role", String.class)).thenReturn("tenant_admin");
        when(claims.get("tenant_id")).thenReturn(42);
        when(users.selectById(7L)).thenReturn(user(7L, 42L, "user", 1));

        AtomicReference<Object> authentication = new AtomicReference<>();
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(),
                (req, res) -> authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authentication.get()).isNull();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt");
        return request;
    }

    private SysUserDO user(Long id, Long tenantId, String role, int status) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername("demo_admin");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private TenantDO tenant(Long id, int status) {
        TenantDO tenant = new TenantDO();
        tenant.setId(id);
        tenant.setStatus(status);
        return tenant;
    }
}
