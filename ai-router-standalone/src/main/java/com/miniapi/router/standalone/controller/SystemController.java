package com.miniapi.router.standalone.controller;

import com.miniapi.router.core.config.CoreProperties;
import com.miniapi.router.standalone.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统信息控制器。
 * <p>
 * 提供系统健康检查、版本信息和系统信息查询等公开接口，
 * 这些接口不需要 Token 认证（在 WebMvcConfig 中被排除）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final CoreProperties properties; // 核心配置属性

    public SystemController(CoreProperties properties) {
        this.properties = properties;
    }

    /**
     * 健康检查接口。
     * 返回服务状态、版本号和运行模式。
     *
     * @return 健康检查信息
     */
    @GetMapping("/health")
    public ApiResponse<Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("version", "1.0.0");
        m.put("mode", "standalone");
        return ApiResponse.success(m);
    }

    /**
     * 版本信息接口。
     * 返回版本号、运行模式和 Java 版本。
     *
     * @return 版本信息
     */
    @GetMapping("/version")
    public ApiResponse<Object> version() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "1.0.0");
        m.put("mode", "standalone");
        m.put("java_version", System.getProperty("java.version"));
        return ApiResponse.success(m);
    }

    /**
     * 系统信息接口。
     * 返回版本号、运行模式和认证 Token。
     *
     * @return 系统信息
     */
    @GetMapping("/info")
    public ApiResponse<Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", "1.0.0");
        m.put("mode", "standalone");
        m.put("auth_token", properties.getAuthToken());
        return ApiResponse.success(m);
    }
}
