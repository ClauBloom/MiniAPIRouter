package com.miniapi.router.core.protocol.converter;

import com.miniapi.router.core.protocol.UnifiedResponse;
import java.util.Map;

/**
 * 响应转换器接口，定义响应协议转换的核心约定。
 * <p>
 * 实现类负责将内部统一响应 {@link UnifiedResponse} 转换为
 * 特定协议（如 OpenAI、Anthropic）的响应格式，以及错误响应。
 */
public interface ResponseConverter {
    /**
     * 将统一响应转换为目标协议的响应格式。
     *
     * @param response         统一响应对象
     * @param inboundProtocol  入站协议名称
     * @return 目标协议格式的响应字典
     */
    Map<String, Object> convert(UnifiedResponse response, String inboundProtocol);

    /**
     * 判断当前转换器是否支持指定的协议。
     *
     * @param protocol 协议名称
     * @return 是否支持该协议
     */
    boolean supports(String protocol);

    /**
     * 将错误信息转换为目标协议格式的错误响应。
     *
     * @param errorCode        错误码
     * @param message          错误消息
     * @param inboundProtocol  入站协议名称
     * @return 目标协议格式的错误响应字典
     */
    Map<String, Object> convertError(String errorCode, String message, String inboundProtocol);
}
