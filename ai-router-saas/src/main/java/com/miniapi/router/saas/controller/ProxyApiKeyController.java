package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 代理 API Key 管理控制器。
 * 
 * <p>提供租户级别的代理 API Key（用于调用代理接口的密钥）的生成和查看功能。
 * 生成的 API Key 存储在 Redis 中，有效期为 365 天，格式为：
 * <code>sk-miniapi-{租户编码}-{随机十六进制字符串}</code>
 */
@RestController
@RequestMapping("/api/v1/tenant/proxy-keys")
public class ProxyApiKeyController {

    private final TenantMapper tenantMapper;      // 租户 Mapper，用于查询租户信息
    private final StringRedisTemplate redis;      // Redis 操作模板，用于存储代理 API Key
    private final SecureRandom random = new SecureRandom(); // 安全随机数生成器，用于生成随机密钥

    /**
     * 构造函数注入租户 Mapper 和 Redis 模板。
     *
     * @param tenantMapper 租户 Mapper
     * @param redis        Redis 操作模板
     */
    public ProxyApiKeyController(TenantMapper tenantMapper, StringRedisTemplate redis) {
        this.tenantMapper = tenantMapper;
        this.redis = redis;
    }

    /**
     * 生成新的代理 API Key。
     * <p>生成流程：
     * <ol>
     *   <li>从租户上下文获取当前租户信息</li>
     *   <li>生成 16 字节随机数，转换为 32 位十六进制字符串</li>
     *   <li>拼接租户编码和随机部分，生成完整的 API Key</li>
     *   <li>将 Key-Value 存入 Redis，有效期为 365 天</li>
     * </ol>
     *
     * @return 包含新生成 API Key 信息的统一响应
     */
    @PostMapping
    public ApiResponse<Object> generate() {
        // 从租户上下文获取当前租户ID
        Long tenantId = TenantContext.getTenantId();
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            return ApiResponse.error(404, "Tenant not found");
        }
        // 生成 16 字节随机数并转换为十六进制字符串
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String randomPart = HexFormat.of().formatHex(bytes);
        // 拼接完整的 API Key：sk-miniapi-{租户编码}-{随机部分}
        String apiKey = "sk-miniapi-" + tenant.getTenantCode() + "-" + randomPart;
        // 将代理 Key 存入 Redis，Key 格式为 proxykey:{租户编码}:{随机部分}，值为租户ID
        redis.opsForValue().set("proxykey:" + tenant.getTenantCode() + ":" + randomPart,
                String.valueOf(tenantId), 365, TimeUnit.DAYS);

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("api_key", apiKey);
        result.put("tenant_code", tenant.getTenantCode());
        result.put("created_at", java.time.LocalDateTime.now());
        return ApiResponse.success(result);
    }

    /**
     * 查询当前租户的所有代理 API Key 列表。
     * <p>从 Redis 中匹配该租户的所有代理 Key，返回完整 Key 和脱敏 Key。
     * 脱敏格式仅显示随机部分的最后 4 位字符。
     *
     * @return 包含 API Key 列表的统一响应
     */
    @GetMapping
    public ApiResponse<Object> list() {
        Long tenantId = TenantContext.getTenantId();
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) return ApiResponse.error(404, "Tenant not found");
        // 使用通配符匹配该租户在 Redis 中的所有代理 Key
        java.util.Set<String> keys = redis.keys("proxykey:" + tenant.getTenantCode() + ":*");
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        if (keys != null) {
            for (String k : keys) {
                // 从 Redis Key 中提取随机部分
                String randomPart = k.substring(k.lastIndexOf(":") + 1);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("api_key", "sk-miniapi-" + tenant.getTenantCode() + "-" + randomPart);
                // 生成脱敏的 API Key，仅显示最后 4 位
                m.put("api_key_masked", "sk-miniapi-" + tenant.getTenantCode() + "-..." + randomPart.substring(randomPart.length() - 4));
                list.add(m);
            }
        }
        return ApiResponse.success(list);
    }
}
