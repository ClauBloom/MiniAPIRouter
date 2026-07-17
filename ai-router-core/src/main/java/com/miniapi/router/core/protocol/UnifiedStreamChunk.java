package com.miniapi.router.core.protocol;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 统一流块模型，表示流式响应中的单个增量数据块。
 * <p>
 * 上游流式响应解析后填充此对象，经流式转换器转换为
 * 目标协议的 SSE 事件格式。包含增量内容、推理内容、
 * 工具调用、停止原因等字段。
 */
@Data
@Builder
public class UnifiedStreamChunk {
    /** 响应唯一标识 */
    private String id;
    /** 模型名称 */
    private String model;
    /** 增量文本内容 */
    private String deltaContent;
    /** 增量角色（通常在流首块为 "assistant"） */
    private String deltaRole;
    /** 增量推理内容（深度思考链） */
    private String reasoningContent;
    /** 工具调用增量列表 */
    private List<Map<String, Object>> toolCalls;
    /** 停止原因（仅流末块非空） */
    private String finishReason;
    /** 当前块的索引位置 */
    private int index;
    /** 内容块类型，用于 Anthropic 流式块类型标识（text / tool_use / thinking） */
    private String contentType;
    /** 上游供应商名称 */
    private String upstreamProvider;
    /** 时间戳（毫秒） */
    private long timestamp;
    /** 上游流式块返回的 usage 数据 */
    private Map<String, Integer> upstreamUsage;
    /** 额外未解析字段，用于透传上游提供商的专有字段 */
    private Map<String, Object> extra;
}
