package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户服务
 * <p>
 * 提供系统用户的管理功能，包括分页查询、创建、更新和删除。
 * 超级管理员可以查看所有租户的用户，普通管理员只能查看本租户的用户。
 * </p>
 */
@Service
public class UserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("user", "tenant_admin", "super_admin");

    private final SysUserMapper userMapper;            // 用户 Mapper，用于数据访问
    private final PasswordEncoder passwordEncoder;     // 密码编码器，用于密码加密

    public UserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表
     * <p>
     * 超级管理员可查看所有租户的用户，其他角色仅能查看本租户的用户。
     * 支持按用户名关键词模糊搜索。
     * </p>
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keyword  用户名搜索关键词（可选）
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize, String keyword) {
        assertCanManageUsers();
        Long tenantId = TenantContext.getTenantId();
        String role = TenantContext.getRole();
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        // 非超级管理员仅能查看本租户用户
        if (!"super_admin".equals(role)) {
            wrapper.eq(SysUserDO::getTenantId, tenantId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysUserDO::getUsername, keyword);
        }
        wrapper.orderByDesc(SysUserDO::getCreatedAt);

        Page<SysUserDO> p = new Page<>(page, pageSize);
        Page<SysUserDO> result = userMapper.selectPage(p, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    /**
     * 创建用户
     *
     * @param body 用户信息请求体，包含用户名、密码、昵称、邮箱、电话、角色等
     * @return 创建后的用户信息
     */
    public Map<String, Object> create(Map<String, Object> body) {
        assertCanManageUsers();
        Long tenantId = TenantContext.getTenantId();
        String username = requireText(body, "username");
        String password = requireText(body, "password");
        String role = body.get("role") != null ? (String) body.get("role") : "user";
        validateAssignableRole(role, tenantId);

        SysUserDO user = new SysUserDO();
        user.setTenantId(tenantId);
        user.setUsername(username);
        // 密码加密存储
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname((String) body.get("nickname"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        // 默认角色为普通用户
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return toResponse(user);
    }

    /**
     * 更新用户信息
     * <p>
     * 仅更新请求中非空的字段，支持部分更新。
     * </p>
     *
     * @param id  用户ID
     * @param body 更新请求体
     * @return 更新后的用户信息
     * @throws RouterException 当用户不存在时抛出 404
     */
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        SysUserDO user = requireManageable(id);
        String requestedRole = body.get("role") != null ? (String) body.get("role") : null;
        if (requestedRole != null) validateAssignableRole(requestedRole, user.getTenantId());
        // 逐字段条件更新
        if (body.get("nickname") != null) user.setNickname((String) body.get("nickname"));
        if (body.get("email") != null) user.setEmail((String) body.get("email"));
        if (body.get("phone") != null) user.setPhone((String) body.get("phone"));
        if (requestedRole != null) user.setRole(requestedRole);
        if (body.get("status") != null) user.setStatus(((Number) body.get("status")).intValue());
        // 若提供了密码则加密后更新
        if (body.get("password") != null) user.setPassword(passwordEncoder.encode((String) body.get("password")));
        userMapper.updateById(user);
        return toResponse(user);
    }

    /**
     * 删除用户
     *
     * @param id 用户ID
     */
    public void delete(Long id) {
        requireManageable(id);
        userMapper.deleteById(id);
    }

    /**
     * 校验当前调用者具备用户管理权限。
     */
    private void assertCanManageUsers() {
        String role = TenantContext.getRole();
        if (!"super_admin".equals(role) && !"tenant_admin".equals(role)) {
            throw new RouterException("FORBIDDEN", "无用户管理权限", 403);
        }
    }

    /**
     * 查询目标用户并校验当前管理员是否有权管理。
     * 跨租户资源统一返回 404，避免泄露用户是否存在。
     */
    private SysUserDO requireManageable(Long id) {
        assertCanManageUsers();
        SysUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new RouterException("RESOURCE_NOT_FOUND", "用户不存在", 404);
        }
        if (!"super_admin".equals(TenantContext.getRole())
                && !Objects.equals(user.getTenantId(), TenantContext.getTenantId())) {
            throw new RouterException("RESOURCE_NOT_FOUND", "用户不存在", 404);
        }
        if ("super_admin".equals(user.getRole()) && !"super_admin".equals(TenantContext.getRole())) {
            throw new RouterException("FORBIDDEN", "无权管理超级管理员", 403);
        }
        return user;
    }

    /**
     * 校验目标角色有效，并阻止租户管理员授予平台超级管理员权限。
     */
    private void validateAssignableRole(String role, Long targetTenantId) {
        if (!ALLOWED_ROLES.contains(role)) {
            throw new RouterException("INVALID_ROLE", "无效的用户角色", 400);
        }
        if ("super_admin".equals(role) && !"super_admin".equals(TenantContext.getRole())) {
            throw new RouterException("FORBIDDEN", "无权授予超级管理员角色", 403);
        }
        if ("super_admin".equals(role) && !Objects.equals(targetTenantId, 0L)) {
            throw new RouterException("INVALID_ROLE", "超级管理员只能属于平台租户", 400);
        }
    }

    /**
     * 获取必填文本字段并拒绝空值。
     */
    private String requireText(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new RouterException("INVALID_REQUEST", field + " 不能为空", 400);
        }
        return text;
    }

    /**
     * 将用户 DO 对象转换为响应 Map
     * <p>
     * 不包含密码字段，确保敏感信息不泄露。
     * </p>
     *
     * @param user 用户 DO 对象
     * @return 响应 Map
     */
    private Map<String, Object> toResponse(SysUserDO user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("tenant_id", user.getTenantId());
        m.put("username", user.getUsername());
        m.put("nickname", user.getNickname());
        m.put("email", user.getEmail());
        m.put("phone", user.getPhone());
        m.put("role", user.getRole());
        m.put("status", user.getStatus());
        m.put("last_login_at", user.getLastLoginAt());
        m.put("created_at", user.getCreatedAt());
        return m;
    }
}
