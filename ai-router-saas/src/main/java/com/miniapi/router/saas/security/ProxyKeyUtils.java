package com.miniapi.router.saas.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 代理 Key 的 Redis 索引和脱敏工具。Redis v2 索引仅保存随机部分的 SHA-256 摘要。
 */
public final class ProxyKeyUtils {

    private ProxyKeyUtils() {
    }

    public static String redisKey(String tenantCode, String randomPart) {
        return "proxykey:v2:" + tenantCode + ":" + sha256(randomPart);
    }

    public static String legacyRedisKey(String tenantCode, String randomPart) {
        return "proxykey:" + tenantCode + ":" + randomPart;
    }

    public static String masked(String tenantCode, String suffix) {
        return "sk-miniapi-" + tenantCode + "-..." + suffix;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
