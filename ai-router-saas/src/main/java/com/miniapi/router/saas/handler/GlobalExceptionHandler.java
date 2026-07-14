package com.miniapi.router.saas.handler;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 基于 Spring 的 {@code @RestControllerAdvice} 实现的全局异常捕获与处理机制。
 * 统一处理控制器抛出的各类异常，返回标准化的错误响应格式。
 * </p>
 * <p>
 * 处理的异常类型：
 * <ul>
 *   <li>{@link AsyncRequestTimeoutException} - 异步请求超时（如 SSE 流式请求）</li>
 *   <li>{@link RouterException} - 业务异常，携带错误码和 HTTP 状态码</li>
 *   <li>{@link MethodArgumentNotValidException} - 参数校验异常</li>
 *   <li>{@link Exception} - 其他未捕获的通用异常</li>
 * </ul>
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理异步请求超时异常
     * <p>
     * SSE 流式请求超时时触发，仅记录警告日志，不返回响应体（连接已关闭）。
     * </p>
     *
     * @param e 异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.warn("[AsyncTimeout] SSE stream request timed out");
    }

    /**
     * 处理业务路由异常
     * <p>
     * 将 {@link RouterException} 转换为带有错误码、错误消息和追踪ID的标准响应。
     * </p>
     *
     * @param e 路由业务异常
     * @return 包含错误信息的响应实体
     */
    @ExceptionHandler(RouterException.class)
    public ResponseEntity<Map<String, Object>> handleRouterException(RouterException e) {
        // 解析异常中指定的 HTTP 状态码，若无法解析则默认 500
        HttpStatus status = HttpStatus.resolve(e.getHttpStatus());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of(
                "code", e.getHttpStatus(),
                "message", e.getMessage(),
                "error_code", e.getErrorCode(),
                "trace_id", getTraceId()
        ));
    }

    /**
     * 处理参数校验异常
     * <p>
     * 收集所有字段校验错误，拼接为错误消息返回。
     * </p>
     *
     * @param e 参数校验异常
     * @return 400 Bad Request 响应，包含详细的字段校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        // 提取所有字段校验错误并拼接为分号分隔的字符串
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid parameters");
        return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", msg,
                "error_code", "INVALID_PARAMS",
                "trace_id", getTraceId()
        ));
    }

    /**
     * 处理通用未捕获异常
     * <p>
     * 作为兜底异常处理器，捕获所有未被其他处理器处理的异常。
     * </p>
     *
     * @param e 通用异常
     * @return 500 Internal Server Error 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500,
                "message", e.getMessage() != null ? e.getMessage() : "Internal error",
                "error_code", "INTERNAL_ERROR",
                "trace_id", getTraceId()
        ));
    }

    /**
     * 获取当前请求的追踪ID
     * <p>
     * 从租户上下文中获取追踪ID，用于错误响应中追踪问题。
     * </p>
     *
     * @return 追踪ID字符串，若不存在则返回空字符串
     */
    private String getTraceId() {
        String tid = TenantContext.getTraceId();
        return tid != null ? tid : "";
    }
}
