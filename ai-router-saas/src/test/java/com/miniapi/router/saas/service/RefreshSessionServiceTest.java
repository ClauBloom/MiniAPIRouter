package com.miniapi.router.saas.service;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.entity.RefreshSessionDO;
import com.miniapi.router.saas.mapper.RefreshSessionMapper;
import com.miniapi.router.saas.security.RefreshTokenCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T15:00:00Z");
    private RefreshSessionMapper mapper;
    private RefreshTokenCodec codec;
    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RefreshSessionMapper.class);
        codec = mock(RefreshTokenCodec.class);
        service = new RefreshSessionService(mapper, codec,
                Clock.fixed(NOW, ZoneOffset.UTC), 2_592_000_000L);
    }

    @Test
    void createsSessionWithoutPersistingRawTokenOrUserAgent() {
        when(codec.issue()).thenReturn(new RefreshTokenCodec.IssuedToken("raw-token", "token-hash"));
        when(codec.hash("Browser/1.0")).thenReturn("agent-hash");

        RefreshSessionService.IssuedSession issued =
                service.create(7L, 42L, "Browser/1.0", "127.0.0.1");

        ArgumentCaptor<RefreshSessionDO> row = ArgumentCaptor.forClass(RefreshSessionDO.class);
        verify(mapper).insert(row.capture());
        assertThat(issued.rawToken()).isEqualTo("raw-token");
        assertThat(row.getValue().getTokenHash()).isEqualTo("token-hash");
        assertThat(row.getValue().getTokenHash()).doesNotContain("raw-token");
        assertThat(row.getValue().getUserAgentHash()).isEqualTo("agent-hash");
        assertThat(row.getValue().getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW.plusSeconds(30L * 24 * 60 * 60), ZoneOffset.UTC));
    }

    @Test
    void rotatesActiveSessionAndLinksReplacement() {
        RefreshSessionDO current = activeSession();
        when(codec.hash("old-raw")).thenReturn("old-hash");
        when(codec.hash("Browser/2.0")).thenReturn("new-agent-hash");
        when(codec.issue()).thenReturn(new RefreshTokenCodec.IssuedToken("new-raw", "new-hash"));
        when(mapper.selectByTokenHash("old-hash")).thenReturn(current);
        when(mapper.insert(any(RefreshSessionDO.class))).thenAnswer(invocation -> {
            RefreshSessionDO replacement = invocation.getArgument(0);
            replacement.setId(99L);
            return 1;
        });

        RefreshSessionService.IssuedSession issued =
                service.rotate("old-raw", "Browser/2.0", "10.0.0.2");

        assertThat(issued.rawToken()).isEqualTo("new-raw");
        assertThat(issued.userId()).isEqualTo(7L);
        assertThat(current.getRevokedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(current.getReplacedById()).isEqualTo(99L);
        verify(mapper).updateById(current);
    }

    @Test
    void rejectsUnknownRevokedAndExpiredTokensWithSamePublicError() {
        when(codec.hash("unknown")).thenReturn("unknown-hash");
        when(mapper.selectByTokenHash("unknown-hash")).thenReturn(null);
        assertInvalid(() -> service.rotate("unknown", null, null));

        RefreshSessionDO revoked = activeSession();
        revoked.setRevokedAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(codec.hash("revoked")).thenReturn("revoked-hash");
        when(mapper.selectByTokenHash("revoked-hash")).thenReturn(revoked);
        assertInvalid(() -> service.rotate("revoked", null, null));

        RefreshSessionDO expired = activeSession();
        expired.setExpiresAt(LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(codec.hash("expired")).thenReturn("expired-hash");
        when(mapper.selectByTokenHash("expired-hash")).thenReturn(expired);
        assertInvalid(() -> service.rotate("expired", null, null));

        verify(mapper, never()).insert(any(RefreshSessionDO.class));
    }

    @Test
    void revokesAllActiveSessionsForUser() {
        service.revokeAllForUser(7L);

        verify(mapper).revokeAllActiveForUser(
                7L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private RefreshSessionDO activeSession() {
        RefreshSessionDO row = new RefreshSessionDO();
        row.setId(1L);
        row.setUserId(7L);
        row.setTenantId(42L);
        row.setTokenHash("old-hash");
        row.setExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
        return row;
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(RouterException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(error.getHttpStatus()).isEqualTo(401);
        });
    }
}
