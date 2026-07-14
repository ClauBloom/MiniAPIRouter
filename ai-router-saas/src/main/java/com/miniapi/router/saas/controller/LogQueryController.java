package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.service.LogQueryService;
import org.springframework.web.bind.annotation.*;

/**
 * 日志查询控制器。
 * 
 * <p>提供租户级别的请求日志查询接口，支持多维度筛选和详情查看。
 * 可按时间范围、模型、供应商、状态、关键词、追踪ID等条件进行查询。
 */
@RestController
@RequestMapping("/api/v1/tenant/logs")
public class LogQueryController {

    private final LogQueryService logQueryService; // 日志查询服务

    /**
     * 构造函数注入日志查询服务。
     *
     * @param logQueryService 日志查询服务
     */
    public LogQueryController(LogQueryService logQueryService) {
        this.logQueryService = logQueryService;
    }

    /**
     * 分页查询请求日志列表。
     * <p>支持按时间范围、模型、供应商、状态、关键词、追踪ID等条件进行筛选。
     *
     * @param page      页码（默认1）
     * @param page_size 每页条数（默认20）
     * @param start_time 查询开始时间（可选）
     * @param end_time   查询结束时间（可选）
     * @param model      模型名称筛选（可选）
     * @param provider   供应商筛选（可选）
     * @param status     请求状态筛选（可选）
     * @param keyword    关键词搜索（可选）
     * @param trace_id   追踪ID精确查询（可选）
     * @return 分页查询结果
     */
    @GetMapping
    public ApiResponse<Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String start_time,
            @RequestParam(required = false) String end_time,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String trace_id) {
        return ApiResponse.success(logQueryService.search(page, page_size, start_time, end_time,
                model, provider, status, keyword, trace_id));
    }

    /**
     * 获取指定 ID 的请求日志详情。
     *
     * @param id 日志记录 ID
     * @return 包含日志详情的统一响应
     */
    @GetMapping("/{id}")
    public ApiResponse<Object> detail(@PathVariable Long id) {
        return ApiResponse.success(logQueryService.getDetail(id));
    }
}
