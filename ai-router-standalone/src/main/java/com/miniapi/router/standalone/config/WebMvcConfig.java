package com.miniapi.router.standalone.config;

import com.miniapi.router.standalone.interceptor.TokenAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类。
 * <p>
 * 配置 Token 认证拦截器和 CORS 跨域策略。
 * 拦截 /api/** 和 /v1/** 路径，排除健康检查、版本信息等公开接口。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenAuthInterceptor tokenAuthInterceptor; // Token 认证拦截器

    public WebMvcConfig(TokenAuthInterceptor tokenAuthInterceptor) {
        this.tokenAuthInterceptor = tokenAuthInterceptor;
    }

    /**
     * 注册拦截器。
     * 对 /api/** 和 /v1/** 路径启用 Token 认证，
     * 排除健康检查、版本信息、系统信息等不需要认证的接口。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns("/api/**", "/v1/**")
                .excludePathPatterns("/api/v1/system/health", "/api/v1/system/version", "/api/v1/system/info");
    }

    /**
     * 配置 CORS 跨域策略。
     * 允许所有来源、常用 HTTP 方法和所有请求头，支持凭证传递。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // 预检请求缓存时间 1 小时
    }
}
