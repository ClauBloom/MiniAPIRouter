package com.miniapi.router.core.protocol;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 统一请求模型，抽象了不同协议（OpenAI、Anthropic）的请求结构。
 * <p>
 * 入站请求经请求转换器解析后填充此对象，随后用于构建上游请求。
 * 包含模型参数、消息历史、系统提示、工具定义等核心字段，
 * 以及未显式映射的额外参数 extraParams。
 */
@Data
public class UnifiedRequest {
    /** 模型名称 */
    private String model;
    /** 对话消息列表 */
    private List<Map<String, Object>> messages;
    /** 系统提示词（Anthropic 协议的 system 字段） */
    private String systemPrompt;
    /** 温度参数，控制生成随机性 */
    private Double temperature;
    /** 最大生成 token 数 */
    private Integer maxTokens;
    /** Top-P 采样参数 */
    private Double topP;
    /** 可用工具列表（函数调用） */
    private List<Map<String, Object>> tools;
    /** 是否使用流式模式 */
    private Boolean stream;
    /** 未显式映射的额外参数，透传到上游请求 */
    private Map<String, Object> extraParams;
    /** 入站协议名称（如 "openai"、"anthropic"） */
    private String inboundProtocol;
    /** 上游协议名称（如 "openai"、"anthropic"） */
    private String upstreamProtocol;
}
