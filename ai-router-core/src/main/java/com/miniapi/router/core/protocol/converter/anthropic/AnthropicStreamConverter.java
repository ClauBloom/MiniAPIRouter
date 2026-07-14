package com.miniapi.router.core.protocol.converter.anthropic;

import com.miniapi.router.core.protocol.UnifiedStreamChunk;
import com.miniapi.router.core.protocol.converter.StreamConverter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic 协议的流式 SSE 转换器。
 * <p>
 * 将内部统一流块 {@link UnifiedStreamChunk} 转换为 Anthropic SSE 事件流格式。
 * Anthropic 的 SSE 流包含多种事件类型：message_start, content_block_start,
 * content_block_delta, content_block_stop, message_delta, message_stop。
 */
@Component
public class AnthropicStreamConverter implements StreamConverter {

    @Override
    public String toSseChunk(UnifiedStreamChunk chunk, String inboundProtocol) {
        StringBuilder sb = new StringBuilder();
        // 当有 deltaRole 为 assistant 时，发送流开始的元数据事件
        if (chunk.getDeltaRole() != null && "assistant".equals(chunk.getDeltaRole())) {
            // message_start 事件：包含消息 ID、模型、角色和空用量统计
            sb.append("event: message_start\n");
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "message_start");
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("id", chunk.getId());
            message.put("type", "message");
            message.put("role", "assistant");
            message.put("model", chunk.getModel());
            message.put("content", List.of());
            message.put("stop_reason", null);
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("input_tokens", 0);
            usage.put("output_tokens", 0);
            message.put("usage", usage);
            msg.put("message", message);
            sb.append("data: ").append(com.miniapi.router.core.util.JsonUtils.toJson(msg)).append("\n\n");

            // content_block_start 事件：声明内容块开始
            sb.append("event: content_block_start\n");
            Map<String, Object> blockStart = new LinkedHashMap<>();
            blockStart.put("type", "content_block_start");
            blockStart.put("index", 0);
            Map<String, Object> contentBlock = new LinkedHashMap<>();
            contentBlock.put("type", "text");
            contentBlock.put("text", "");
            blockStart.put("content_block", contentBlock);
            sb.append("data: ").append(com.miniapi.router.core.util.JsonUtils.toJson(blockStart)).append("\n\n");
        }
        // 增量文本内容发送为 content_block_delta
        if (chunk.getDeltaContent() != null && !chunk.getDeltaContent().isEmpty()) {
            sb.append("event: content_block_delta\n");
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("type", "content_block_delta");
            delta.put("index", chunk.getIndex());
            Map<String, Object> textDelta = new LinkedHashMap<>();
            textDelta.put("type", "text_delta");
            textDelta.put("text", chunk.getDeltaContent());
            delta.put("delta", textDelta);
            sb.append("data: ").append(com.miniapi.router.core.util.JsonUtils.toJson(delta)).append("\n\n");
        }
        // 流结束时发送停止事件
        if (chunk.getFinishReason() != null) {
            // content_block_stop 事件
            sb.append("event: content_block_stop\n");
            Map<String, Object> stop = new LinkedHashMap<>();
            stop.put("type", "content_block_stop");
            stop.put("index", 0);
            sb.append("data: ").append(com.miniapi.router.core.util.JsonUtils.toJson(stop)).append("\n\n");

            // message_delta 事件：包含停止原因
            sb.append("event: message_delta\n");
            Map<String, Object> msgDelta = new LinkedHashMap<>();
            msgDelta.put("type", "message_delta");
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("stop_reason", mapStop(chunk.getFinishReason()));
            msgDelta.put("delta", delta);
            sb.append("data: ").append(com.miniapi.router.core.util.JsonUtils.toJson(msgDelta)).append("\n\n");
        }
        return sb.toString();
    }

    @Override
    public String toDoneMark(String inboundProtocol) {
        return "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";
    }

    /**
     * 将内部统一的停止原因映射为 Anthropic 协议格式。
     * stop -> end_turn, length -> max_tokens, tool_calls -> tool_use
     */
    private String mapStop(String reason) {
        return switch (reason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> reason;
        };
    }

    @Override
    public boolean supports(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol);
    }
}
