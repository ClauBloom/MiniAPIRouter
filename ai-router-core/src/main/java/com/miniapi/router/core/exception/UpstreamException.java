package com.miniapi.router.core.exception;

/**
 * 上游服务异常。
 * <p>
 * 当向上游 AI 服务商转发请求失败时抛出。
 * 默认使用 UPSTREAM_ERROR 错误码和 502 状态码，
 * 支持通过子类构造器传入自定义错误码和状态码以描述具体失败原因。
 * </p>
 */
public class UpstreamException extends RouterException {

    /**
     * 使用默认上游错误码构造异常。
     *
     * @param message 错误描述信息
     */
    public UpstreamException(String message) {
        super("UPSTREAM_ERROR", message, 502);
    }

    /**
     * 使用自定义错误码构造异常，用于细分上游错误类型（如超时、限流等）。
     *
     * @param errorCode  业务错误码
     * @param message    错误描述信息
     * @param httpStatus HTTP 状态码
     */
    public UpstreamException(String errorCode, String message, int httpStatus) {
        super(errorCode, message, httpStatus);
    }
}
