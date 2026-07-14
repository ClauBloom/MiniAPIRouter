package com.miniapi.router.core.exception;

/**
 * 路由器通用异常基类。
 * <p>
 * 所有业务异常的父类，携带错误码和 HTTP 状态码，便于全局异常处理器
 * 统一拦截并转换为标准 API 响应格式。
 * </p>
 */
public class RouterException extends RuntimeException {

    /** 业务错误码 */
    private final String errorCode;

    /** HTTP 状态码 */
    private final int httpStatus;

    /**
     * 构造路由器异常实例。
     *
     * @param errorCode  业务错误码
     * @param message    错误描述信息
     * @param httpStatus HTTP 状态码
     */
    public RouterException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    /** @return 业务错误码 */
    public String getErrorCode() { return errorCode; }

    /** @return HTTP 状态码 */
    public int getHttpStatus() { return httpStatus; }
}
