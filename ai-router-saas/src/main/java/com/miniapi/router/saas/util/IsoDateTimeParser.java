package com.miniapi.router.saas.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 解析 API 接收的 ISO-8601 时间，保留输入中的本地日期时间字段。
 */
public final class IsoDateTimeParser {

    private IsoDateTimeParser() {
    }

    public static LocalDateTime parse(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value);
        }
    }
}
