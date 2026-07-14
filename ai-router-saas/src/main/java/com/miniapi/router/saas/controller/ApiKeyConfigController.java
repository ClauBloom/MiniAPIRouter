package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.request.ApiKeyConfigRequest;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.service.ApiKeyConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API Key 配置管理控制器。
 * 
 * <p>提供租户级别的上游 API Key 配置管理接口，包括：
 * <ul>
 *   <li>创建 API Key 配置</li>
 *   <li>分页查询 API Key 列表（支持按供应商、状态、健康状态筛选）</li>
 *   <li>更新 API Key 配置</li>
 *   <li>删除 API Key 配置</li>
 *   <li>启用/禁用 API Key</li>
 *   <li>执行 API Key 健康检查</li>
 * </ul>
 * 所有操作基于当前登录用户的租户上下文进行隔离。
 */
@RestController
@RequestMapping("/api/v1/tenant/api-keys")
public class ApiKeyConfigController {

    private final ApiKeyConfigService apiService; // API Key 配置服务

    /**
     * 构造函数注入 API Key 配置服务。
     *
     * @param apiService API Key 配置服务
     */
    public ApiKeyConfigController(ApiKeyConfigService apiService) {
        this.apiService = apiService;
    }

    /**
     * 创建新的 API Key 配置。
     *
     * @param req API Key 配置请求体（经过参数校验）
     * @return 包含创建结果的统一响应
     */
    @PostMapping
    public ApiResponse<Object> create(@Valid @RequestBody ApiKeyConfigRequest req) {
        return ApiResponse.success(apiService.create(req));
    }

    /**
     * 分页查询 API Key 配置列表。
     * <p>支持按供应商、启用状态、健康状态进行筛选。
     *
     * @param page          页码（默认1）
     * @param page_size     每页条数（默认20）
     * @param provider      供应商筛选（可选）
     * @param status        启用状态筛选（可选）
     * @param health_status 健康状态筛选（可选）
     * @return 分页查询结果
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String health_status) {
        return ApiResponse.success(apiService.list(page, page_size, provider, status, health_status));
    }

    /**
     * 更新指定 ID 的 API Key 配置。
     *
     * @param id  API Key 配置 ID
     * @param req 更新请求体
     * @return 包含更新结果的统一响应
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody ApiKeyConfigRequest req) {
        return ApiResponse.success(apiService.update(id, req));
    }

    /**
     * 删除指定 ID 的 API Key 配置。
     *
     * @param id API Key 配置 ID
     * @return 空数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        apiService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 更新 API Key 的启用/禁用状态。
     *
     * @param id   API Key 配置 ID
     * @param body 请求体，包含 enabled 字段
     * @return 空数据的成功响应
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Object> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        apiService.updateStatus(id, Boolean.TRUE.equals(body.get("enabled")));
        return ApiResponse.success();
    }

    /**
     * 对指定 API Key 执行健康检查。
     * <p>向上游服务发送测试请求，检测 API Key 是否可用。
     *
     * @param id API Key 配置 ID
     * @return 包含健康检查结果的统一响应
     */
    @PostMapping("/{id}/health-check")
    public ApiResponse<Object> healthCheck(@PathVariable Long id) {
        return ApiResponse.success(apiService.healthCheck(id));
    }
}
