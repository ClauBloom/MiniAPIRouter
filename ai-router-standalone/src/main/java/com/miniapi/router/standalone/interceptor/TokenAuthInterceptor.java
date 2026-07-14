package com.miniapi.router.standalone.interceptor;

import com.miniapi.router.core.config.CoreProperties;
import com.miniapi.router.core.exception.RouterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token 认证拦截器。
 * <p>
 * 对需要认证的 API 请求进行 Token 验证。
 * 支持 Authorization: Bearer xxx 和 x-api-key 两种认证方式。
 * 认证失败时抛出 RouterException（401 未授权）。
 * </p>
 */
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    private final String authToken; // 系统配置的认证 Token

    public TokenAuthInterceptor(CoreProperties properties) {
        this.authToken = properties.getAuthToken();
    }

    /**
     * 请求预处理：验证 Token。
     * 优先检查 Authorization 头中的 Bearer Token，其次检查 x-api-key 头。
     * 如果 Token 匹配则放行，否则抛出未授权异常。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  处理器对象
     * @return true 表示放行，false 表示拦截（此处通过抛异常拦截）
     * @throws RouterException 认证失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 检查 Authorization: Bearer xxx 头
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7); // 去掉 "Bearer " 前缀
            if (authToken.equals(token)) return true;
        }
        // 检查 x-api-key 头
        String xApiKey = request.getHeader("x-api-key");
        if (authToken.equals(xApiKey)) return true;
        // 认证失败，抛出未授权异常
        throw new RouterException("UNAUTHORIZED", "Invalid or missing token", 401);
    }
}
