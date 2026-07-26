package com.miniapi.router.saas.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IsoDateTimeParserTest {

    @Test
    void parsesUtcTimestamp() {
        assertThat(IsoDateTimeParser.parse("2026-07-26T10:15:30Z"))
                .isEqualTo(LocalDateTime.of(2026, 7, 26, 10, 15, 30));
    }

    @Test
    void parsesLocalTimestamp() {
        assertThat(IsoDateTimeParser.parse("2026-07-26T10:15:30"))
                .isEqualTo(LocalDateTime.of(2026, 7, 26, 10, 15, 30));
    }

    @Test
    void preservesLocalFieldsFromOffsetTimestamp() {
        assertThat(IsoDateTimeParser.parse("2026-07-26T10:15:30+08:00"))
                .isEqualTo(LocalDateTime.of(2026, 7, 26, 10, 15, 30));
    }
}
