package com.miniapi.router.standalone.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.standalone.entity.ApiKeyConfigDO;
import com.miniapi.router.standalone.mapper.ApiKeyConfigMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 SQLite 的 API Key 配置仓储实现。
 * <p>
 * 实现 ApiKeyConfigRepository SPI 接口，使用 MyBatis-Plus Mapper 操作 SQLite 数据库。
 * 内置 Caffeine 缓存，缓存单个 Key 的配置信息，减少数据库查询。
 * API Key 在存储时加密，读取时解密。
 * </p>
 */
@Component
public class SqliteApiKeyConfigRepository implements ApiKeyConfigRepository {

    private static final long CACHE_TTL_MINUTES = 5; // 缓存过期时间（分钟）

    private final ApiKeyConfigMapper mapper;       // MyBatis-Plus Mapper
    private final CryptoUtils cryptoUtils;         // 加密工具
    private final Cache<Long, ApiKeyConfig> cache; // Key ID -> 配置的缓存

    public SqliteApiKeyConfigRepository(ApiKeyConfigMapper mapper, CryptoUtils cryptoUtils) {
        this.mapper = mapper;
        this.cryptoUtils = cryptoUtils;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(200)
                .build();
    }

    /**
     * 根据 ID 查找 API Key 配置（优先从缓存读取）。
     *
     * @param id Key ID
     * @return API Key 配置，不存在返回 null
     */
    @Override
    public ApiKeyConfig findById(Long id) {
        ApiKeyConfig cached = cache.getIfPresent(id);
        if (cached != null) return cached;
        ApiKeyConfigDO dO = mapper.selectById(id);
        if (dO == null) return null;
        ApiKeyConfig config = toDomain(dO);
        cache.put(id, config);
        return config;
    }

    /**
     * 根据 API Key 字符串查找配置。
     * 独立版不支持此功能（返回 null）。
     *
     * @param apiKey API Key 字符串
     * @return null
     */
    @Override
    public ApiKeyConfig findByApiKey(String apiKey) {
        return null;
    }

    /**
     * 查找指定租户的所有 API Key 配置。
     *
     * @param tenantId 租户 ID
     * @return API Key 配置列表
     */
    @Override
    public List<ApiKeyConfig> findByTenantId(Long tenantId) {
        List<ApiKeyConfigDO> list = mapper.selectList(
                new LambdaQueryWrapper<ApiKeyConfigDO>().eq(ApiKeyConfigDO::getTenantId, tenantId));
        return list.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * 根据 ID 列表批量查找 API Key 配置。
     * 优先从缓存读取，未命中的从数据库批量查询并回填缓存。
     *
     * @param ids Key ID 列表
     * @return API Key 配置列表
     */
    @Override
    public List<ApiKeyConfig> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<ApiKeyConfig> result = new java.util.ArrayList<>();
        List<Long> missedIds = new java.util.ArrayList<>();
        // 先从缓存中查找
        for (Long id : ids) {
            ApiKeyConfig cached = cache.getIfPresent(id);
            if (cached != null) {
                result.add(cached);
            } else {
                missedIds.add(id);
            }
        }
        // 缓存未命中的从数据库批量查询
        if (!missedIds.isEmpty()) {
            List<ApiKeyConfigDO> dbList = mapper.selectBatchIds(missedIds);
            for (ApiKeyConfigDO dO : dbList) {
                ApiKeyConfig config = toDomain(dO);
                cache.put(dO.getId(), config); // 回填缓存
                result.add(config);
            }
        }
        return result;
    }

    /**
     * 保存新的 API Key 配置。
     * 如果提供了明文 API Key，则加密后存储。
     *
     * @param config API Key 配置
     * @return 保存后的配置（包含生成的 ID）
     */
    @Override
    public ApiKeyConfig save(ApiKeyConfig config) {
        ApiKeyConfigDO dO = toDO(config);
        if (config.getApiKey() != null) {
            dO.setApiKeyEnc(cryptoUtils.encrypt(config.getApiKey())); // 加密 API Key
        }
        mapper.insert(dO);
        config.setId(dO.getId());
        return config;
    }

    /**
     * 更新 API Key 配置。
     * 如果提供了明文 API Key，则加密后更新。更新后清除缓存。
     *
     * @param config API Key 配置
     */
    @Override
    public void update(ApiKeyConfig config) {
        ApiKeyConfigDO dO = toDO(config);
        if (config.getApiKey() != null) {
            dO.setApiKeyEnc(cryptoUtils.encrypt(config.getApiKey()));
        }
        mapper.updateById(dO);
        cache.invalidate(config.getId()); // 清除缓存
    }

    /**
     * 删除 API Key（逻辑删除）。
     *
     * @param id       Key ID
     * @param tenantId 租户 ID
     */
    @Override
    public void delete(Long id, Long tenantId) {
        mapper.deleteById(id);
        cache.invalidate(id);
    }

    /**
     * 更新 API Key 状态（启用/禁用）。更新后清除缓存。
     *
     * @param id       Key ID
     * @param tenantId 租户 ID
     * @param status   状态（1=启用, 0=禁用）
     */
    @Override
    public void updateStatus(Long id, Long tenantId, int status) {
        ApiKeyConfigDO dO = new ApiKeyConfigDO();
        dO.setId(id);
        dO.setStatus(status);
        mapper.updateById(dO);
        cache.invalidate(id);
    }

    /**
     * 更新 API Key 健康状态。更新后清除缓存。
     *
     * @param id            Key ID
     * @param healthStatus  健康状态（healthy/degraded/down/unknown）
     */
    @Override
    public void updateHealthStatus(Long id, String healthStatus) {
        ApiKeyConfigDO dO = new ApiKeyConfigDO();
        dO.setId(id);
        dO.setHealthStatus(healthStatus);
        dO.setLastHealthCheckAt(java.time.LocalDateTime.now());
        mapper.updateById(dO);
        cache.invalidate(id);
    }

    /**
     * 将 DO 转换为域对象（解密 API Key）。
     *
     * @param dO 数据对象
     * @return 域对象
     */
    private ApiKeyConfig toDomain(ApiKeyConfigDO dO) {
        ApiKeyConfig c = new ApiKeyConfig();
        c.setId(dO.getId());
        c.setTenantId(dO.getTenantId());
        c.setName(dO.getName());
        c.setProvider(dO.getProvider());
        c.setProtocol(dO.getProtocol());
        c.setApiKeyEnc(dO.getApiKeyEnc());
        c.setApiKey(cryptoUtils.decrypt(dO.getApiKeyEnc())); // 解密 API Key
        c.setBaseUrl(dO.getBaseUrl());
        c.setModels(dO.getModels());
        c.setWeight(dO.getWeight());
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
     * 将域对象转换为 DO。
     *
     * @param c 域对象
     * @return 数据对象
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
        dO.setModels(c.getModels());
        dO.setWeight(c.getWeight());
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
}
