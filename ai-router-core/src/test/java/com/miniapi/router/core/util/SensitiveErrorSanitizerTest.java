package com.miniapi.router.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveErrorSanitizerTest {

    @Test
    void masksUrlHostPathAndQueryValues() {
        assertThat(SensitiveErrorSanitizer.sanitize(
                "failed at https://api.example.com/v1/models?key=secret&mode=test"))
                .isEqualTo("failed at https://***.com/***/***?key=***&mode=***");
    }

    @Test
    void masksDomainsIpsAndCredentials() {
        String raw = "api.openai.com 192.168.1.10 sk-exampleSecret123 "
                + "Bearer abc.def.secret";

        assertThat(SensitiveErrorSanitizer.sanitize(raw))
                .isEqualTo("***.***.com ***.***.***.*** sk-*** Bearer ***");
    }

    @Test
    void masksArbitraryDomainsInternalHostsAndShortCredentials() {
        String raw = "api.vendor.tech http://gateway:8080/private Bearer x sk-a";

        assertThat(SensitiveErrorSanitizer.sanitize(raw))
                .isEqualTo("***.***.tech http://***/*** Bearer *** sk-***");
    }

    @Test
    void preservesPunctuationAroundMaskedUrls() {
        assertThat(SensitiveErrorSanitizer.sanitize(
                "Error at (https://api.example.com/v1). retry"))
                .isEqualTo("Error at (https://***.com/***). retry");
    }

    @Test
    void preservesOrdinaryErrorsAndHandlesEmptyValues() {
        assertThat(SensitiveErrorSanitizer.sanitize("model gpt-4.5 rejected role=user"))
                .isEqualTo("model gpt-4.5 rejected role=user");
        assertThat(SensitiveErrorSanitizer.sanitize("")).isEmpty();
        assertThat(SensitiveErrorSanitizer.sanitize(null)).isNull();
    }
}
