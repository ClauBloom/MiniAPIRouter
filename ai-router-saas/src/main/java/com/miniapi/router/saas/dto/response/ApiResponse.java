package com.miniapi.router.saas.dto.response;

import lombok.Data;
import lombok.Builder;

/**
 * 统一 API 响应封装类。
 * 
 * <p>用于封装所有 API 接口的返回结果，提供统一的响应格式。
 * 包含状态码、消息、业务数据和链路追踪ID。
 * 
 * @param <T> 业务数据的类型
 */
@Data
@Builder
public class ApiResponse<T> {
    private int code;       // 状态码，0 表示成功，非 0 表示错误
    private String message; // 响应消息描述
    private T data;         // 业务数据
    private String traceId; // 链路追踪ID，用于日志关联和问题排查

    /**
     * 构建成功响应（带数据）。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(0).message("success").data(data).build();
    }

    /**
     * 构建成功响应（不带数据）。
     *
     * @param <T> 数据类型
     * @return 不带数据的成功响应对象
     */
    public static <T> ApiResponse<T> success() {
        return success(null);
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
        return ApiResponse.<T>builder().code(code).message(message).build();
    }
}
