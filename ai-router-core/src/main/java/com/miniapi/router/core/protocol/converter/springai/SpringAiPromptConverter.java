package com.miniapi.router.core.protocol.converter.springai;

import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.openai.OpenAIRequestConverter;
import com.miniapi.router.core.springai.RouterChatOptions;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI → 内部统一格式的入站转换器（理念 A：Spring AI 是第三种协议）。
 * <p>
 * Spring AI 的 {@code Prompt} 与 OpenAI Chat Completions 消息模型同构，
 * 因此落地为：Prompt → OpenAI wire 格式 {@code Map}，再复用现有
 * {@link OpenAIRequestConverter} 进入核心管道。这样审计日志、意图评估、
 * 路由匹配与 HTTP 路径完全一致。
 * <p>
 * 协议名固定为 {@code "spring-ai"}，仅用于日志与观测。
 */
@Component
public class SpringAiPromptConverter {

    /** Spring AI 协议名（用于审计与日志） */
    public static final String PROTOCOL = "spring-ai";

    private final OpenAIRequestConverter openAIRequestConverter;

    public SpringAiPromptConverter(OpenAIRequestConverter openAIRequestConverter) {
        this.openAIRequestConverter = openAIRequestConverter;
    }

    /**
     * 将 Prompt（含合并后的 ChatOptions 与已解析的工具定义）转换为 OpenAI wire 请求体。
     *
     * @param prompt              Spring AI 提示
     * @param options             合并后的运行时选项（可为 null）
     * @param toolDefinitions     已由 ToolCallingManager 解析的工具定义（可为 null/空）
     * @param stream              是否流式
     * @return OpenAI Chat Completions 格式请求体
     */
    public Map<String, Object> toOpenAiBody(Prompt prompt, ChatOptions options,
                                            List<ToolDefinition> toolDefinitions, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (options != null && options.getModel() != null) {
            body.put("model", options.getModel());
        } else {
            /* 未指定模型时交给路由决策（Auto Route 兜底），入站模型名使用占位 "auto" */
            body.put("model", "auto");
        }
        body.put("messages", toOpenAiMessages(prompt.getInstructions()));
        if (options != null) {
            if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
            if (options.getMaxTokens() != null) body.put("max_tokens", options.getMaxTokens());
            if (options.getTopP() != null) body.put("top_p", options.getTopP());
            if (options.getTopK() != null) body.put("top_k", options.getTopK());
            if (options.getFrequencyPenalty() != null) body.put("frequency_penalty", options.getFrequencyPenalty());
            if (options.getPresencePenalty() != null) body.put("presence_penalty", options.getPresencePenalty());
            if (options.getStopSequences() != null && !options.getStopSequences().isEmpty()) {
                body.put("stop", options.getStopSequences());
            }
            if (options instanceof RouterChatOptions routerOptions && routerOptions.getExtraBody() != null) {
                body.putAll(routerOptions.getExtraBody());
            }
        }
        if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
            body.put("tools", toOpenAiTools(toolDefinitions));
        }
        body.put("stream", stream);
        return body;
    }

    /**
     * 复用 OpenAI 请求转换器把 wire 请求体提升为统一请求（供直接使用统一格式的调用方）。
     */
    public UnifiedRequest toUnifiedRequest(Map<String, Object> openAiBody) {
        return openAIRequestConverter.convert(openAiBody, null);
    }

    /**
     * Spring AI 消息 → OpenAI messages 数组。
     */
    public List<Map<String, Object>> toOpenAiMessages(List<Message> instructions) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : instructions) {
            switch (message.getMessageType()) {
                case SYSTEM -> messages.add(textMessage("system", ((SystemMessage) message).getText()));
                case USER -> messages.add(toOpenAiUserMessage((UserMessage) message));
                case ASSISTANT -> messages.add(toOpenAiAssistantMessage((AssistantMessage) message));
                case TOOL -> {
                    ToolResponseMessage toolMessage = (ToolResponseMessage) message;
                    for (ToolResponseMessage.ToolResponse response : toolMessage.getResponses()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("role", "tool");
                        item.put("content", response.responseData() != null ? response.responseData() : "");
                        item.put("tool_call_id", response.id());
                        messages.add(item);
                    }
                }
                default -> messages.add(textMessage("user", message.getText()));
            }
        }
        return messages;
    }

    private Map<String, Object> toOpenAiUserMessage(UserMessage message) {
        String text = message.getText();
        if (message.getMedia() == null || message.getMedia().isEmpty()) {
            return textMessage("user", text);
        }
        List<Map<String, Object>> content = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            content.add(Map.of("type", "text", "text", text));
        }
        for (Media media : message.getMedia()) {
            content.add(toMediaPart(media));
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "user");
        item.put("content", content);
        return item;
    }

    /**
     * Spring AI Media → OpenAI 多模态 content 片段。
     * 图片映射为 image_url，音频映射为 input_audio，其余类型尝试按 image_url 处理。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMediaPart(Media media) {
        MimeType mimeType = media.getMimeType();
        boolean audio = mimeType != null && "audio".equalsIgnoreCase(mimeType.getType());
        Map<String, Object> part = new LinkedHashMap<>();
        if (audio) {
            Map<String, Object> inputAudio = new LinkedHashMap<>();
            inputAudio.put("data", toBase64(media.getData()));
            if (mimeType.getSubtype() != null) {
                inputAudio.put("format", mimeType.getSubtype());
            }
            part.put("type", "input_audio");
            part.put("input_audio", inputAudio);
            return part;
        }
        part.put("type", "image_url");
        part.put("image_url", Map.of("url", toMediaUrl(media)));
        return part;
    }

    private String toMediaUrl(Media media) {
        Object data = media.getData();
        if (data instanceof String s) return s;
        if (data instanceof URI uri) return uri.toString();
        if (data instanceof URL url) return url.toString();
        if (data instanceof Resource resource) {
            try (var in = resource.getInputStream()) {
                return "data:" + media.getMimeType() + ";base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to read media resource", e);
            }
        }
        return toBase64(data) != null ? "data:" + media.getMimeType() + ";base64," + toBase64(data) : String.valueOf(data);
    }

    private String toBase64(Object data) {
        if (data instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return null;
    }

    private Map<String, Object> toOpenAiAssistantMessage(AssistantMessage message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "assistant");
        item.put("content", message.getText());
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", toolCall.name());
                fn.put("arguments", toolCall.arguments() != null ? toolCall.arguments() : "{}");
                Map<String, Object> item2 = new LinkedHashMap<>();
                item2.put("id", toolCall.id());
                item2.put("type", toolCall.type() != null ? toolCall.type() : "function");
                item2.put("function", fn);
                toolCalls.add(item2);
            }
            item.put("tool_calls", toolCalls);
        }
        return item;
    }

    private static Map<String, Object> textMessage(String role, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", Objects.requireNonNullElse(text, ""));
        return item;
    }

    /**
     * Spring AI ToolDefinition → OpenAI tools 数组。
     */
    public List<Map<String, Object>> toOpenAiTools(List<ToolDefinition> toolDefinitions) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition definition : toolDefinitions) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", definition.name());
            fn.put("description", definition.description());
            /* inputSchema 是 JSON Schema 字符串，OpenAI 期望 JSON 对象 */
            Object schema;
            try {
                schema = JsonUtils.fromJson(definition.inputSchema(), Map.class);
            } catch (Exception e) {
                schema = definition.inputSchema();
            }
            fn.put("parameters", schema != null ? schema : Map.of());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function");
            item.put("function", fn);
            tools.add(item);
        }
        return tools;
    }

    /**
     * 从 ToolCallingChatOptions 解析工具回调名称集合（用于观测/日志）。
     */
    public List<String> toolNames(ToolCallingChatOptions options) {
        if (options == null || options.getToolNames() == null) return List.of();
        return new ArrayList<>(options.getToolNames());
    }
}
