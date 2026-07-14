package com.miniapi.router.saas.security;

import com.miniapi.router.saas.context.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import com.miniapi.router.saas.mapper.TenantMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.TraceUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * API Key 认证服务
 * <p>
 * 负责对通过 API Key 方式访问的请求进行认证。
 * API Key 格式为 {@code sk-miniapi-{tenantCode}-{randomPart}}，
 * 系统通过解析租户编码和随机部分来验证 API Key 的有效性。
 * </p>
 * <p>
 * 认证流程：
 * <ol>
 *   <li>从请求头提取 API Key</li>
 *   <li>解析 API Key 格式，提取租户编码和随机部分</li>
 *   <li>通过 Redis 缓存或数据库查询验证租户存在且启用</li>
 *   <li>通过 Redis 验证 API Key 的随机部分是否已注册</li>
 * </ol>
 * </p>
 */
@Component
public class ApiKeyAuthService {

    private final TenantMapper tenantMapper;        // 租户 Mapper，用于查询租户信息
    private final StringRedisTemplate redis;         // Redis 模板，用于缓存和 Key 验证
    private final CryptoUtils cryptoUtils;           // 加密工具类

    public ApiKeyAuthService(TenantMapper tenantMapper, StringRedisTemplate redis, CryptoUtils cryptoUtils) {
        this.tenantMapper = tenantMapper;
        this.redis = redis;
        this.cryptoUtils = cryptoUtils;
    }

    /**
     * 认证 API Key
     * <p>
     * 验证 API Key 的格式和有效性，返回认证结果。
     * </p>
     *
     * @param apiKey 待认证的 API Key 字符串
     * @return 认证结果，包含成功/失败标志、租户ID和错误信息
     */
    public AuthResult authenticate(String apiKey) {
        // 校验 API Key 格式，必须以 "sk-miniapi-" 开头
        if (apiKey == null || !apiKey.startsWith("sk-miniapi-")) {
            return AuthResult.fail("INVALID_API_KEY", "Invalid API key format");
        }
        // 解析 API Key：去掉前缀后按 "-" 分割为租户编码和随机部分
        String[] parts = apiKey.substring("sk-miniapi-".length()).split("-", 2);
        if (parts.length < 2) {
            return AuthResult.fail("INVALID_API_KEY", "Invalid API key format");
        }
        String tenantCode = parts[0];
        String randomPart = parts[1];

        // 从 Redis 缓存中查询租户ID
        String cacheKey = "apikey:tenant:" + tenantCode;
        String cached = redis.opsForValue().get(cacheKey);
        Long tenantId;
        if (cached != null) {
            // 缓存命中，直接解析租户ID
            tenantId = Long.parseLong(cached);
        } else {
            // 缓存未命中，查询数据库获取租户信息
            LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TenantDO::getTenantCode, tenantCode);
            TenantDO tenant = tenantMapper.selectOne(wrapper);
            if (tenant == null) {
                return AuthResult.fail("INVALID_API_KEY", "Tenant not found: " + tenantCode);
            }
            // 检查租户是否被禁用
            if (tenant.getStatus() != null && tenant.getStatus() == 0) {
                return AuthResult.fail("TENANT_DISABLED", "Tenant is disabled");
            }
            tenantId = tenant.getId();
            // 将租户ID缓存到 Redis，有效期 5 分钟
            redis.opsForValue().set(cacheKey, String.valueOf(tenantId), 5, TimeUnit.MINUTES);
        }

        // 验证 API Key 的随机部分是否在 Redis 中注册
        String proxyKey = "proxykey:" + tenantCode + ":" + randomPart;
        if (Boolean.FALSE.equals(redis.hasKey(proxyKey))) {
            return AuthResult.fail("INVALID_API_KEY", "API key not recognized");
        }
        return AuthResult.success(tenantId, apiKey);
    }

    /**
     * 从 HTTP 请求中提取 API Key
     * <p>
     * 支持两种方式提取 API Key：
     * <ul>
     *   <li>Authorization 请求头：Bearer sk-miniapi-...</li>
     *   <li>x-api-key 请求头：sk-miniapi-...</li>
     * </ul>
     * </p>
     *
     * @param request HTTP 请求对象
     * @return 提取到的 API Key，若不存在则返回 null
     */
    public String extractApiKey(HttpServletRequest request) {
        // 优先从 Authorization 头提取
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer sk-miniapi")) {
            return auth.substring(7);
        }
        // 其次从 x-api-key 头提取
        String xApiKey = request.getHeader("x-api-key");
        if (xApiKey != null && xApiKey.startsWith("sk-miniapi")) {
            return xApiKey;
        }
        return null;
    }

    /**
     * 认证结果记录
     * <p>
     * 封装 API Key 认证的结果，包含成功/失败标志、租户ID、API Key 和错误信息。
     * </p>
     *
     * @param success      是否认证成功
     * @param tenantId     租户ID（成功时有效）
     * @param apiKey       API Key（成功时有效）
     * @param errorCode    错误码（失败时有效）
     * @param errorMessage 错误消息（失败时有效）
     */
    public record AuthResult(boolean success, Long tenantId, String apiKey, String errorCode, String errorMessage) {
        // 构建成功结果
        public static AuthResult success(Long tenantId, String apiKey) {
            return new AuthResult(true, tenantId, apiKey, null, null);
        }
        // 构建失败结果
        public static AuthResult fail(String code, String msg) {
            return new AuthResult(false, null, null, code, msg);
        }
    }
}
