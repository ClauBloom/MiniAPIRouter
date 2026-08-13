package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.request.IntentConfigRequest;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.IntentConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant/intents")
public class IntentConfigController {
    private final IntentConfigService service;

    public IntentConfigController(IntentConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:routing:read')")
    public ApiResponse<Object> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:read')")
    public ApiResponse<Object> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> create(@RequestBody IntentConfigRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody IntentConfigRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
