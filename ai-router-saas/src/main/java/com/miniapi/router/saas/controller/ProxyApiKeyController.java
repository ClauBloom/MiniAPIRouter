package com.miniapi.router.saas.controller;

import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiResponse;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.saas.security.ProxyKeyUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class ProxyApiKeyController {

    private static final int SCAN_BATCH_SIZE = 500;

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
        LocalDateTime createdAt = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenant_id", tenantId);
        metadata.put("suffix", randomPart.substring(randomPart.length() - 4));
        metadata.put("created_at", createdAt.toString());
        // Redis Key 仅包含随机部分摘要；完整代理 Key 只在本次响应中返回。
        redis.opsForValue().set(ProxyKeyUtils.redisKey(tenant.getTenantCode(), randomPart),
                JsonUtils.toJson(metadata), 365, TimeUnit.DAYS);

        // 构建返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("api_key", apiKey);
        result.put("tenant_code", tenant.getTenantCode());
        result.put("created_at", createdAt);
        return ApiResponse.success(result);
    }

    /**
     * 查询当前租户的所有代理 API Key 列表。
     * <p>从 Redis 中匹配该租户的代理 Key，仅返回脱敏 Key。
     * 完整 Key 只在创建时展示一次。
     *
     * @return 包含 API Key 列表的统一响应
     */
    @GetMapping
    public ApiResponse<Object> list() {
        Long tenantId = TenantContext.getTenantId();
        TenantDO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) return ApiResponse.error(404, "Tenant not found");
        List<Map<String, Object>> list = new ArrayList<>();

        List<String> v2Keys = scanKeys("proxykey:v2:" + tenant.getTenantCode() + ":*");
        if (!v2Keys.isEmpty()) {
            List<String> values = redis.opsForValue().multiGet(v2Keys);
            if (values != null) {
                for (String value : values) {
                    if (value == null) continue;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> metadata = JsonUtils.fromJson(value, Map.class);
                        Object suffixValue = metadata.get("suffix");
                        if (!(suffixValue instanceof String suffix) || suffix.length() != 4) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("api_key_masked", ProxyKeyUtils.masked(tenant.getTenantCode(), suffix));
                        item.put("created_at", metadata.get("created_at"));
                        list.add(item);
                    } catch (RuntimeException ignored) {
                        // 跳过损坏或旧格式的元数据，不影响其余 Key 列表。
                    }
                }
            }
        }

        // 兼容迁移前的 Key；只从旧索引提取末四位，不再返回完整密钥。
        for (String key : scanKeys("proxykey:" + tenant.getTenantCode() + ":*")) {
            String randomPart = key.substring(key.lastIndexOf(":") + 1);
            if (randomPart.length() < 4) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("api_key_masked", ProxyKeyUtils.masked(
                    tenant.getTenantCode(), randomPart.substring(randomPart.length() - 4)));
            list.add(item);
        }
        return ApiResponse.success(list);
    }

    private List<String> scanKeys(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_BATCH_SIZE)
                .build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
