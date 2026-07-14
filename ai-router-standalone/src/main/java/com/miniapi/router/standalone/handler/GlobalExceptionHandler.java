package com.miniapi.router.standalone.handler;

import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.standalone.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * <p>
 * 使用 @RestControllerAdvice 统一捕获和处理控制器抛出的异常，
 * 将异常转换为统一的 ApiResponse 格式响应。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理路由业务异常。
     * 根据异常中的错误码映射 HTTP 状态码，返回错误响应。
     *
     * @param e 路由业务异常
     * @return 包含错误信息的 ResponseEntity
     */
    @ExceptionHandler(RouterException.class)
    public ResponseEntity<ApiResponse<Object>> handleRouterException(RouterException e) {
        int httpStatus = e.getHttpStatus();
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(e.getErrorCode() != null ? mapErrorCode(e.getErrorCode()) : 500, e.getMessage()));
    }

    /**
     * 处理资源未找到异常，返回 404 响应。
     *
     * @param e 资源未找到异常
     * @return 404 错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.error(404, "Not found"));
    }

    /**
     * 处理异步请求超时异常（SSE 流式请求超时）。
     * 仅记录日志，不返回响应体（连接已关闭）。
     *
     * @param e 异步请求超时异常
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.warn("[AsyncTimeout] SSE stream request timed out");
    }

    /**
     * 处理所有未捕获的异常，返回 500 响应。
     *
     * @param e 未捕获的异常
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("[Unhandled] {}", e.getMessage(), e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500, e.getMessage() != null ? e.getMessage() : "Internal error"));
    }

    /**
     * 将业务错误码映射为 HTTP 状态码。
     *
     * @param errorCode 业务错误码字符串
     * @return 对应的 HTTP 状态码
     */
    private int mapErrorCode(String errorCode) {
        return switch (errorCode) {
            case "UNAUTHORIZED", "INVALID_API_KEY", "INVALID_TOKEN" -> 401;  // 未认证
            case "FORBIDDEN", "TENANT_DISABLED", "TENANT_EXPIRED", "QUOTA_EXCEEDED" -> 403; // 禁止访问
            case "RESOURCE_NOT_FOUND", "NO_ROUTE_MATCHED" -> 404; // 资源未找到
            case "RATE_LIMITED" -> 429; // 限流
            case "MISSING_REQUIRED_FIELD", "INVALID_PARAMS", "INVALID_JSON" -> 400; // 参数错误
            default -> 500; // 服务器内部错误
        };
    }
}
