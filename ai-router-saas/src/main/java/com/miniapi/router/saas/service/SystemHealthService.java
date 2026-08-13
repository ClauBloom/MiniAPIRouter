package com.miniapi.router.saas.service;

import com.miniapi.router.saas.mapper.TenantMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemHealthService {
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redis;

    public SystemHealthService(TenantMapper tenantMapper, StringRedisTemplate redis) {
        this.tenantMapper = tenantMapper;
        this.redis = redis;
    }

    public Map<String, Object> health() {
        String database = probeDatabase();
        String redisState = probeRedis();
        boolean up = "UP".equals(database) && "UP".equals(redisState);
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", up ? "UP" : "DOWN");
        health.put("database", database);
        health.put("redis", redisState);
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    public Map<String, Object> metrics() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        metrics.put("used_memory_bytes", runtime.totalMemory() - runtime.freeMemory());
        metrics.put("max_memory_bytes", runtime.maxMemory());
        metrics.put("database", probeDatabase());
        metrics.put("redis", probeRedis());
        return metrics;
    }

    private String probeDatabase() {
        try {
            tenantMapper.selectCount(null);
            return "UP";
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    private String probeRedis() {
        try {
            redis.hasKey("health:probe");
            return "UP";
        } catch (Exception exception) {
            return "DOWN";
        }
    }
}
