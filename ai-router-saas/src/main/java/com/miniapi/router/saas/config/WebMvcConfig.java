package com.miniapi.router.saas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC 配置类。
 * 
 * <p>实现 {@link WebMvcConfigurer} 接口，自定义 Spring MVC 配置。
 * 主要功能是配置跨域（CORS）策略，允许前端应用跨域访问后端 API。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebMvcConfig(@Value("${miniapi.router.cors-allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * 配置跨域请求映射规则。
     * <p>仅当配置了允许来源时启用，支持常见 HTTP 方法，
     * 不允许浏览器凭证（Cookie），预检请求缓存时间为 3600 秒。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) return;
        registry.addMapping("/**")                              // 对所有路径生效
                .allowedOrigins(allowedOrigins)                   // 仅允许显式配置的来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 允许的 HTTP 方法
                .allowedHeaders("*")                             // 允许所有请求头
                .allowCredentials(false)                         // Bearer Token 无需 Cookie 凭证
                .maxAge(3600);                                   // 预检请求缓存时间（秒）
    }
}
