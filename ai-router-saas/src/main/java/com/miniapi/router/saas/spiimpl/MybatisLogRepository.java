package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.core.spi.LogRepository;
import com.miniapi.router.saas.entity.RequestLogMetaDO;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import org.springframework.stereotype.Component;

/**
 * MyBatis 日志仓库
 * <p>
 * 实现 {@link LogRepository} SPI 接口，基于 MyBatis-Plus 提供请求日志元数据的持久化功能。
 * 负责将日志元数据保存到数据库以及根据 ID 查询日志记录。
 * </p>
 */
@Component
public class MybatisLogRepository implements LogRepository {

    private final RequestLogMetaMapper mapper;  // 请求日志元数据 Mapper

    public MybatisLogRepository(RequestLogMetaMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存请求日志元数据
     * <p>
     * 将领域对象转换为 DO 对象后插入数据库，并回写生成的 ID。
     * </p>
     *
     * @param meta 请求日志元数据
     */
    @Override
    public void save(RequestLogMeta meta) {
        mapper.insert(toDO(meta));
        // 回写数据库生成的自增ID
        if (meta.getId() == null) {
            meta.setId(toDO(meta).getId());
        }
    }

    /**
     * 根据ID查询请求日志
     *
     * @param id 日志ID
     * @return 请求日志元数据，若不存在则返回 null
     */
    @Override
    public RequestLogMeta findById(Long id) {
        RequestLogMetaDO dO = mapper.selectById(id);
        return dO != null ? toDomain(dO) : null;
    }

    /**
     * 将领域对象转换为 DO 对象
     *
     * @param m 请求日志元数据领域对象
     * @return 请求日志 DO 对象
     */
    private RequestLogMetaDO toDO(RequestLogMeta m) {
        RequestLogMetaDO dO = new RequestLogMetaDO();
        dO.setId(m.getId());
        dO.setTenantId(m.getTenantId());
        dO.setUserId(m.getUserId());
        dO.setTraceId(m.getTraceId());
        dO.setRequestId(m.getRequestId());
        dO.setClientIp(m.getClientIp());
        dO.setProtocol(m.getProtocol());
        dO.setModel(m.getModel());
        dO.setMappedProvider(m.getMappedProvider());
        dO.setApiKeyId(m.getApiKeyId());
        dO.setRouteRuleId(m.getRouteRuleId());
        dO.setIntent(m.getIntent());
        dO.setPromptTokens(m.getPromptTokens());
        dO.setCompletionTokens(m.getCompletionTokens());
        dO.setTotalTokens(m.getTotalTokens());
        dO.setLatencyMs(m.getLatencyMs());
        dO.setTtftMs(m.getTtftMs());
        dO.setStatus(m.getStatus());
        dO.setFallbackCount(m.getFallbackCount());
        dO.setErrorCode(m.getErrorCode());
        dO.setErrorMessage(m.getErrorMessage());
        dO.setPromptStorageUrl(m.getPromptStorageUrl());
        dO.setResponseStorageUrl(m.getResponseStorageUrl());
        dO.setAgentId(m.getAgentId());
        dO.setAgentType(m.getAgentType());
        dO.setCreatedAt(m.getCreatedAt());
        return dO;
    }

    /**
     * 将 DO 对象转换为领域对象
     *
     * @param dO 请求日志 DO 对象
     * @return 请求日志元数据领域对象
     */
    private RequestLogMeta toDomain(RequestLogMetaDO dO) {
        RequestLogMeta m = new RequestLogMeta();
        m.setId(dO.getId());
        m.setTenantId(dO.getTenantId());
        m.setUserId(dO.getUserId());
        m.setTraceId(dO.getTraceId());
        m.setRequestId(dO.getRequestId());
        m.setClientIp(dO.getClientIp());
        m.setProtocol(dO.getProtocol());
        m.setModel(dO.getModel());
        m.setMappedProvider(dO.getMappedProvider());
        m.setApiKeyId(dO.getApiKeyId());
        m.setRouteRuleId(dO.getRouteRuleId());
        m.setIntent(dO.getIntent());
        m.setPromptTokens(dO.getPromptTokens());
        m.setCompletionTokens(dO.getCompletionTokens());
        m.setTotalTokens(dO.getTotalTokens());
        m.setLatencyMs(dO.getLatencyMs());
        m.setTtftMs(dO.getTtftMs());
        m.setStatus(dO.getStatus());
        m.setFallbackCount(dO.getFallbackCount());
        m.setErrorCode(dO.getErrorCode());
        m.setErrorMessage(dO.getErrorMessage());
        m.setPromptStorageUrl(dO.getPromptStorageUrl());
        m.setResponseStorageUrl(dO.getResponseStorageUrl());
        m.setAgentId(dO.getAgentId());
        m.setAgentType(dO.getAgentType());
        m.setCreatedAt(dO.getCreatedAt());
        return m;
    }
}
