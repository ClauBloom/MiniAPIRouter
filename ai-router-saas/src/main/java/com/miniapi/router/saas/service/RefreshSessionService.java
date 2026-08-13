package com.miniapi.router.saas.service;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.entity.RefreshSessionDO;
import com.miniapi.router.saas.mapper.RefreshSessionMapper;
import com.miniapi.router.saas.security.RefreshTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** Creates, rotates, and revokes refresh sessions. */
@Service
public class RefreshSessionService {

    private final RefreshSessionMapper mapper;
    private final RefreshTokenCodec codec;
    private final Clock clock;
    private final Duration lifetime;

    public RefreshSessionService(RefreshSessionMapper mapper, RefreshTokenCodec codec, Clock clock,
                                 @Value("${miniapi.router.refresh-expiration:2592000000}") long expirationMs) {
        this.mapper = mapper;
        this.codec = codec;
        this.clock = clock;
        this.lifetime = Duration.ofMillis(expirationMs);
    }

    @Transactional
    public IssuedSession create(Long userId, Long tenantId, String userAgent, String ipAddress) {
        return persist(userId, tenantId, userAgent, ipAddress);
    }

    @Transactional
    public IssuedSession rotate(String rawToken, String userAgent, String ipAddress) {
        LocalDateTime now = now();
        RefreshSessionDO current = mapper.selectByTokenHash(hashOrInvalid(rawToken));
        if (current == null || current.getRevokedAt() != null
                || current.getExpiresAt() == null || !current.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        IssuedSession replacement = persist(
                current.getUserId(), current.getTenantId(), userAgent, ipAddress);
        current.setRevokedAt(now);
        current.setReplacedById(replacement.sessionId());
        mapper.updateById(current);
        return replacement;
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        RefreshSessionDO current = mapper.selectByTokenHash(codec.hash(rawToken));
        if (current != null && current.getRevokedAt() == null) {
            current.setRevokedAt(now());
            mapper.updateById(current);
        }
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        mapper.revokeAllActiveForUser(userId, now());
    }

    private IssuedSession persist(Long userId, Long tenantId, String userAgent, String ipAddress) {
        RefreshTokenCodec.IssuedToken token = codec.issue();
        RefreshSessionDO row = new RefreshSessionDO();
        row.setUserId(userId);
        row.setTenantId(tenantId);
        row.setTokenHash(token.hash());
        row.setUserAgentHash(hashOptional(userAgent));
        row.setIpAddress(safeIp(ipAddress));
        row.setExpiresAt(now().plus(lifetime));
        mapper.insert(row);
        return new IssuedSession(row.getId(), token.raw(), userId, tenantId, row.getExpiresAt());
    }

    private String hashOrInvalid(String rawToken) {
        try {
            return codec.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String hashOptional(String value) {
        return value == null || value.isBlank() ? null : codec.hash(value);
    }

    private String safeIp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), 64));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
    }

    private RouterException invalidToken() {
        return new RouterException("INVALID_REFRESH_TOKEN", "Refresh session is invalid", 401);
    }

    public record IssuedSession(Long sessionId, String rawToken, Long userId,
                                Long tenantId, LocalDateTime expiresAt) {
    }
}
