package com.miniapi.router.core.util;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 链路追踪工具类。
 * <p>
 * 使用安全随机数生成分布式链路追踪所需的 traceId 和 requestId。
 * traceId 为 16 位十六进制字符串，用于跨服务链路关联；
 * requestId 以 "req-" 为前缀，为 32 位十六进制字符串，用于单次请求唯一标识。
 * </p>
 */
public final class TraceUtils {

    /** 安全随机数生成器 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceUtils() {}

    /**
     * 生成新的链路追踪 ID（16 位十六进制字符串）。
     *
     * @return traceId
     */
    public static String newTraceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成新的请求 ID（"req-" + 32 位十六进制字符串）。
     *
     * @return requestId
     */
    public static String newRequestId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "req-" + HexFormat.of().formatHex(bytes);
    }
}
