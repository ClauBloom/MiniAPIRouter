package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.SystemHealthService;
import com.miniapi.router.saas.service.SystemSettingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/system")
public class AdminSystemController {
    private final SystemHealthService healthService;
    private final SystemSettingService settingService;

    public AdminSystemController(SystemHealthService healthService, SystemSettingService settingService) {
        this.healthService = healthService;
        this.settingService = settingService;
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('platform:system:read')")
    public ApiResponse<Object> health() {
        return ApiResponse.success(healthService.health());
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('platform:system:read')")
    public ApiResponse<Object> metrics() {
        return ApiResponse.success(healthService.metrics());
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('platform:system:read')")
    public ApiResponse<Object> config() {
        return ApiResponse.success(settingService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('platform:system:write')")
    public ApiResponse<Object> updateConfig(@RequestBody Map<String, Object> input) {
        return ApiResponse.success(settingService.updateConfig(input));
    }
}
