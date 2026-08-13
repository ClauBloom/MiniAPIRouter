package com.miniapi.router.saas.handler;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiErrorResponse;
import com.miniapi.router.saas.dto.response.ApiFieldError;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        TenantContext.setTraceId("trace_01JABCDEF0123456789");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsStableBusinessErrorContract() {
        ResponseEntity<ApiErrorResponse> response = handler.handleRouterException(
                new RouterException("RESOURCE_NOT_FOUND", "租户不存在", 404));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().traceId()).isEqualTo("trace_01JABCDEF0123456789");
        assertThat(response.getBody().details()).isEmpty();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void returnsStructuredValidationDetails() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "quotaLimit", null, false,
                null, null, "must_be_positive"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .containsExactly(new ApiFieldError("quota_limit", "must_be_positive"));
    }

    @Test
    void genericErrorsNeverExposeInternalExceptionMessages() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(
                    new SQLException("Bearer secret-token at https://internal.example/private"));

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Internal server error");
            assertThat(response.getBody().message()).doesNotContain("secret", "internal");
            assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).doesNotContain("secret-token", "internal.example", "/private");
            assertThat(event.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
