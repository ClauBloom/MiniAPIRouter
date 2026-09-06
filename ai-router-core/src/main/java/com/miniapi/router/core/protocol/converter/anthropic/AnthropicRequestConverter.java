package com.miniapi.router.core.protocol.converter.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic 协议的请求转换器。
 * <p>
 * 负责将 Anthropic 格式的原始请求转换为内部统一请求 {@link UnifiedRequest}，
 * 以及将统一请求反向构建为上游 Anthropic 格式的请求体。
 * Anthropic 协议特有的 system 字段被映射为统一请求的 systemPrompt。
 */
@Component
public class AnthropicRequestConverter implements RequestConverter {

    /** max_tokens 安全上限，与 OpenAI 转换器保持一致 */
    private static final int MAX_TOKENS_UPPER_BOUND = 128000;

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest convert(Map<String, Object> rawRequest, String apiKey) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel((String) rawRequest.get("model"));
        req.setMessages((List<Map<String, Object>>) rawRequest.get("messages"));
        // Anthropic 协议使用独立的 system 字段，映射为统一请求的 systemPrompt
        Object system = rawRequest.get("system");
        if (system instanceof String s) {
            req.setSystemPrompt(s);
        }
        // 当 system 为非 String 类型（如 Anthropic 结构化 system prompt 数组）时，
        // 不存入 systemPrompt，也不从 extra 中移除，使其通过 extraParams 透传
        if (rawRequest.get("temperature") != null) {
            req.setTemperature(((Number) rawRequest.get("temperature")).doubleValue());
        }
        if (rawRequest.get("max_tokens") != null) {
            int val = ((Number) rawRequest.get("max_tokens")).intValue();
            req.setMaxTokens(Math.min(val, MAX_TOKENS_UPPER_BOUND));
        }
        if (rawRequest.get("top_p") != null) {
            req.setTopP(((Number) rawRequest.get("top_p")).doubleValue());
        }
        req.setTools((List<Map<String, Object>>) rawRequest.get("tools"));
        req.setStream(Boolean.TRUE.equals(rawRequest.get("stream")));
        // 将未映射的参数保存到 extraParams 中
        Map<String, Object> extra = new LinkedHashMap<>(rawRequest);
        extra.remove("model");
        extra.remove("messages");
        if (system instanceof String) {
            extra.remove("system");  // 已捕获为 systemPrompt，从 extra 移除
        }
        extra.remove("temperature");
        extra.remove("max_tokens");
        extra.remove("top_p");
        extra.remove("tools");
        extra.remove("stream");
        req.setExtraParams(extra);
        req.setInboundProtocol("anthropic");
        return req;
    }

    @Override
    public boolean supports(String protocol) {
        return "anthropic".equalsIgnoreCase(protocol);
    }

    @Override
    public Map<String, Object> buildUpstreamRequest(UnifiedRequest request, String upstreamProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());

        // 将统一消息历史（OpenAI 风格）归一化为 Anthropic 协议格式
        List<Map<String, Object>> normalizedMessages = new java.util.ArrayList<>();
        List<String> inlineSystemParts = new java.util.ArrayList<>();
        normalizeMessages(request.getMessages(), normalizedMessages, inlineSystemParts);
        body.put("messages", normalizedMessages);

        // system prompt 作为独立字段发送（Anthropic 协议特有），
        // 并合并消息历史中内联的 system 角色内容（若有）
        String system = request.getSystemPrompt();
        if (!inlineSystemParts.isEmpty()) {
            String joined = String.join("\n\n", inlineSystemParts);
            system = (system == null || system.isBlank()) ? joined : system + "\n\n" + joined;
        }
        if (system != null) body.put("system", system);
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getTools() != null) {
            List<Map<String, Object>> anthropicTools = new java.util.ArrayList<>();
            for (Map<String, Object> tool : request.getTools()) {
                if (tool == null) continue;
                // 如果是 OpenAI 格式 (type=function 且包含 function 对象)
                if ("function".equalsIgnoreCase((String) tool.get("type")) && tool.get("function") instanceof Map<?, ?> fn) {
                    Map<String, Object> anthropicTool = new LinkedHashMap<>();
                    if (fn.get("name") != null) {
                        anthropicTool.put("name", fn.get("name"));
                    }
                    if (fn.get("description") != null) {
                        anthropicTool.put("description", fn.get("description"));
                    }
                    if (fn.get("parameters") != null) {
                        anthropicTool.put("input_schema", fn.get("parameters"));
                    }
                    anthropicTools.add(anthropicTool);
                } else {
                    // 已是 Anthropic 标准格式 (包含 name 和 input_schema) 或自定义格式，直接保留
                    anthropicTools.add(tool);
                }
            }
            body.put("tools", anthropicTools);
        }
        body.put("stream", Boolean.TRUE.equals(request.getStream()));
        // 合并额外参数
        if (request.getExtraParams() != null) body.putAll(request.getExtraParams());
        return body;
    }

    /**
     * 将统一消息历史（通常为 OpenAI 风格）归一化为 Anthropic 协议消息格式：
     * <ol>
     *   <li>role=system 的内联消息 -> 提取合并至独立 system 字段</li>
     *   <li>assistant 消息中的 tool_calls 数组 -> content 内容块 [{type: tool_use, id, name, input}]</li>
     *   <li>role=tool 的工具结果消息 -> role=user 消息中的 [{type: tool_result, tool_use_id, content}]</li>
     *   <li>其余消息原样保留（user / assistant 文本消息 Anthropic 均接受字符串 content）</li>
     * </ol>
     */
    private void normalizeMessages(List<Map<String, Object>> messages,
                                   List<Map<String, Object>> out,
                                   List<String> systemParts) {
        if (messages == null) return;
        for (Map<String, Object> msg : messages) {
            if (msg == null) continue;
            String role = msg.get("role") instanceof String r ? r : "";

            // 1. 内联 system 消息 -> 提取为独立 system 字段
            if ("system".equalsIgnoreCase(role)) {
                String text = flattenTextContent(msg.get("content"));
                if (!text.isBlank()) systemParts.add(text);
                continue;
            }

            // 3. 工具结果消息 -> user 消息中的 tool_result 内容块
            if ("tool".equalsIgnoreCase(role) || msg.get("tool_call_id") != null) {
                Map<String, Object> toolResultMsg = new LinkedHashMap<>();
                toolResultMsg.put("role", "user");
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", msg.get("tool_call_id"));
                block.put("content", msg.get("content"));
                toolResultMsg.put("content", List.of(block));
                out.add(toolResultMsg);
                continue;
            }

            // 2. assistant 消息：tool_calls 数组 -> tool_use 内容块
            if ("assistant".equalsIgnoreCase(role)
                    && msg.get("tool_calls") instanceof List<?> toolCalls && !toolCalls.isEmpty()) {
                Map<String, Object> assistant = new LinkedHashMap<>();
                assistant.put("role", "assistant");
                List<Object> blocks = new java.util.ArrayList<>();
                String text = flattenTextContent(msg.get("content"));
                if (!text.isBlank()) {
                    Map<String, Object> textBlock = new LinkedHashMap<>();
                    textBlock.put("type", "text");
                    textBlock.put("text", text);
                    blocks.add(textBlock);
                }
                for (Object tcObj : toolCalls) {
                    if (!(tcObj instanceof Map<?, ?> tc)) continue;
                    if (!(tc.get("function") instanceof Map<?, ?> fn)) continue;
                    Map<String, Object> toolUse = new LinkedHashMap<>();
                    toolUse.put("type", "tool_use");
                    toolUse.put("id", tc.get("id"));
                    toolUse.put("name", fn.get("name"));
                    toolUse.put("input", parseToolArguments(fn.get("arguments")));
                    blocks.add(toolUse);
                }
                assistant.put("content", blocks);
                out.add(assistant);
                continue;
            }

            // 4. 其余消息原样保留
            out.add(msg);
        }
    }

    /**
     * 将消息 content 归并为纯文本（兼容字符串与 OpenAI 结构化 content 数组）。
     */
    private String flattenTextContent(Object content) {
        if (content instanceof String s) return s;
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> p && "text".equals(p.get("type"))) {
                    sb.append(p.get("text"));
                }
            }
            return sb.toString();
        }
        return content != null ? String.valueOf(content) : "";
    }

    /**
     * 将 tool_calls.function.arguments 解析为 Anthropic tool_use 所需的 input 对象。
     * 非法 JSON 时退化为空对象，避免上游 400。
     */
    private Object parseToolArguments(Object arguments) {
        if (arguments instanceof Map<?, ?> m) return m;
        if (arguments instanceof String s && !s.isBlank()) {
            try {
                return JsonUtils.MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return new LinkedHashMap<String, Object>();
            }
        }
        return new LinkedHashMap<String, Object>();
    }
}
