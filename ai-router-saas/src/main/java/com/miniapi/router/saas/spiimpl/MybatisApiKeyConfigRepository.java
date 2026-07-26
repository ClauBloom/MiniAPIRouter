package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.entity.ModelConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import com.miniapi.router.saas.mapper.ModelConfigMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * API Key 的密钥在存储时加密，查询时解密。Redis 缓存中仅保存密文（apiKeyEnc），
 * 读取缓存后再在内存中解密，避免明文密钥落入 Redis。
 * </p>
 */
@Component
public class MybatisApiKeyConfigRepository implements ApiKeyConfigRepository {

    private static final String CACHE_PREFIX = "apikey:v2:id:";  // v2 仅缓存密文，不与旧明文格式混用
    private static final String LEGACY_CACHE_PREFIX = "apikey:id:";
    private static final long CACHE_TTL_MINUTES = 5;              // 缓存过期时间（分钟）

    private final ApiKeyConfigMapper mapper;       // MyBatis-Plus Mapper
    private final CryptoUtils cryptoUtils;          // 加密工具类
    private final StringRedisTemplate redis;        // Redis 模板
    private final ModelConfigMapper modelConfigMapper;  // 模型配置 Mapper（用于批量加载模型映射）

    public MybatisApiKeyConfigRepository(ApiKeyConfigMapper mapper, CryptoUtils cryptoUtils,
                                         StringRedisTemplate redis,
                                         ModelConfigMapper modelConfigMapper) {
        this.mapper = mapper;
        this.cryptoUtils = cryptoUtils;
        this.redis = redis;
        this.modelConfigMapper = modelConfigMapper;
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
            return fromCache(cached);
        }
        // 缓存未命中，查询数据库
        ApiKeyConfigDO dO = mapper.selectById(id);
        if (dO == null) return null;
        ApiKeyConfig config = toDomain(dO, loadModelMappings(List.of(id)).get(id));
        // 写入缓存
        cachePut(cacheKey, config);
        return config;
    }

    /**
     * 根据 API Key 字符串查询配置（未实现）
     * <p>
     * 密钥以非确定性加密（AES-GCM 随机 IV）存储，无法按明文密钥反查，该方法不受支持。
     * </p>
     *
     * @param apiKey API Key 字符串
     * @return 始终返回 null
     */
    @Override
    public ApiKeyConfig findByApiKey(String apiKey) {
        return null;
    }

    /**
     * 根据租户ID查询所有已启用的 API Key 配置
     * <p>
     * 模型映射通过单次 IN 查询批量加载，避免 N+1 查询。
     * </p>
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
        return toDomainList(list);
    }

    /**
     * 根据ID列表批量查询 API Key 配置
     * <p>
     * 先通过 Redis MGET 批量查询缓存，未命中的 ID 再从数据库批量查询并回填缓存。
     * </p>
     *
     * @param ids ID 列表
     * @return API Key 配置列表（仅启用状态）
     */
    @Override
    public List<ApiKeyConfig> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> cacheKeys = ids.stream().map(id -> CACHE_PREFIX + id).collect(Collectors.toList());
        // 一次 MGET 批量查询缓存，避免逐个往返 Redis
        List<String> cachedValues = redis.opsForValue().multiGet(cacheKeys);

        Map<Long, ApiKeyConfig> resultById = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String cached = cachedValues != null && i < cachedValues.size() ? cachedValues.get(i) : null;
            if (cached != null) {
                resultById.put(ids.get(i), fromCache(cached));
            } else {
                missedIds.add(ids.get(i));
            }
        }
        // 批量查询未命中的 ID 并回填缓存
        if (!missedIds.isEmpty()) {
            List<ApiKeyConfigDO> dbList = mapper.selectList(
                    new LambdaQueryWrapper<ApiKeyConfigDO>()
                            .in(ApiKeyConfigDO::getId, missedIds)
                            .eq(ApiKeyConfigDO::getStatus, 1));
            Map<Long, Map<String, String>> mappings = loadModelMappings(
                    dbList.stream().map(ApiKeyConfigDO::getId).collect(Collectors.toList()));
            for (ApiKeyConfigDO dO : dbList) {
                ApiKeyConfig config = toDomain(dO, mappings.get(dO.getId()));
                cachePut(CACHE_PREFIX + dO.getId(), config);
                resultById.put(dO.getId(), config);
            }
        }
        // 数据库返回顺序和缓存命中顺序都不稳定，按调用方 ID 顺序重组并去重。
        return new java.util.LinkedHashSet<>(ids).stream()
                .map(resultById::get)
                .filter(java.util.Objects::nonNull)
                .filter(ApiKeyConfig::isEnabled)
                .collect(Collectors.toList());
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
     * <p>
     * WHERE 条件同时包含 ID 和租户 ID，防止跨租户越权删除。
     * </p>
     *
     * @param id       配置ID
     * @param tenantId 租户ID
     */
    @Override
    public void delete(Long id, Long tenantId) {
        mapper.delete(new LambdaQueryWrapper<ApiKeyConfigDO>()
                .eq(ApiKeyConfigDO::getId, id)
                .eq(ApiKeyConfigDO::getTenantId, tenantId));
        evict(id);
    }

    /**
     * 更新 API Key 配置状态（启用/禁用）
     * <p>
     * WHERE 条件同时包含 ID 和租户 ID，防止跨租户越权修改。
     * </p>
     *
     * @param id       配置ID
     * @param tenantId 租户ID
     * @param status   状态值（1=启用，0=禁用）
     */
    @Override
    public void updateStatus(Long id, Long tenantId, int status) {
        mapper.update(null, new LambdaUpdateWrapper<ApiKeyConfigDO>()
                .eq(ApiKeyConfigDO::getId, id)
                .eq(ApiKeyConfigDO::getTenantId, tenantId)
                .set(ApiKeyConfigDO::getStatus, status));
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
        int updated = mapper.update(null, new LambdaUpdateWrapper<ApiKeyConfigDO>()
                .eq(ApiKeyConfigDO::getId, id)
                .and(w -> w.ne(ApiKeyConfigDO::getHealthStatus, healthStatus)
                        .or().isNull(ApiKeyConfigDO::getHealthStatus))
                .set(ApiKeyConfigDO::getHealthStatus, healthStatus)
                .set(ApiKeyConfigDO::getLastHealthCheckAt, java.time.LocalDateTime.now()));
        if (updated > 0) {
            evict(id);
        }
    }

    /**
     * 清除指定配置的缓存
     *
     * @param id 配置ID
     */
    private void evict(Long id) {
        Runnable eviction = () -> {
            redis.delete(CACHE_PREFIX + id);
            // 清除可能含明文密钥的旧格式缓存。
            redis.delete(LEGACY_CACHE_PREFIX + id);
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }

    /**
     * 将配置写入 Redis 缓存。
     * <p>
     * 序列化前临时清空明文 apiKey，只缓存密文（apiKeyEnc），避免明文密钥落入 Redis。
     * </p>
     */
    private void cachePut(String cacheKey, ApiKeyConfig config) {
        String plainKey = config.getApiKey();
        config.setApiKey(null);
        try {
            redis.opsForValue().set(cacheKey, JsonUtils.toJson(config), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } finally {
            config.setApiKey(plainKey);
        }
    }

    /**
     * 从缓存 JSON 恢复配置对象，并在内存中解密 apiKeyEnc 得到明文密钥。
     */
    private ApiKeyConfig fromCache(String json) {
        ApiKeyConfig config = JsonUtils.fromJson(json, ApiKeyConfig.class);
        if (config.getApiKey() == null && config.getApiKeyEnc() != null) {
            config.setApiKey(cryptoUtils.decrypt(config.getApiKeyEnc()));
        }
        return config;
    }

    /**
     * 批量转换 DO 列表为领域对象列表，模型映射一次性批量加载。
     */
    private List<ApiKeyConfig> toDomainList(List<ApiKeyConfigDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, Map<String, String>> mappings = loadModelMappings(
                list.stream().map(ApiKeyConfigDO::getId).collect(Collectors.toList()));
        return list.stream()
                .map(dO -> toDomain(dO, mappings.get(dO.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 将 DO 对象转换为领域对象
     * <p>
     * 同时解密 API Key 密文。
     * </p>
     *
     * @param dO           API Key 配置 DO 对象
     * @param modelMapping 该 Key 的模型映射（可为 null）
     * @return API Key 配置领域对象
     */
    private ApiKeyConfig toDomain(ApiKeyConfigDO dO, Map<String, String> modelMapping) {
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
        c.setModelMapping(modelMapping);
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
     * 从 model_config 表批量加载多个 Key 的模型映射（单次 IN 查询）。
     * <p>
     * 如果某个 Key 在 model_config 表中无数据（迁移前），结果 Map 中不含该 Key，
     * 调用方取到 null，与旧行为保持一致。
     * </p>
     *
     * @param keyIds API Key ID 集合
     * @return keyId -> (对外模型名 -> 真实模型名) 的映射
     */
    private Map<Long, Map<String, String>> loadModelMappings(Collection<Long> keyIds) {
        if (keyIds == null || keyIds.isEmpty()) return Map.of();
        List<ModelConfigDO> models = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigDO>()
                        .in(ModelConfigDO::getApiKeyId, keyIds)
                        .orderByAsc(ModelConfigDO::getId));
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (ModelConfigDO m : models) {
            result.computeIfAbsent(m.getApiKeyId(), k -> new LinkedHashMap<>())
                    .put(m.getDisplayName(), m.getRealName());
        }
        return result;
    }
}
