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
        Long tenantId = TenantContext.getTenantId();
        SysUserDO user = new SysUserDO();
        user.setTenantId(tenantId);
        user.setUsername((String) body.get("username"));
        // 密码加密存储
        user.setPassword(passwordEncoder.encode((String) body.get("password")));
        user.setNickname((String) body.get("nickname"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        // 默认角色为普通用户
        user.setRole(body.get("role") != null ? (String) body.get("role") : "user");
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
        SysUserDO user = userMapper.selectById(id);
        if (user == null) throw new RouterException("RESOURCE_NOT_FOUND", "用户不存在", 404);
        // 逐字段条件更新
        if (body.get("nickname") != null) user.setNickname((String) body.get("nickname"));
        if (body.get("email") != null) user.setEmail((String) body.get("email"));
        if (body.get("phone") != null) user.setPhone((String) body.get("phone"));
        if (body.get("role") != null) user.setRole((String) body.get("role"));
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
        userMapper.deleteById(id);
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
