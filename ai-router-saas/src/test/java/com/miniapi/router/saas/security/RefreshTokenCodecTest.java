package com.miniapi.router.saas.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void issuesUrlSafeOpaqueTokenAndStoresOnlyHash() {
        RefreshTokenCodec.IssuedToken issued = codec.issue();

        assertThat(issued.raw()).matches("[A-Za-z0-9_-]{43}");
        assertThat(issued.hash()).matches("[0-9a-f]{64}");
        assertThat(issued.hash()).isNotEqualTo(issued.raw());
        assertThat(codec.hash(issued.raw())).isEqualTo(issued.hash());
    }

    @Test
    void eachIssuedTokenIsUnique() {
        assertThat(codec.issue().raw()).isNotEqualTo(codec.issue().raw());
    }

    @Test
    void rejectsBlankTokens() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> codec.hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
