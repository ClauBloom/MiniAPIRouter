package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.dto.request.TenantCreateRequest;
import com.miniapi.router.saas.service.TenantService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租户管理控制器。
 * 
 * <p>提供平台级别的租户管理接口，仅供超级管理员（super_admin）访问，包括：
 * <ul>
 *   <li>创建租户</li>
 *   <li>分页查询租户列表（支持按关键词、状态、套餐筛选）</li>
 *   <li>更新租户信息</li>
 *   <li>删除租户</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class TenantManageController {

    private final TenantService tenantService; // 租户管理服务

    /**
     * 构造函数注入租户管理服务。
     *
     * @param tenantService 租户管理服务
     */
    public TenantManageController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * 创建新租户。
     *
     * @param req 租户创建请求体
     * @return 包含创建结果的统一响应
     */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody TenantCreateRequest req) {
        return ApiResponse.success(tenantService.create(req));
    }

    /**
     * 分页查询租户列表。
     * <p>支持按关键词、状态、套餐类型进行筛选。
     *
     * @param page      页码（默认1）
     * @param page_size 每页条数（默认20）
     * @param keyword   搜索关键词（可选，匹配租户编码或名称）
     * @param status    租户状态筛选（可选）
     * @param plan      套餐类型筛选（可选）
     * @return 分页查询结果
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String plan) {
        return ApiResponse.success(tenantService.list(page, page_size, keyword, status, plan));
    }

    /**
     * 更新指定 ID 的租户信息。
     *
     * @param id  租户 ID
     * @param req 更新请求体
     * @return 包含更新结果的统一响应
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody TenantCreateRequest req) {
        return ApiResponse.success(tenantService.update(id, req));
    }

    /**
     * 删除指定 ID 的租户。
     *
     * @param id 租户 ID
     * @return 空数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return ApiResponse.success();
    }
}
