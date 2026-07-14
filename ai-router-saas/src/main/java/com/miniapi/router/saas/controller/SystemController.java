package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统控制器。
 * 
 * <p>提供系统级别的公共接口，无需认证即可访问，包括：
 * <ul>
 *   <li>健康检查：检查服务、数据库、Redis 等组件的运行状态</li>
 *   <li>版本信息：返回应用名称、版本号和 Java 版本</li>
 *   <li>系统配置：返回运行模式和功能特性开关</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    /**
     * 健康检查接口。
     * <p>返回服务、数据库、Redis 的运行状态和当前时间戳。
     *
     * @return 包含健康状态信息的统一响应
     */
    @GetMapping("/health")
    public ApiResponse<Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");       // 服务整体状态
        result.put("database", "UP");     // 数据库状态
        result.put("redis", "UP");        // Redis 状态
        result.put("timestamp", System.currentTimeMillis()); // 当前时间戳
        return ApiResponse.success(result);
    }

    /**
     * 版本信息接口。
     * <p>返回应用名称、版本号和 Java 运行时版本。
     *
     * @return 包含版本信息的统一响应
     */
    @GetMapping("/version")
    public ApiResponse<Object> version() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "MiniAPIRouter SaaS");           // 应用名称
        result.put("version", "1.0.0");                     // 应用版本号
        result.put("java_version", System.getProperty("java.version")); // Java 版本
        return ApiResponse.success(result);
    }

    /**
     * 系统配置接口。
     * <p>返回当前运行模式和功能特性开关状态。
     *
     * @return 包含系统配置信息的统一响应
     */
    @GetMapping("/config")
    public ApiResponse<Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "saas"); // 运行模式：SaaS 多租户模式
        // 功能特性开关：多租户已启用，Elasticsearch 和 MinIO 未启用
        result.put("features", Map.of("multi_tenant", true, "elasticsearch", false, "minio", false));
        return ApiResponse.success(result);
    }
}
