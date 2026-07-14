package com.miniapi.router.standalone.controller;

import com.miniapi.router.standalone.dto.ApiResponse;
import com.miniapi.router.standalone.service.LogQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志查询控制器。
 * <p>
 * 提供请求日志的搜索、详情查看和仪表盘统计接口。
 * 所有接口路径前缀为 /api/v1/logs，需要 Token 认证。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogQueryService logQueryService; // 日志查询服务

    public LogController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    /**
     * 搜索请求日志。
     * 支持按模型、供应商、状态、追踪 ID、时间范围等条件进行过滤。
     *
     * @param page       页码，默认 1
     * @param page_size  每页条数，默认 20
     * @param model      模型名称（可选）
     * @param provider   供应商名称（可选）
     * @param status     请求状态（可选）
     * @param trace_id   追踪 ID（可选）
     * @param start_time 开始时间（可选）
     * @param end_time   结束时间（可选）
     * @return 日志列表分页数据
     */
    @GetMapping
    public ApiResponse<Object> search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trace_id,
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time) {
        // 构建查询条件 Map
        Map<String, Object> query = new HashMap<>();
        if (model != null) query.put("model", model);
        if (provider != null) query.put("provider", provider);
        if (status != null) query.put("status", status);
        if (trace_id != null) query.put("traceId", trace_id);
        if (start_time != null) query.put("startTime", start_time);
        if (end_time != null) query.put("endTime", end_time);
        return ApiResponse.success(logQueryService.search(query, page, page_size));
    }

    /**
     * 查询指定日志的详情（包含 prompt 和 response 内容）。
     *
     * @param id 日志记录 ID
     * @return 日志详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Object> getDetail(@PathVariable Long id) {
        return ApiResponse.success(logQueryService.getDetail(id));
    }

    /**
     * 获取仪表盘统计数据。
     * 包括总请求数、Token 用量、平均延迟、成功率、模型分布等。
     *
     * @param start_time 开始时间（可选）
     * @param end_time   结束时间（可选）
     * @param interval   统计间隔（可选）
     * @return 仪表盘统计数据
     */
    @GetMapping("/dashboard")
    public ApiResponse<Object> dashboard(
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time,
            @RequestParam(required = false) String interval) {
        return ApiResponse.success(logQueryService.dashboardSummary(start_time, end_time, interval));
    }
}
