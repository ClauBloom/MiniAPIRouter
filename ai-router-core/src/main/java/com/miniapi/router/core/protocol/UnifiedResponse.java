package com.miniapi.router.core.protocol;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 统一响应模型，抽象了不同协议（OpenAI、Anthropic）的响应结构。
 * <p>
 * 上游响应经转换器解析后填充此对象，随后用于构建出站响应。
 * 包含响应 ID、模型名称、生成内容、推理内容、内容块、
 * 停止原因和 token 用量统计等字段。
 */
@Data
public class UnifiedResponse {
    /** 响应唯一标识 */
    private String id;
    /** 模型名称 */
    private String model;
    /** 角色名称，默认为 assistant */
    private String role = "assistant";
    /** 生成的主要文本内容 */
    private String content;
    /** 推理过程内容（部分模型支持，如深度思考链） */
    private String reasoningContent;
    /** 内容块列表（用于多模态或 Anthropic 格式的内容块） */
    private List<Map<String, Object>> contentBlocks;
    /** 停止原因（如 stop、length、tool_calls） */
    private String finishReason;
    /** 输入 token 数量 */
    private int promptTokens;
    /** 输出 token 数量 */
    private int completionTokens;
    /** 总 token 数量 */
    private int totalTokens;
    /** 原始响应数据（用于透传未解析的字段） */
    private Map<String, Object> raw;
    /** 上游协议名称 */
    private String upstreamProtocol;
}
