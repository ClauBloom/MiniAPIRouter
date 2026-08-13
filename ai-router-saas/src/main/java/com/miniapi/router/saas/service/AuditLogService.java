package com.miniapi.router.saas.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.AuditLogDO;
import com.miniapi.router.saas.dto.response.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.saas.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Append-only security and administration audit writer. */
@Service
public class AuditLogService {
    private static final Set<String> SENSITIVE_TERMS = Set.of(
            "password", "secret", "token", "api_key", "authorization", "cookie");

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public PageResult<Map<String, Object>> list(int page, int pageSize, Long tenantId, String action) {
        LambdaQueryWrapper<AuditLogDO> query = new LambdaQueryWrapper<>();
        if (tenantId != null) query.eq(AuditLogDO::getTargetTenantId, tenantId);
        if (action != null && !action.isBlank()) query.eq(AuditLogDO::getAction, action);
        query.orderByDesc(AuditLogDO::getCreatedAt);
        Page<AuditLogDO> result = mapper.selectPage(new Page<>(page, pageSize), query);
        List<Map<String, Object>> events = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(events, result.getTotal(), page, pageSize);
    }

    public void record(String action, String resourceType, Long resourceId,
                       Long targetTenantId, Map<String, Object> details) {
        AuditLogDO row = new AuditLogDO();
        row.setActorUserId(TenantContext.getUserId());
        row.setActorTenantId(TenantContext.getAuthenticatedTenantId());
        row.setTargetTenantId(targetTenantId);
        row.setAction(action);
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setTraceId(TenantContext.getTraceId());
        row.setDetailsJson(serialize(sanitize(details)));
        mapper.insert(row);
    }

    private Map<String, Object> toResponse(AuditLogDO row) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", row.getId());
        response.put("actor_user_id", row.getActorUserId());
        response.put("actor_tenant_id", row.getActorTenantId());
        response.put("target_tenant_id", row.getTargetTenantId());
        response.put("action", row.getAction());
        response.put("resource_type", row.getResourceType());
        response.put("resource_id", row.getResourceId());
        response.put("trace_id", row.getTraceId());
        response.put("details", deserialize(row.getDetailsJson()));
        response.put("created_at", row.getCreatedAt());
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (details == null) return safe;
        details.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT);
            if (SENSITIVE_TERMS.stream().noneMatch(normalized::contains)) safe.put(key, value);
        });
        return safe;
    }

    private String serialize(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
