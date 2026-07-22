package com.miniapi.router.core.protocol.converter.openai;

import com.miniapi.router.core.protocol.ReasoningContentCache;
import com.miniapi.router.core.protocol.UnifiedRequest;
import com.miniapi.router.core.protocol.converter.RequestConverter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OpenAI 协议的请求转换器。
 * <p>
 * 负责将 OpenAI Chat Completions 格式的原始请求转换为内部统一请求 {@link UnifiedRequest}，
 * 以及将统一请求反向构建为上游 OpenAI 格式的请求体。
 * 构建上游请求时会注入缓存的推理内容（reasoning_content）以保持对话一致性。
 */
@Component
public class OpenAIRequestConverter implements RequestConverter {

    private final ReasoningContentCache reasoningCache;

    public OpenAIRequestConverter(ReasoningContentCache reasoningCache) {
        this.reasoningCache = reasoningCache;
    }

    /** max_tokens / max_completion_tokens 安全上限。
     *  设为 128000 以兼容广泛的供应商限制（如 ByteDance/GLM 等），
     *  避免因超过特定模型的 max_tokens 上限而导致上游 400 错误。 */
    private static final int MAX_TOKENS_UPPER_BOUND = 128000;

    @Override
    @SuppressWarnings("unchecked")
    public UnifiedRequest convert(Map<String, Object> rawRequest, String apiKey) {
        UnifiedRequest req = new UnifiedRequest();
        req.setModel((String) rawRequest.get("model"));
        req.setMessages((List<Map<String, Object>>) rawRequest.get("messages"));
        if (rawRequest.get("temperature") != null) {
            req.setTemperature(((Number) rawRequest.get("temperature")).doubleValue());
        }
        // 兼容 max_tokens 和 max_completion_tokens 两种参数名（OpenAI v2+ 使用后者）
        Number maxTokensRaw = (Number) rawRequest.get("max_tokens");
        if (maxTokensRaw == null) {
            maxTokensRaw = (Number) rawRequest.get("max_completion_tokens");
        }
        if (maxTokensRaw != null) {
            int val = maxTokensRaw.intValue();
            // 保护性上限，防止异常大值导致上游 API 报错
            req.setMaxTokens(Math.min(val, MAX_TOKENS_UPPER_BOUND));
        }
        if (rawRequest.get("top_p") != null) {
            req.setTopP(((Number) rawRequest.get("top_p")).doubleValue());
        }
        req.setTools((List<Map<String, Object>>) rawRequest.get("tools"));
        req.setStream(Boolean.TRUE.equals(rawRequest.get("stream")));
        // 将未显式映射的参数保存到 extraParams 中
        Map<String, Object> extra = new LinkedHashMap<>(rawRequest);
        extra.remove("model");
        extra.remove("messages");
        extra.remove("temperature");
        extra.remove("max_tokens");
        extra.remove("max_completion_tokens");
        extra.remove("top_p");
        extra.remove("tools");
        extra.remove("stream");
        req.setExtraParams(extra);
        req.setInboundProtocol("openai");
        return req;
    }

    @Override
    public boolean supports(String protocol) {
        return "openai".equalsIgnoreCase(protocol);
    }

    @Override
    public Map<String, Object> buildUpstreamRequest(UnifiedRequest request, String upstreamProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        // 注入缓存的推理内容，确保推理链的连续性
        body.put("messages", injectReasoningContent(request.getMessages()));
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) {
            // 上游使用 max_completion_tokens（OpenAI v2+ 推荐参数名）时转发对应名称，
            // 否则使用传统 max_tokens
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null) body.put("top_p", request.getTopP());
        if (request.getTools() != null) body.put("tools", request.getTools());
        body.put("stream", Boolean.TRUE.equals(request.getStream()));
        // 合并额外参数（客户端可传 thinking 等参数），但排除已显式处理的字段防止意外覆盖
        if (request.getExtraParams() != null) {
            Map<String, Object> safeExtra = new LinkedHashMap<>(request.getExtraParams());
            safeExtra.remove("max_tokens");
            safeExtra.remove("max_completion_tokens");
            body.putAll(safeExtra);
        }
        // 当 assistant 消息缺少 reasoning_content 时禁用 thinking 模式，
        // 避免 DeepSeek 等推理模型报错 "reasoning_content must be passed back"
        disableThinkingIfReasoningIncomplete(body);
        return body;
    }

    /**
     * 确保对话历史中 reasoning_content 的完整性，避免 DeepSeek 等推理模型报错。
     * <p>
     * 策略：若部分 assistant 消息有 reasoning_content 而部分没有，则尝试从缓存补齐。
     * 缓存无法补齐时保留已有 reasoning_content，
     * 由 {@link #disableThinkingIfReasoningIncomplete} 禁用 thinking 模式。
     * </p>
     *
     * @param messages 原始消息列表
     * @return 处理后的消息列表
     */
    private List<Map<String, Object>> injectReasoningContent(List<Map<String, Object>> messages) {
        if (messages == null) return null;

        boolean anyHas = false;
        boolean anyMissing = false;
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role"))) {
                if (msg.containsKey("reasoning_content")) anyHas = true;
                else anyMissing = true;
            }
        }
        if (!anyHas) return messages;
        if (!anyMissing) return messages;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role")) && !msg.containsKey("reasoning_content")) {
                Object contentObj = msg.get("content");
                String contentKey = contentObj instanceof String s ? s : null;
                String reasoning = reasoningCache.lookup(contentKey);
                if (reasoning != null) {
                    Map<String, Object> enriched = new LinkedHashMap<>(msg);
                    enriched.put("reasoning_content", reasoning);
                    result.add(enriched);
                } else {
                    result.add(msg);
                }
            } else {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 当任意 assistant 消息缺少 reasoning_content 时，显式禁用 thinking 模式。
     * <p>
     * DeepSeek 等推理模型要求：若 thinking 模式开启，所有 assistant 消息必须包含 reasoning_content。
     * 缺少时会报 400 错误："The reasoning_content in the thinking mode must be passed back to the API"。
     * 因此当无法保证 reasoning_content 完整时，必须禁用 thinking 模式。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void disableThinkingIfReasoningIncomplete(Map<String, Object> body) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        if (messages == null) return;
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role")) && !msg.containsKey("reasoning_content")) {
                body.put("thinking", Map.of("type", "disabled"));
                return;
            }
        }
    }
}
