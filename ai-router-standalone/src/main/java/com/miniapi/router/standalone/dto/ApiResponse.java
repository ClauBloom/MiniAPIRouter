package com.miniapi.router.standalone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应封装类。
 * <p>
 * 使用 Java Record 定义的不可变响应对象，包含状态码、消息、数据和追踪 ID。
 * 使用 @JsonInclude(NON_NULL) 确保序列化时忽略 null 字段。
 * </p>
 *
 * @param code     响应状态码，0 表示成功，非 0 表示错误
 * @param message  响应消息描述
 * @param data     响应数据
 * @param traceId  请求追踪 ID，用于链路追踪
 * @param <T>      数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    /**
     * 构建带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, null);
    }

    /**
     * 构建不带数据的成功响应。
     *
     * @param <T> 数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "success", null, null);
    }

    /**
     * 构建错误响应。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 错误响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }
}
