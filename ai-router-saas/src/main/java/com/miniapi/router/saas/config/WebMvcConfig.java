package com.miniapi.router.saas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类。
 * 
 * <p>实现 {@link WebMvcConfigurer} 接口，自定义 Spring MVC 配置。
 * 主要功能是配置跨域（CORS）策略，允许前端应用跨域访问后端 API。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置跨域请求映射规则。
     * <p>允许所有路径的跨域请求，支持常见的 HTTP 方法，
     * 允许携带凭证（Cookie），预检请求缓存时间为 3600 秒。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                              // 对所有路径生效
                .allowedOriginPatterns("*")                      // 允许所有来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS") // 允许的 HTTP 方法
                .allowedHeaders("*")                             // 允许所有请求头
                .allowCredentials(true)                          // 允许携带凭证（Cookie）
                .maxAge(3600);                                   // 预检请求缓存时间（秒）
    }
}
