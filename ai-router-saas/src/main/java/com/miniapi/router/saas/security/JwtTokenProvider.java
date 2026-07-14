package com.miniapi.router.saas.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 提供者
 * <p>
 * 负责 JWT Token 的生成、解析和验证。
 * 使用 HMAC-SHA 算法对 Token 进行签名，确保 Token 的完整性和不可篡改性。
 * </p>
 * <p>
 * Token 中包含的声明（Claims）：
 * <ul>
 *   <li>subject - 用户ID</li>
 *   <li>username - 用户名</li>
 *   <li>role - 用户角色</li>
 *   <li>tenant_id - 租户ID</li>
 *   <li>tenant_name - 租户名称</li>
 * </ul>
 * </p>
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;            // JWT 签名密钥
    private final long expirationMs;         // Token 过期时间（毫秒）

    /**
     * 构造函数
     * <p>
     * 从配置中读取密钥和过期时间，生成 HMAC-SHA 签名密钥。
     * </p>
     *
     * @param secret       JWT 密钥（从配置项 miniapi.router.jwt-secret 读取）
     * @param expirationMs Token 过期时间（毫秒，默认 86400000 即 24 小时）
     */
    public JwtTokenProvider(@Value("${miniapi.router.jwt-secret}") String secret,
                            @Value("${miniapi.router.jwt-expiration:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 生成 JWT Token
     * <p>
     * 根据用户信息生成带有签名的 JWT Token。
     * </p>
     *
     * @param userId     用户ID
     * @param username   用户名
     * @param role       用户角色
     * @param tenantId   租户ID
     * @param tenantName 租户名称
     * @return 签名后的 JWT Token 字符串
     */
    public String generateToken(Long userId, String username, String role, Long tenantId, String tenantName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("tenant_id", tenantId)
                .claim("tenant_name", tenantName)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 JWT Token
     * <p>
     * 验证签名并解析 Token，返回 Token 中的声明信息。
     * 若签名无效或 Token 过期，将抛出异常。
     * </p>
     *
     * @param token JWT Token 字符串
     * @return Token 中的声明信息
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 有效性
     * <p>
     * 尝试解析 Token，若解析成功则表示 Token 有效。
     * </p>
     *
     * @param token JWT Token 字符串
     * @return true 表示有效，false 表示无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 Token 过期时间
     *
     * @return 过期时间（毫秒）
     */
    public long getExpirationMs() {
        return expirationMs;
    }
}
