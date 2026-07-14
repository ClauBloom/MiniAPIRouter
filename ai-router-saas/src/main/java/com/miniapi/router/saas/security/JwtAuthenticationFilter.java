package com.miniapi.router.saas.security;

import com.miniapi.router.saas.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 继承 Spring Security 的 {@link OncePerRequestFilter}，确保每个请求只执行一次过滤。
 * 负责从请求头中提取 JWT Token，验证并解析后设置安全上下文和租户上下文。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *   <li>从 Authorization 请求头提取 Bearer Token</li>
 *   <li>验证 Token 有效性</li>
 *   <li>解析 Token 获取用户角色、租户ID、用户ID等信息</li>
 *   <li>设置 Spring Security 上下文和租户上下文</li>
 *   <li>继续过滤链，最后清理租户上下文</li>
 * </ol>
 * </p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;  // JWT Token 提供者，用于验证和解析 Token

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * 执行过滤逻辑
     * <p>
     * 提取并验证 JWT Token，设置安全上下文后继续过滤链。
     * 无论是否认证成功，都会在 finally 块中清理租户上下文，防止线程池复用导致的上下文泄露。
     * </p>
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param chain    过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        // 仅当 Token 存在且有效时才设置认证上下文
        if (token != null && tokenProvider.validateToken(token)) {
            Claims claims = tokenProvider.parseToken(token);
            String role = claims.get("role", String.class);
            Long tenantId = claims.get("tenant_id", Long.class);
            // 处理 tenant_id 可能以非 Long 类型存储的情况
            if (tenantId == null) {
                Object tid = claims.get("tenant_id");
                if (tid instanceof Number n) tenantId = n.longValue();
            }
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);

            // 设置租户上下文，供后续业务逻辑使用
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(userId);
            TenantContext.setRole(role);

            // 构建 Spring Security 认证令牌，设置角色权限
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 清理租户上下文，防止线程池复用导致上下文泄露
            TenantContext.clear();
        }
    }

    /**
     * 从请求头中提取 JWT Token
     * <p>
     * 从 Authorization 请求头中提取 Bearer Token。
     * 排除以 "sk-miniapi" 开头的 Token（这些是 API Key，不是 JWT）。
     * </p>
     *
     * @param request HTTP 请求对象
     * @return JWT Token 字符串，若不存在或为 API Key 则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // 排除 API Key 类型的 Token
            if (!token.startsWith("sk-miniapi")) {
                return token;
            }
        }
        return null;
    }
}
