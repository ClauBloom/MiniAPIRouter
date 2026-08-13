package com.miniapi.router.saas.dto.response;

import java.time.Instant;
import java.util.List;

/** Public management API error contract. */
public record ApiErrorResponse(
        int code,
        String message,
        String errorCode,
        String traceId,
        List<ApiFieldError> details,
        Instant timestamp) {
}
