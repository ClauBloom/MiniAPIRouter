package com.miniapi.router.core.protocol;

import com.miniapi.router.core.protocol.converter.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 协议注册中心，管理请求/响应/流式转换器的注册与查找。
 * <p>
 * 通过 Spring 的依赖注入收集所有实现了对应接口的转换器 Bean，
 * 根据协议名称（"openai"、"anthropic"）匹配对应的转换器实例。
 * 同时提供从供应商名称推断协议类型、构建 OpenAI 格式请求的静态工具方法。
 */
@Component
public class ProtocolRegistry {

    /** 所有请求转换器的列表 */
    private final List<RequestConverter> requestConverters;
    /** 所有响应转换器的列表 */
    private final List<ResponseConverter> responseConverters;
    /** 所有流式转换器的列表 */
    private final List<StreamConverter> streamConverters;

    public ProtocolRegistry(List<RequestConverter> requestConverters,
                            List<ResponseConverter> responseConverters,
                            List<StreamConverter> streamConverters) {
        this.requestConverters = requestConverters;
        this.responseConverters = responseConverters;
        this.streamConverters = streamConverters;
    }

    /**
     * 查找支持指定协议的请求转换器。
     *
     * @param protocol 协议名称
     * @return 匹配的请求转换器
     * @throws IllegalArgumentException 如果找不到支持的转换器
     */
    public RequestConverter getRequestConverter(String protocol) {
        return requestConverters.stream()
                .filter(c -> c.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported protocol: " + protocol));
    }

    /**
     * 查找支持指定协议的响应转换器。
     */
    public ResponseConverter getResponseConverter(String protocol) {
        return responseConverters.stream()
                .filter(c -> c.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported protocol: " + protocol));
    }

    /**
     * 查找支持指定协议的流式转换器。
     */
    public StreamConverter getStreamConverter(String protocol) {
        return streamConverters.stream()
                .filter(c -> c.supports(protocol))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported protocol: " + protocol));
    }

    /**
     * 从供应商名称推断协议类型。
     * 目前仅支持 "anthropic" 和 "openai" 两种协议。
     *
     * @param provider 供应商名称
     * @return 协议名称
     */
    public static String inferProtocol(String provider) {
        return switch (provider.toLowerCase()) {
            case "anthropic" -> "anthropic";
            default -> "openai";
        };
    }

    /**
     * 从消息列表构建标准的 OpenAI 格式请求体（用于非侵入式调用）。
     *
     * @param model    模型名称
     * @param messages 消息列表
     * @param stream   是否使用流式模式
     * @return OpenAI 格式的请求参数字典
     */
    public static Map<String, Object> buildOpenAIRequestFromMessages(String model, List<Map<String, Object>> messages, boolean stream) {
        return Map.of("model", model, "messages", messages, "stream", stream);
    }
}
