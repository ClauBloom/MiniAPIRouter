package com.miniapi.router.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.streaming.UpstreamStreamClient;
import com.miniapi.router.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图评估器，负责将用户提问归类到最佳匹配的意图标签，并评估问题复杂度。
 * <p>
 * 通过调用外部大模型（意图识别模型）对用户问题进行意图分类和复杂度评分，
 * 结果将被缓存以提升性能。支持特殊意图（如无效继续指令、追问等）的识别与处理。
 */
@Component
public class IntentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(IntentEvaluator.class);
    /** 用于从大模型响应中提取 JSON 块的正则 */
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);
    /** 意图评估请求的超时时间（毫秒） */
    private static final int INTENT_TIMEOUT_MS = 5000;
    /** 意图评估模型的最大输出 token 数 */
    private static final int INTENT_MAX_TOKENS = 3000;

    /** 意图评估结果缓存，30秒过期，最多存储10000条 */
    private final Cache<String, IntentResult> intentCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    /** 上游大模型流式客户端 */
    private final UpstreamStreamClient upstreamClient;
    /** 提示词模板，用于构建意图评估的 system/user 提示 */
    private final PromptTemplate promptTemplate;
    /** 意图目录提供者，获取租户配置的意图分类列表 */
    private final IntentCatalogProvider catalogProvider;

    public IntentEvaluator(UpstreamStreamClient upstreamClient, PromptTemplate promptTemplate,
                           IntentCatalogProvider catalogProvider) {
        this.upstreamClient = upstreamClient;
        this.promptTemplate = promptTemplate;
        this.catalogProvider = catalogProvider;
    }

    /** 特殊意图集合：无效继续指令、追问/不满意，需要走降级/兜底逻辑 */
    private static final Set<String> SPECIAL_INTENTS = Set.of("invalid_continuation", "follow_up");

    /**
     * 执行意图评估，返回最佳匹配的意图结果。
     *
     * @param candidates  候选 API Key 配置列表
     * @param messages    对话消息历史
     * @param intentModel 用于意图识别的模型名称
     * @param evalKey     用于调用意图模型的 API Key 配置
     * @param tenantId    租户 ID
     * @param agentId     Agent 身份标识，用于缓存隔离（可为 null）
     * @return 意图评估结果，包含意图标签、复杂度评分和推理说明
     */
    public IntentResult evaluate(List<ApiKeyConfig> candidates, List<Map<String, Object>> messages,
                                  String intentModel, ApiKeyConfig evalKey, Long tenantId, String agentId) {
        if (candidates == null || candidates.isEmpty() || messages == null || messages.isEmpty()) {
            return null;
        }
        if (intentModel == null || intentModel.isBlank() || evalKey == null) {
            return null;
        }

        // 提取最近一条用户提问
        String userQuestion = promptTemplate.extractUserQuestion(messages);
        if (userQuestion.isBlank()) {
            log.warn("[IntentEval] Empty user question, skipping intent evaluation");
            return null;
        }

        // 提取 Agent 最近活动的摘要信息
        String agentActivity = promptTemplate.extractAgentActivitySummary(messages);

        // 获取当前租户的意图分类目录
        List<IntentConfig> catalog = catalogProvider.findAll(tenantId);

        // 构建缓存键：agentId + 用户问题 + Agent活动摘要（截取前100字符）
        String agentPrefix = (agentId != null && !agentId.isBlank()) ? agentId + "|" : "";
        String cacheKey = agentPrefix + (agentActivity.isBlank() ? userQuestion
                : userQuestion + "|" + agentActivity.substring(0, Math.min(100, agentActivity.length())));
        log.info("[IntentEval] question='{}' agentActivity='{}' cacheKey='{}'",
                userQuestion.substring(0, Math.min(60, userQuestion.length())),
                agentActivity.isBlank() ? "(none)" : agentActivity,
                cacheKey.substring(0, Math.min(120, cacheKey.length())));
        // 检查缓存，若命中则直接返回
        IntentResult cached = intentCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("[IntentEval] Cache hit for key '{}', skipping eval",
                    cacheKey.substring(0, Math.min(80, cacheKey.length())));
            return cached;
        }

        // 构建意图识别模型的 system 提示和 user 提示
        String systemPrompt = promptTemplate.buildSystemPrompt(catalog);
        String userPrompt = promptTemplate.buildUserPrompt(candidates, userQuestion, agentActivity);

        log.debug("[IntentEval] ===== Prompt (Intent Classification) =====");
        log.debug("[IntentEval] model={}", intentModel);
        log.debug("[IntentEval] eval_key_id={}, provider={}", evalKey.getId(), evalKey.getProvider());
        log.debug("[IntentEval] --- system prompt ---\n{}", systemPrompt);
        log.debug("[IntentEval] --- user prompt ---\n{}", userPrompt);
        log.debug("[IntentEval] --- agent activity ---\n{}", agentActivity.isBlank() ? "(none)" : agentActivity);
        log.debug("[IntentEval] ==========================================");

        // 复制 API Key 配置并设置意图评估专用超时
        ApiKeyConfig evalKeyCopy = copyWithTimeout(evalKey, INTENT_TIMEOUT_MS);

        try {
            IntentResult result = callEvaluator(evalKeyCopy, intentModel, systemPrompt, userPrompt, candidates);
            if (result == null) return null;

            // 处理特殊意图：检查 Agent 是否活跃，活跃时适当保底评分
            if (SPECIAL_INTENTS.contains(result.getIntent())) {
                boolean isAgentActive = !agentActivity.isBlank()
                        && agentActivity.contains("正在修改代码");
                if (isAgentActive) {
                    // Agent 正在修改代码时的追问/继续，保底 30 分（确保不低于最便宜模型）
                    result.setScore(Math.max(30, result.getScore()));
                    if (result.getIntent() != null) {
                        intentCache.put(cacheKey, result);
                    }
                    log.info("[IntentEval] Special intent '{}' but agent active, score adjusted to {}",
                            result.getIntent(), result.getScore());
                    return result;
                }
                // Agent 不活跃时标记为特殊意图，走管线降级逻辑
                log.info("[IntentEval] Special intent '{}' detected, marking for pipeline fallback",
                        result.getIntent());
                result.setSpecialIntent(true);
                intentCache.put(cacheKey, result);
                return result;
            }

            if (result.getIntent() != null) {
                intentCache.put(cacheKey, result);
            }

            log.info("[IntentEval] >> Intent={} | score={} | reasoning={} | model={} | key_id={}",
                    result.getIntent(), result.getScore(),
                    result.getReasoning(), intentModel, evalKey.getId());
            return result;
        } catch (Exception e) {
            log.warn("[IntentEval] Intent evaluation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用上游意图评估大模型，发送 system 和 user 提示，解析返回的 JSON 结果。
     */
    private IntentResult callEvaluator(ApiKeyConfig evalKey, String intentModel,
                                        String systemPrompt, String userPrompt,
                                        List<ApiKeyConfig> candidates) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", intentModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("max_tokens", INTENT_MAX_TOKENS);
        body.put("temperature", 0);
        body.put("stream", false);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("response_format", Map.of("type", "json_object"));

        try {
            UpstreamStreamClient.NonStreamResult result = upstreamClient.callUpstream(evalKey, "/v1/chat/completions", body);

            if (result.statusCode() != 200) {
                log.warn("[IntentEval] Intent model returned status={}, body={}",
                        result.statusCode(), result.body() != null ? result.body().substring(0, Math.min(200, result.body().length())) : "");
                return null;
            }

            String content = extractContent(result.body());
            if (content == null || content.isBlank()) {
                log.warn("[IntentEval] Empty content from intent model");
                return null;
            }

            return parseIntentResult(content);
        } catch (Exception e) {
            log.warn("[IntentEval] Evaluator call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从候选列表中查找支持指定意图评估模型的 API Key。
     * 遍历候选列表，返回第一个模型列表包含目标模型的 Key。
     */
    public ApiKeyConfig findEvalKey(List<ApiKeyConfig> candidates, String intentModel) {
        if (candidates == null || intentModel == null) return null;
        for (ApiKeyConfig key : candidates) {
            if (key.getModels() != null && key.getModels().contains(intentModel)) {
                return key;
            }
        }
        return null;
    }

    /**
     * 从 OpenAI 格式的响应 JSON 中提取模型生成的内容文本。
     * 优先提取 choices[0].message.content，若为空则尝试 reasoning_content。
     */
    private String extractContent(String responseBody) {
        try {
            JsonNode root = JsonUtils.parse(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText("");
                if (content.isBlank()) {
                    content = message.path("reasoning_content").asText("");
                }
                return content;
            }
        } catch (Exception e) {
            log.warn("[IntentEval] Failed to extract content: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 解析意图评估模型返回的 JSON 内容，提取 intent、score、reasoning 字段。
     * score 被限制在 1-100 范围内。
     */
    private IntentResult parseIntentResult(String content) {
        String jsonStr = extractJson(content);
        if (jsonStr == null) {
            log.warn("[IntentEval] No JSON found in content: {}", content.substring(0, Math.min(200, content.length())));
            return null;
        }

        try {
            JsonNode root = JsonUtils.parse(jsonStr);
            String intent = root.path("intent").asText("other");
            int score = root.path("score").asInt(50);
            score = Math.max(1, Math.min(100, score));
            String reasoning = root.path("reasoning").asText("");

            return IntentResult.builder()
                    .intent(intent)
                    .score(score)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.warn("[IntentEval] Failed to parse intent JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从大模型响应的文本中提取 JSON 字符串。
     * 先尝试直接作为 JSON 解析，再尝试去除 markdown 代码块标记后解析，
     * 最后使用正则匹配查找花括号包围的 JSON 块。
     */
    private String extractJson(String content) {
        content = content.trim();
        // 内容本身即为完整 JSON
        if (content.startsWith("{") && content.endsWith("}")) {
            return content;
        }
        // 去除 markdown 代码块标记后再次尝试
        String cleaned = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned;
        }
        // 使用正则匹配提取第一个花括号包围的 JSON 块
        Matcher matcher = JSON_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        matcher = JSON_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 创建 ApiKeyConfig 的副本，并覆盖超时时间和重试次数。
     * 用于意图评估时使用独立的超时配置，避免影响原始 Key 配置。
     */
    private ApiKeyConfig copyWithTimeout(ApiKeyConfig original, int timeoutMs) {
        ApiKeyConfig copy = new ApiKeyConfig();
        copy.setId(original.getId());
        copy.setTenantId(original.getTenantId());
        copy.setName(original.getName());
        copy.setProvider(original.getProvider());
        copy.setProtocol(original.getProtocol());
        copy.setApiKey(original.getApiKey());
        copy.setApiKeyEnc(original.getApiKeyEnc());
        copy.setBaseUrl(original.getBaseUrl());
        copy.setModels(original.getModels());
        copy.setWeight(original.getWeight());
        copy.setPriority(original.getPriority());
        copy.setMaxConcurrent(original.getMaxConcurrent());
        copy.setQpsLimit(original.getQpsLimit());
        copy.setTimeoutMs(timeoutMs);
        copy.setRetryCount(0);
        copy.setStatus(original.getStatus());
        copy.setHealthStatus(original.getHealthStatus());
        return copy;
    }
}
