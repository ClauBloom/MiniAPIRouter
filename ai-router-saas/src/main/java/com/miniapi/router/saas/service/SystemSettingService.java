package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.entity.SystemSettingDO;
import com.miniapi.router.saas.mapper.SystemSettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemSettingService {
    private static final Map<String, String> WHITELIST = Map.of(
            "default_upstream_timeout_ms", "30000",
            "default_retry_count", "1",
            "health_check_interval_seconds", "60",
            "log_retention_days", "30",
            "tenant_proxy_key_policy", "allow");

    private final SystemSettingMapper mapper;

    public SystemSettingService(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> getConfig() {
        Map<String, String> stored = mapper.selectList(new LambdaQueryWrapper<>())
                .stream().collect(Collectors.toMap(SystemSettingDO::getSettingKey, SystemSettingDO::getSettingValue));
        Map<String, Object> config = new LinkedHashMap<>();
        WHITELIST.forEach((key, defaultValue) -> config.put(key, stored.getOrDefault(key, defaultValue)));
        return config;
    }

    @Transactional
    public Map<String, Object> updateConfig(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            throw new RouterException("INVALID_SETTING", "无有效设置", 400);
        }
        Set<String> invalid = input.keySet().stream().filter(key -> !WHITELIST.containsKey(key)).collect(Collectors.toSet());
        if (!invalid.isEmpty()) {
            throw new RouterException("INVALID_SETTING", "不允许修改的设置: " + String.join(", ", invalid), 400);
        }
        Long actor = TenantContext.getUserId();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            if (entry.getValue() == null) continue;
            SystemSettingDO existing = mapper.selectOne(new LambdaQueryWrapper<SystemSettingDO>()
                    .eq(SystemSettingDO::getSettingKey, entry.getKey()));
            if (existing != null) {
                existing.setSettingValue(String.valueOf(entry.getValue()));
                existing.setUpdatedBy(actor);
                mapper.updateById(existing);
            } else {
                SystemSettingDO setting = new SystemSettingDO();
                setting.setSettingKey(entry.getKey());
                setting.setSettingValue(String.valueOf(entry.getValue()));
                setting.setUpdatedBy(actor);
                mapper.insert(setting);
            }
        }
        return getConfig();
    }
}
