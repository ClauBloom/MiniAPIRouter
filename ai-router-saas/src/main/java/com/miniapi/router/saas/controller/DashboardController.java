package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.DashboardService;
import org.springframework.web.bind.annotation.*;

/**
 * 仪表盘控制器。
 * 
 * <p>提供租户级别的仪表盘数据汇总接口，包括请求量、Token 消耗、
 * 延迟统计等关键指标的统计与趋势数据。
 */
@RestController
@RequestMapping("/api/v1/tenant/dashboard")
public class DashboardController {

    private final DashboardService dashboardService; // 仪表盘数据服务

    /**
     * 构造函数注入仪表盘数据服务。
     *
     * @param dashboardService 仪表盘数据服务
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取仪表盘汇总数据。
     * <p>根据时间范围和时间间隔参数，返回请求量、Token 消耗、延迟等统计指标。
     *
     * @param start_time 统计开始时间（可选，ISO 格式字符串）
     * @param end_time   统计结束时间（可选，ISO 格式字符串）
     * @param interval   时间间隔粒度（可选，如 hour/day）
     * @return 包含汇总统计数据的统一响应
     */
    @GetMapping("/summary")
    public ApiResponse<Object> summary(
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time,
            @RequestParam(required = false) String interval) {
        return ApiResponse.success(dashboardService.summary(start_time, end_time, interval));
    }
}
