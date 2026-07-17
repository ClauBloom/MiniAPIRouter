package com.miniapi.router.standalone.controller;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.standalone.dto.ApiResponse;
import com.miniapi.router.standalone.service.ConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 配置管理控制器。
 * <p>
 * 提供 API Key 配置、路由规则、意图配置的 CRUD 接口。
 * 所有接口路径前缀为 /api/v1/config，需要 Token 认证。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService; // 配置管理服务

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    // ===== API Key 配置管理 =====

    /**
     * 分页查询 API Key 列表。
     *
     * @param page      页码，默认 1
     * @param page_size 每页条数，默认 20
     * @return API Key 列表分页数据
     */
    @GetMapping("/api-keys")
    public ApiResponse<Object> listKeys(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.success(configService.listKeys(page, page_size));
    }

    /**
     * 根据 ID 查询单个 API Key 详情。
     *
     * @param id API Key ID
     * @return API Key 详情
     */
    @GetMapping("/api-keys/{id}")
    public ApiResponse<Object> getKey(@PathVariable Long id) {
        return ApiResponse.success(configService.getKey(id));
    }

    /**
     * 创建新的 API Key 配置。
     *
     * @param config API Key 配置信息
     * @return 创建后的 API Key 详情
     */
    @PostMapping("/api-keys")
    public ApiResponse<Object> createKey(@RequestBody ApiKeyConfig config) {
        return ApiResponse.success(configService.createKey(config));
    }

    /**
     * 更新指定 API Key 配置。
     *
     * @param id     API Key ID
     * @param config 更新的配置信息
     * @return 更新后的 API Key 详情
     */
    @PutMapping("/api-keys/{id}")
    public ApiResponse<Object> updateKey(@PathVariable Long id, @RequestBody ApiKeyConfig config) {
        return ApiResponse.success(configService.updateKey(id, config));
    }

    /**
     * 删除指定 API Key。
     *
     * @param id API Key ID
     * @return 空成功响应
     */
    @DeleteMapping("/api-keys/{id}")
    public ApiResponse<Object> deleteKey(@PathVariable Long id) {
        configService.deleteKey(id);
        return ApiResponse.success();
    }

    /**
     * 更新 API Key 的启用/禁用状态。
     *
     * @param id   API Key ID
     * @param body 包含 status 字段的请求体
     * @return 空成功响应
     */
    @PatchMapping("/api-keys/{id}/status")
    public ApiResponse<Object> updateKeyStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        configService.updateKeyStatus(id, Integer.parseInt(body.get("status").toString()));
        return ApiResponse.success();
    }

    /**
     * 对指定 API Key 执行健康检测。
     * 发送测试请求到上游，根据响应判定健康状态。
     *
     * @param id API Key ID
     * @return 检测结果，包含 health_status 和 detail
     */
    @PostMapping("/api-keys/{id}/health-check")
    public ApiResponse<Object> healthCheck(@PathVariable Long id) {
        return ApiResponse.success(configService.healthCheck(id));
    }

    // ===== 路由规则管理 =====

    /**
     * 分页查询路由规则列表。
     *
     * @param page      页码，默认 1
     * @param page_size 每页条数，默认 20
     * @return 路由规则列表分页数据
     */
    @GetMapping("/route-rules")
    public ApiResponse<Object> listRules(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.success(configService.listRules(page, page_size));
    }

    /**
     * 根据 ID 查询单个路由规则详情。
     *
     * @param id 路由规则 ID
     * @return 路由规则详情
     */
    @GetMapping("/route-rules/{id}")
    public ApiResponse<Object> getRule(@PathVariable Long id) {
        return ApiResponse.success(configService.getRule(id));
    }

    /**
     * 创建新的路由规则。
     *
     * @param rule 路由规则信息
     * @return 创建后的路由规则详情
     */
    @PostMapping("/route-rules")
    public ApiResponse<Object> createRule(@RequestBody RouteRule rule) {
        return ApiResponse.success(configService.createRule(rule));
    }

    /**
     * 更新指定路由规则。
     *
     * @param id   路由规则 ID
     * @param rule 更新的规则信息
     * @return 更新后的路由规则详情
     */
    @PutMapping("/route-rules/{id}")
    public ApiResponse<Object> updateRule(@PathVariable Long id, @RequestBody RouteRule rule) {
        return ApiResponse.success(configService.updateRule(id, rule));
    }

    /**
     * 删除指定路由规则。
     *
     * @param id 路由规则 ID
     * @return 空成功响应
     */
    @DeleteMapping("/route-rules/{id}")
    public ApiResponse<Object> deleteRule(@PathVariable Long id) {
        configService.deleteRule(id);
        return ApiResponse.success();
    }

    /**
     * 更新路由规则的启用/禁用状态。
     *
     * @param id   路由规则 ID
     * @param body 包含 enabled 字段的请求体
     * @return 空成功响应
     */
    @PatchMapping("/route-rules/{id}/enabled")
    public ApiResponse<Object> updateRuleEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        configService.updateRuleEnabled(id, Boolean.TRUE.equals(body.get("enabled")));
        return ApiResponse.success();
    }

    // ===== 意图配置管理 =====

    /**
     * 查询所有意图配置列表（按排序顺序排列）。
     *
     * @return 意图配置列表
     */
    @GetMapping("/intents")
    public ApiResponse<Object> listIntents() {
        return ApiResponse.success(configService.listIntents());
    }

    /**
     * 根据 ID 查询单个意图配置详情。
     *
     * @param id 意图配置 ID
     * @return 意图配置详情
     */
    @GetMapping("/intents/{id}")
    public ApiResponse<Object> getIntent(@PathVariable Long id) {
        return ApiResponse.success(configService.getIntent(id));
    }

    /**
     * 创建新的意图配置。
     *
     * @param config 意图配置信息
     * @return 创建后的意图配置详情
     */
    @PostMapping("/intents")
    public ApiResponse<Object> createIntent(@RequestBody IntentConfig config) {
        return ApiResponse.success(configService.createIntent(config));
    }

    /**
     * 更新指定意图配置。
     *
     * @param id     意图配置 ID
     * @param config 更新的配置信息
     * @return 更新后的意图配置详情
     */
    @PutMapping("/intents/{id}")
    public ApiResponse<Object> updateIntent(@PathVariable Long id, @RequestBody IntentConfig config) {
        return ApiResponse.success(configService.updateIntent(id, config));
    }

    /**
     * 删除指定意图配置（默认意图不允许删除）。
     *
     * @param id 意图配置 ID
     * @return 空成功响应
     */
    @DeleteMapping("/intents/{id}")
    public ApiResponse<Object> deleteIntent(@PathVariable Long id) {
        configService.deleteIntent(id);
        return ApiResponse.success();
    }

    /**
     * 重置意图配置为跟随默认模板。
     * 将 customized 置为 0，同步默认意图的目标 Key 和权重配置。
     *
     * @param id 意图配置 ID
     * @return 空成功响应
     */
    @PatchMapping("/intents/{id}/reset-to-default")
    public ApiResponse<Object> resetIntentToDefault(@PathVariable Long id) {
        configService.resetIntentToDefault(id);
        return ApiResponse.success();
    }
}
