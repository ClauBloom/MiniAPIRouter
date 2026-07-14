package com.miniapi.router.saas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.saas.security.ApiKeyAuthService;
import com.miniapi.router.saas.security.JwtAuthenticationFilter;
import com.miniapi.router.saas.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Spring Security 安全配置类。
 * 
 * <p>配置 Web 安全策略，主要功能包括：
 * <ul>
 *   <li>禁用 CSRF（前后端分离架构无需 CSRF 保护）</li>
 *   <li>使用无状态会话策略（STATELESS），基于 JWT Token 进行认证</li>
 *   <li>定义公开访问路径（登录接口、系统接口、代理接口）与需认证路径</li>
 *   <li>配置未认证时的 401 响应格式</li>
 *   <li>注册 JWT 认证过滤器</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链。
     * <p>定义请求的认证与授权规则，并注册 JWT 过滤器。
     *
     * @param http              HttpSecurity 构建器
     * @param jwtTokenProvider  JWT Token 生成与验证工具
     * @param apiKeyAuthService API Key 认证服务（用于代理请求认证）
     * @return 构建完成的安全过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
                                           ApiKeyAuthService apiKeyAuthService) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 禁用 CSRF 保护，前后端分离架构使用 Token 认证
            // 使用无状态会话策略，不创建 HttpSession，完全依赖 JWT Token
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 登录接口和系统接口允许匿名访问
                .requestMatchers("/api/v1/auth/login", "/api/v1/system/**").permitAll()
                // 代理接口（/v1/**）使用 API Key 认证，不走 Spring Security 认证
                .requestMatchers("/v1/**").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )
            // 配置未认证时的响应处理，返回 JSON 格式的错误信息
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                            "code", 4010,
                            "message", "Unauthorized",
                            "error_code", "UNAUTHORIZED"
                    )));
                })
            )
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 认证过滤器
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
