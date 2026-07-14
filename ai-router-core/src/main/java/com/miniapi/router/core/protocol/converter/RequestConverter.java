package com.miniapi.router.core.protocol.converter;

import com.miniapi.router.core.protocol.UnifiedRequest;
import java.util.Map;

/**
 * 请求转换器接口，定义协议转换的核心约定。
 * <p>
 * 实现类负责将特定协议（如 OpenAI、Anthropic）的原始请求
 * 转换为内部统一格式 {@link UnifiedRequest}，以及反向构建上游请求。
 */
public interface RequestConverter {
    /**
     * 将原始请求转换为内部统一请求对象。
     *
     * @param rawRequest 原始请求参数
     * @param apiKey     API 密钥
     * @return 统一请求对象
     */
    UnifiedRequest convert(Map<String, Object> rawRequest, String apiKey);

    /**
     * 判断当前转换器是否支持指定的协议。
     *
     * @param protocol 协议名称（如 "openai"、"anthropic"）
     * @return 是否支持该协议
     */
    boolean supports(String protocol);

    /**
     * 将统一请求反向构建为指定协议的上游请求体。
     *
     * @param request          统一请求对象
     * @param upstreamProtocol 上游协议名称
     * @return 上游请求参数字典
     */
    Map<String, Object> buildUpstreamRequest(UnifiedRequest request, String upstreamProtocol);
}
