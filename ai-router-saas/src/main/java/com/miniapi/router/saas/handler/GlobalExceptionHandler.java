package com.miniapi.router.saas.handler;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.util.SensitiveErrorSanitizer;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiErrorResponse;
import com.miniapi.router.saas.dto.response.ApiFieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.time.Instant;
import java.util.List;

/** Converts management exceptions to stable, safe response contracts. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException exception) {
        log.warn("[AsyncTimeout] traceId={} SSE stream request timed out", traceId());
    }

    @ExceptionHandler(RouterException.class)
    public ResponseEntity<ApiErrorResponse> handleRouterException(RouterException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getHttpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(error(
                exception.getHttpStatus(), exception.getMessage(), exception.getErrorCode(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiFieldError(
                        PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE.translate(fieldError.getField()),
                        stableReason(fieldError.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(error(
                40001, "Validation failed", "VALIDATION_FAILED", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
        log.error("[Unhandled] traceId={} type={} detail={}",
                traceId(), exception.getClass().getSimpleName(),
                SensitiveErrorSanitizer.sanitize(exception.getMessage()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
                50000, "Internal server error", "INTERNAL_ERROR", List.of()));
    }

    private ApiErrorResponse error(int code, String message, String errorCode,
                                   List<ApiFieldError> details) {
        return new ApiErrorResponse(code, message, errorCode, traceId(), details, Instant.now());
    }

    private String stableReason(String message) {
        if (message == null || message.isBlank()) {
            return "invalid";
        }
        return message.matches("[a-z0-9_]+") ? message : "invalid";
    }

    private String traceId() {
        String traceId = TenantContext.getTraceId();
        return traceId == null ? "" : traceId;
    }
}
