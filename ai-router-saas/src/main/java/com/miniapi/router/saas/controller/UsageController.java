package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.UsageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant/usage")
public class UsageController {
    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('tenant:usage:read')")
    public ApiResponse<Object> summary(@RequestParam(required = false) String start_time,
                                       @RequestParam(required = false) String end_time) {
        return ApiResponse.success(service.summary(start_time, end_time));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('tenant:usage:read')")
    public ApiResponse<Object> trend(@RequestParam(required = false) String start_time,
                                     @RequestParam(required = false) String end_time) {
        return ApiResponse.success(service.trend(start_time, end_time));
    }

    @GetMapping("/by-model")
    @PreAuthorize("hasAuthority('tenant:usage:read')")
    public ApiResponse<Object> byModel(@RequestParam(required = false) String start_time,
                                       @RequestParam(required = false) String end_time) {
        return ApiResponse.success(service.byModel(start_time, end_time));
    }

    @GetMapping("/by-user")
    @PreAuthorize("hasAuthority('tenant:usage:read')")
    public ApiResponse<Object> byUser(@RequestParam(required = false) String start_time,
                                      @RequestParam(required = false) String end_time) {
        return ApiResponse.success(service.byUser(start_time, end_time));
    }

    @RestController
    @RequestMapping("/api/v1/admin/usage")
    public static class AdminUsageController {
        private final UsageService service;

        public AdminUsageController(UsageService service) {
            this.service = service;
        }

        @GetMapping("/summary")
        @PreAuthorize("hasAuthority('platform:system:read')")
        public ApiResponse<Object> summary(@RequestParam(required = false) String start_time,
                                           @RequestParam(required = false) String end_time) {
            return ApiResponse.success(service.adminSummary(start_time, end_time));
        }
    }
}
