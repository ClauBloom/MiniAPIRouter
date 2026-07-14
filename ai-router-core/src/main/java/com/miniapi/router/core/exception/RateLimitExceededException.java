package com.miniapi.router.core.exception;

/**
 * 请求限流异常。
 * <p>
 * 当客户端请求超过速率限制时抛出，对应 HTTP 429 状态码。
 * </p>
 */
public class RateLimitExceededException extends RouterException {
    public RateLimitExceededException(String message) {
        super("RATE_LIMITED", message, 429);
    }
}
