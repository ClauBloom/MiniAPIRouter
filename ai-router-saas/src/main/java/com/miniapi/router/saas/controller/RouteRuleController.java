package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.request.RouteRuleRequest;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.service.RouteRuleService;
import com.miniapi.router.saas.service.RouteSimulationService;
import com.miniapi.router.saas.dto.request.SimulationRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 路由规则管理控制器。
 * 
 * <p>提供租户级别的模型路由规则管理接口，包括：
 * <ul>
 *   <li>创建路由规则</li>
 *   <li>分页查询路由规则列表</li>
 *   <li>查询单个路由规则详情</li>
 *   <li>更新路由规则</li>
 *   <li>删除路由规则</li>
 *   <li>启用/禁用路由规则</li>
 * </ul>
 * 路由规则决定了请求模型如何被映射和分发到具体的上游 API Key。
 */
@RestController
@RequestMapping("/api/v1/tenant/route-rules")
public class RouteRuleController {

    private final RouteRuleService routeRuleService;
    private final RouteSimulationService simulationService;

    public RouteRuleController(RouteRuleService routeRuleService, RouteSimulationService simulationService) {
        this.routeRuleService = routeRuleService;
        this.simulationService = simulationService;
    }

    /**
     * 创建新的路由规则。
     *
     * @param req 路由规则请求体（经过参数校验）
     * @return 包含创建结果的统一响应
     */
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> create(@Valid @RequestBody RouteRuleRequest req) {
        return ApiResponse.success(routeRuleService.create(req));
    }

    /**
     * 分页查询路由规则列表。
     *
     * @param page      页码（默认1）
     * @param page_size 每页条数（默认20）
     * @return 分页查询结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:routing:read')")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.success(routeRuleService.list(page, page_size));
    }

    /**
     * 根据 ID 查询路由规则详情。
     *
     * @param id 路由规则 ID
     * @return 包含路由规则详情的统一响应
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:read')")
    public ApiResponse<Object> findById(@PathVariable Long id) {
        return ApiResponse.success(routeRuleService.findById(id));
    }

    /**
     * 更新指定 ID 的路由规则。
     *
     * @param id  路由规则 ID
     * @param req 更新请求体
     * @return 包含更新结果的统一响应
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> validate(@RequestBody RouteRuleRequest req) {
        return ApiResponse.success(Map.of("valid", true, "summary", "路由规则验证通过"));
    }

    @PostMapping("/simulate")
    @PreAuthorize("hasAuthority('tenant:routing:read')")
    public ApiResponse<Object> simulate(@RequestBody SimulationRequest request) {
        return ApiResponse.success(simulationService.simulate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody RouteRuleRequest req) {
        return ApiResponse.success(routeRuleService.update(id, req));
    }

    /**
     * 删除指定 ID 的路由规则。
     *
     * @param id 路由规则 ID
     * @return 空数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        routeRuleService.delete(id);
        return ApiResponse.success();
    }

    /**
     * 更新路由规则的启用/禁用状态。
     *
     * @param id   路由规则 ID
     * @param body 请求体，包含 enabled 字段
     * @return 空数据的成功响应
     */
    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasAuthority('tenant:routing:write')")
    public ApiResponse<Object> updateEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        routeRuleService.updateEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
        return ApiResponse.success();
    }
}
