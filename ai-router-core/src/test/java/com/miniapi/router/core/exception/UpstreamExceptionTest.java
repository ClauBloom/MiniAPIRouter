package com.miniapi.router.core.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamExceptionTest {

    @Test
    void transientStatusesAreFailoverEligible() {
        // 凭证/限流/过载/服务端错误：换 Key 可能成功
        assertThat(UpstreamException.isFailoverEligible(401)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(402)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(403)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(408)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(429)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(500)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(502)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(529)).isTrue();
        // 网络层错误（未收到响应）
        assertThat(UpstreamException.isFailoverEligible(0)).isTrue();
        assertThat(UpstreamException.isFailoverEligible(-1)).isTrue();
    }

    @Test
    void deterministicRequestErrorsAreNotFailoverEligible() {
        assertThat(UpstreamException.isFailoverEligible(400)).isFalse();
        assertThat(UpstreamException.isFailoverEligible(404)).isFalse();
        assertThat(UpstreamException.isFailoverEligible(409)).isFalse();
        assertThat(UpstreamException.isFailoverEligible(413)).isFalse();
        assertThat(UpstreamException.isFailoverEligible(422)).isFalse();
    }

    @Test
    void clientFacingStatusPassesThroughUpstream4xxAndMapsOthersTo502() {
        assertThat(new UpstreamException("bad request", 400).getHttpStatus()).isEqualTo(400);
        assertThat(new UpstreamException("too large", 413).getHttpStatus()).isEqualTo(413);
        assertThat(new UpstreamException("server error", 500).getHttpStatus()).isEqualTo(502);
        assertThat(new UpstreamException("timeout", 0).getHttpStatus()).isEqualTo(502);
    }

    @Test
    void carriesUpstreamStatusAndErrorCode() {
        UpstreamException error = new UpstreamException("rate limited", 429);
        assertThat(error.getUpstreamStatus()).isEqualTo(429);
        assertThat(error.getErrorCode()).isEqualTo("UPSTREAM_ERROR");
        assertThat(error.isFailoverEligible()).isTrue();
    }

    @Test
    void legacyConstructorKeepsExplicitHttpStatus() {
        UpstreamException error = new UpstreamException("UPSTREAM_TIMEOUT", "timed out", 504);
        assertThat(error.getHttpStatus()).isEqualTo(504);
        assertThat(error.getUpstreamStatus()).isZero();
        assertThat(error.isFailoverEligible()).isTrue();
    }
}
