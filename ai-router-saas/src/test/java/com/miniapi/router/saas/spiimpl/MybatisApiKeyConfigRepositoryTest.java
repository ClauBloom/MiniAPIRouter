package com.miniapi.router.saas.spiimpl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.miniapi.router.core.util.CryptoUtils;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.saas.entity.ApiKeyConfigDO;
import com.miniapi.router.saas.mapper.ApiKeyConfigMapper;
import com.miniapi.router.saas.mapper.ModelConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class MybatisApiKeyConfigRepositoryTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ApiKeyConfigDO.class);
        /* findByIds 会通过 ModelConfigMapper 的 lambda 条件查询模型映射，
         * MyBatis-Plus 3.5.17 要求 lambda cache 提前初始化 */
        TableInfoHelper.initTableInfo(assistant, com.miniapi.router.saas.entity.ModelConfigDO.class);
    }

    @Test
    void unchangedHealthStatusDoesNotEvictCache() {
        ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        MybatisApiKeyConfigRepository repository = repository(mapper, redis);

        repository.updateHealthStatus(7L, "healthy");

        verify(redis, never()).delete("apikey:v2:id:7");
        verify(mapper, never()).updateById(any(ApiKeyConfigDO.class));
    }

    @Test
    void changedHealthStatusEvictsCache() {
        ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        MybatisApiKeyConfigRepository repository = repository(mapper, redis);

        repository.updateHealthStatus(7L, "down");

        verify(redis).delete("apikey:v2:id:7");
    }

    @Test
    void findByIdsPreservesRequestedOrderAcrossCacheHitsAndMisses() {
        ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
        ModelConfigMapper modelMapper = mock(ModelConfigMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        CryptoUtils cryptoUtils = mock(CryptoUtils.class);
        when(redis.opsForValue()).thenReturn(values);

        ApiKeyConfig cached = new ApiKeyConfig();
        cached.setId(2L);
        cached.setTenantId(10L);
        cached.setStatus(1);
        cached.setApiKeyEnc("enc-2");
        when(values.multiGet(List.of("apikey:v2:id:1", "apikey:v2:id:2")))
                .thenReturn(Arrays.asList(null, JsonUtils.toJson(cached)));
        ApiKeyConfigDO missed = key(1L, "enc-1");
        when(mapper.selectList(any())).thenReturn(List.of(missed));
        when(modelMapper.selectList(any())).thenReturn(List.of());
        when(cryptoUtils.decrypt(any())).thenReturn("plain");
        MybatisApiKeyConfigRepository repository =
                new MybatisApiKeyConfigRepository(mapper, cryptoUtils, redis, modelMapper);

        List<ApiKeyConfig> result = repository.findByIds(List.of(1L, 2L));

        assertThat(result).extracting(ApiKeyConfig::getId).containsExactly(1L, 2L);
        ArgumentCaptor<String> cachedJson = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("apikey:v2:id:1"), cachedJson.capture(), eq(5L), eq(TimeUnit.MINUTES));
        assertThat(cachedJson.getValue()).contains("\"api_key_enc\":\"enc-1\"");
        assertThat(cachedJson.getValue()).doesNotContain("\"api_key\":");
    }

    @Test
    void evictionInsideTransactionRunsAfterCommit() {
        ApiKeyConfigMapper mapper = mock(ApiKeyConfigMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        MybatisApiKeyConfigRepository repository = repository(mapper, redis);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            repository.updateHealthStatus(7L, "down");
            verify(redis, never()).delete("apikey:v2:id:7");

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(redis).delete("apikey:v2:id:7");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private MybatisApiKeyConfigRepository repository(ApiKeyConfigMapper mapper, StringRedisTemplate redis) {
        return new MybatisApiKeyConfigRepository(
                mapper,
                mock(CryptoUtils.class),
                redis,
                mock(ModelConfigMapper.class));
    }

    private ApiKeyConfigDO key(Long id, String encryptedKey) {
        ApiKeyConfigDO key = new ApiKeyConfigDO();
        key.setId(id);
        key.setTenantId(10L);
        key.setStatus(1);
        key.setApiKeyEnc(encryptedKey);
        return key;
    }
}
