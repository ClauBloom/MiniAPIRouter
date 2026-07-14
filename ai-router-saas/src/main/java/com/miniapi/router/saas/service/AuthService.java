package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.saas.entity.SysUserDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.SysUserMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import com.miniapi.router.saas.security.JwtTokenProvider;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.util.TraceUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证服务
 * <p>
 * 提供用户登录认证功能，验证用户名密码后生成 JWT Token。
 * 同时检查用户状态和租户状态，确保被禁用的用户或租户无法登录。
 * </p>
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;              // 用户 Mapper，用于查询用户信息
    private final TenantMapper tenantMapper;             // 租户 Mapper，用于查询租户信息
    private final JwtTokenProvider jwtTokenProvider;     // JWT Token 提供者，用于生成 Token
    private final PasswordEncoder passwordEncoder;       // 密码编码器，用于验证密码

    public AuthService(SysUserMapper userMapper, TenantMapper tenantMapper,
                       JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户登录
     * <p>
     * 验证用户名和密码，检查用户和租户状态，成功后生成 JWT Token 并返回用户信息。
     * </p>
     *
     * @param username   用户名
     * @param password   密码
     * @param tenantCode 租户编码
     * @return 登录结果，包含 Token、过期时间和用户信息
     * @throws RouterException 当用户名或密码错误（401）、用户被禁用（403）或租户被禁用（403）时抛出
     */
    public Map<String, Object> login(String username, String password, String tenantCode) {
        // 根据用户名查询用户
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getUsername, username);
        SysUserDO user = userMapper.selectOne(wrapper);

        // 验证用户存在性和密码正确性
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RouterException("UNAUTHORIZED", "用户名或密码错误", 401);
        }
        // 检查用户是否被禁用
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RouterException("FORBIDDEN", "用户已被禁用", 403);
        }

        // 查询租户信息，获取租户名称并检查租户状态
        Long tenantId = user.getTenantId();
        String tenantName = "";
        if (tenantId != null && tenantId > 0) {
            TenantDO tenant = tenantMapper.selectById(tenantId);
            if (tenant != null) {
                tenantName = tenant.getTenantName();
                // 检查租户是否被禁用
                if (tenant.getStatus() != null && tenant.getStatus() == 0) {
                    throw new RouterException("TENANT_DISABLED", "租户已被禁用", 403);
                }
            }
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成 JWT Token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(),
                user.getRole(), tenantId, tenantName);

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expires_in", jwtTokenProvider.getExpirationMs() / 1000);

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("role", user.getRole());
        userInfo.put("tenant_id", tenantId);
        userInfo.put("tenant_name", tenantName);
        result.put("user", userInfo);

        return result;
    }
}
