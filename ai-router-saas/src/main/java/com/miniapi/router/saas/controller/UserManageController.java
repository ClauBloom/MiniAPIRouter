package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器。
 * 
 * <p>提供平台级别的用户管理接口，供管理员进行用户账户的增删改查操作，包括：
 * <ul>
 *   <li>分页查询用户列表（支持关键词搜索）</li>
 *   <li>创建用户</li>
 *   <li>更新用户信息</li>
 *   <li>删除用户</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManageController {

    private final UserService userService; // 用户管理服务

    /**
     * 构造函数注入用户管理服务。
     *
     * @param userService 用户管理服务
     */
    public UserManageController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表。
     * <p>支持按关键词搜索用户名、昵称等信息。
     *
     * @param page      页码（默认1）
     * @param page_size 每页条数（默认20）
     * @param keyword   搜索关键词（可选）
     * @return 分页查询结果
     */
    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.list(page, page_size, keyword));
    }

    /**
     * 创建新用户。
     *
     * @param body 用户创建请求体（包含用户名、密码、角色等信息）
     * @return 包含创建结果的统一响应
     */
    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(userService.create(body));
    }

    /**
     * 更新指定 ID 的用户信息。
     *
     * @param id   用户 ID
     * @param body 更新请求体
     * @return 包含更新结果的统一响应
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(userService.update(id, body));
    }

    /**
     * 删除指定 ID 的用户。
     *
     * @param id 用户 ID
     * @return 空数据的成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success();
    }
}
