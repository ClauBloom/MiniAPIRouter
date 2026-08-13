package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant/members")
@PreAuthorize("hasAuthority('tenant:member:manage')")
public class MemberController {
    private final UserService userService;

    public MemberController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.list(page, page_size, keyword));
    }

    @PostMapping
    public ApiResponse<Object> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(userService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(userService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success();
    }
}
