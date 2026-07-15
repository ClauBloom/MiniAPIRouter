package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MyBatis API Key 配置仓库
 * <p>
 * 实现 {@link ApiKeyConfigRepository} SPI 接口，基于 MyBatis-Plus 和 Redis 缓存提供 API Key 配置的数据访问层。
 * 通过 Redis 缓存减少数据库查询，缓存 TTL 为 5 分钟，写操作时自动失效缓存。
 * </p>
 * <p>
 * API Key 的密钥在存储时加密，查询时解密，确保敏感信息安全。
 * </p>
 */
@Component
public class MybatisApiKeyConfigRepository implements ApiKeyConfigRepository {

    private static final String CACHE_PREFIX = "apikey:id:";     // Redis 缓存键前缀
    private static final long CACHE_TTL_MINUTES = 5;              // 缓存过期时间（分钟）

    private final ApiKeyConfigMapper mapper;       // MyBatis-Plus Mapper
    private final CryptoUtils cryptoUtils;          // 加密工具类
    private final StringRedisTemplate redis;        // Redis 模板

    public MybatisApiKeyConfigRepository(ApiKeyConfigMapper mapper, CryptoUtils cryptoUtils,
                                         StringRedisTemplate redis) {
        this.mapper = mapper;
        this.cryptoUtils = cryptoUtils;
        this.redis = redis;
    }

    /**
     * 根据ID查询 API Key 配置
     * <p>
     * 优先从 Redis 缓存查询，缓存未命中时查询数据库并写入缓存。
     * </p>
     *
     * @param id 配置ID
     * @return API Key 配置领域对象，若不存在则返回 null
     */
    @Override
    public ApiKeyConfig findById(Long id) {
        // 尝试从缓存获取
        String cacheKey = CACHE_PREFIX + id;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, ApiKeyConfig.class);
        }
        // 缓存未命中，查询数据库
        ApiKeyConfigDO dO = mapper.selectById(id);
        if (dO == null) return null;
        ApiKeyConfig config = toDomain(dO);
        // 写入缓存
        redis.opsForValue().set(cacheKey, JsonUtils.toJson(config), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return config;
    }

    /**
     * 根据 API Key 字符串查询配置（未实现）
     *
     * @param apiKey API Key 字符串
     * @return 始终返回 null
     */
    @Override
    public ApiKeyConfig findByApiKey(String apiKey) {
        return null;
    }

    /**
     * 根据租户ID查询所有 API Key 配置
     *
     * @param tenantId 租户ID
     * @return API Key 配置列表
     */
    @Override
    public List<ApiKeyConfig> findByTenantId(Long tenantId) {
        List<ApiKeyConfigDO> list = mapper.selectList(
                new LambdaQueryWrapper<ApiKeyConfigDO>()
                        .eq(ApiKeyConfigDO::getTenantId, tenantId)
                        .eq(ApiKeyConfigDO::getStatus, 1));
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * 根据ID列表批量查询 API Key 配置
     * <p>
     * 先从 Redis 缓存批量查询，未命中的 ID 再从数据库批量查询并回填缓存。
     * </p>
     *
     * @param ids ID 列表
     * @return API Key 配置列表
     */
    @Override
    public List<ApiKeyConfig> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<ApiKeyConfig> result = new java.util.ArrayList<>();
        List<Long> missedIds = new java.util.ArrayList<>();
        // 逐个从缓存查询，记录未命中的 ID
        for (Long id : ids) {
            String cacheKey = CACHE_PREFIX + id;
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                result.add(JsonUtils.fromJson(cached, ApiKeyConfig.class));
            } else {
                missedIds.add(id);
            }
        }
        // 批量查询未命中的 ID 并回填缓存
        if (!missedIds.isEmpty()) {
            List<ApiKeyConfigDO> dbList = mapper.selectList(
                    new LambdaQueryWrapper<ApiKeyConfigDO>()
                            .in(ApiKeyConfigDO::getId, missedIds)
                            .eq(ApiKeyConfigDO::getStatus, 1));
            for (ApiKeyConfigDO dO : dbList) {
                ApiKeyConfig config = toDomain(dO);
                redis.opsForValue().set(CACHE_PREFIX + dO.getId(), JsonUtils.toJson(config),
                        CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                result.add(config);
            }
        }
        return result.stream().filter(ApiKeyConfig::isEnabled).collect(Collectors.toList());
    }

    /**
     * 保存 API Key 配置
     * <p>
     * 将 API Key 加密后存储到数据库。
     * </p>
     *
     * @param config API Key 配置领域对象
     * @return 保存后的配置（包含生成的ID）
     */
    @Override
    public ApiKeyConfig save(ApiKeyConfig config) {
        ApiKeyConfigDO dO = toDO(config);
        // 加密 API Key
        if (config.getApiKey() != null) {
            dO.setApiKeyEnc(cryptoUtils.encrypt(config.getApiKey()));
        }
        mapper.insert(dO);
        config.setId(dO.getId());
        return config;
    }

    /**
     * 更新 API Key 配置
     * <p>
     * 更新数据库记录并清除缓存。
     * </p>
     *
     * @param config API Key 配置领域对象
     */
    @Override
    public void update(ApiKeyConfig config) {
        ApiKeyConfigDO dO = toDO(config);
        if (config.getApiKey() != null) {
            dO.setApiKeyEnc(cryptoUtils.encrypt(config.getApiKey()));
        }
        mapper.updateById(dO);
        // 清除缓存
        evict(config.getId());
    }

    /**
     * 删除 API Key 配置
     *
     * @param id       配置ID
     * @param tenantId 租户ID
     */
    @Override
    public void delete(Long id, Long tenantId) {
        mapper.deleteById(id);
        evict(id);
    }

    /**
     * 更新 API Key 配置状态（启用/禁用）
     *
     * @param id       配置ID
     * @param tenantId 租户ID
     * @param status   状态值（1=启用，0=禁用）
     */
    @Override
    public void updateStatus(Long id, Long tenantId, int status) {
        ApiKeyConfigDO dO = new ApiKeyConfigDO();
        dO.setId(id);
        dO.setStatus(status);
        mapper.updateById(dO);
        evict(id);
    }

    /**
     * 更新健康状态
     *
     * @param id           配置ID
     * @param healthStatus 健康状态（healthy、degraded、down）
     */
    @Override
    public void updateHealthStatus(Long id, String healthStatus) {
        ApiKeyConfigDO dO = new ApiKeyConfigDO();
        dO.setId(id);
        dO.setHealthStatus(healthStatus);
        dO.setLastHealthCheckAt(java.time.LocalDateTime.now());
        mapper.updateById(dO);
        evict(id);
    }

    /**
     * 清除指定配置的缓存
     *
     * @param id 配置ID
     */
    private void evict(Long id) {
        redis.delete(CACHE_PREFIX + id);
    }

    /**
     * 将 DO 对象转换为领域对象
     * <p>
     * 同时解密 API Key 密文。
     * </p>
     *
     * @param dO API Key 配置 DO 对象
     * @return API Key 配置领域对象
     */
    private ApiKeyConfig toDomain(ApiKeyConfigDO dO) {
        ApiKeyConfig c = new ApiKeyConfig();
        c.setId(dO.getId());
        c.setTenantId(dO.getTenantId());
        c.setName(dO.getName());
        c.setProvider(dO.getProvider());
        c.setProtocol(dO.getProtocol());
        c.setApiKeyEnc(dO.getApiKeyEnc());
        // 解密 API Key
        c.setApiKey(cryptoUtils.decrypt(dO.getApiKeyEnc()));
        c.setBaseUrl(dO.getBaseUrl());
        c.setModelMapping(convertModelMapping(dO.getModelMapping()));
        c.setPriority(dO.getPriority());
        c.setMaxConcurrent(dO.getMaxConcurrent());
        c.setQpsLimit(dO.getQpsLimit());
        c.setTimeoutMs(dO.getTimeoutMs());
        c.setRetryCount(dO.getRetryCount());
        c.setStatus(dO.getStatus());
        c.setHealthStatus(dO.getHealthStatus());
        c.setLastHealthCheckAt(dO.getLastHealthCheckAt());
        c.setCreatedAt(dO.getCreatedAt());
        c.setUpdatedAt(dO.getUpdatedAt());
        return c;
    }

    /**
     * 将领域对象转换为 DO 对象
     *
     * @param c API Key 配置领域对象
     * @return API Key 配置 DO 对象
     */
    private ApiKeyConfigDO toDO(ApiKeyConfig c) {
        ApiKeyConfigDO dO = new ApiKeyConfigDO();
        dO.setId(c.getId());
        dO.setTenantId(c.getTenantId());
        dO.setName(c.getName());
        dO.setProvider(c.getProvider());
        dO.setProtocol(c.getProtocol());
        dO.setApiKeyEnc(c.getApiKeyEnc());
        dO.setBaseUrl(c.getBaseUrl());
        dO.setModelMapping(c.getModelMapping());
        dO.setPriority(c.getPriority());
        dO.setMaxConcurrent(c.getMaxConcurrent());
        dO.setQpsLimit(c.getQpsLimit());
        dO.setTimeoutMs(c.getTimeoutMs());
        dO.setRetryCount(c.getRetryCount());
        dO.setStatus(c.getStatus());
        dO.setHealthStatus(c.getHealthStatus());
        dO.setLastHealthCheckAt(c.getLastHealthCheckAt());
        return dO;
    }

    /**
     * 将 DO 中的原始模型数据转换为 Map<String, String>。
     * 兼容旧格式（JSON 数组 ["a","b"]）和新格式（JSON 对象 {"a":"x","b":"y"}）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> convertModelMapping(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) {
            return (Map<String, String>) raw;
        }
        if (raw instanceof List) {
            Map<String, String> mapping = new LinkedHashMap<>();
            for (Object item : (List<?>) raw) {
                String s = String.valueOf(item);
                mapping.put(s, s);
            }
            return mapping;
        }
        return null;
    }
}
