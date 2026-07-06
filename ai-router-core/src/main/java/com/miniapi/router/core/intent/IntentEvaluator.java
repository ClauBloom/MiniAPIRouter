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

@Component
public class IntentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(IntentEvaluator.class);
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL);
    private static final int INTENT_TIMEOUT_MS = 5000;
    private static final int INTENT_MAX_TOKENS = 3000;

    private final Cache<String, IntentResult> intentCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    private final UpstreamStreamClient upstreamClient;
    private final PromptTemplate promptTemplate;
    private final IntentCatalogProvider catalogProvider;

    public IntentEvaluator(UpstreamStreamClient upstreamClient, PromptTemplate promptTemplate,
                           IntentCatalogProvider catalogProvider) {
        this.upstreamClient = upstreamClient;
        this.promptTemplate = promptTemplate;
        this.catalogProvider = catalogProvider;
    }

    private static final Set<String> SPECIAL_INTENTS = Set.of("invalid_continuation", "follow_up");

    public IntentResult evaluate(List<ApiKeyConfig> candidates, List<Map<String, Object>> messages,
                                  String intentModel, ApiKeyConfig evalKey, Long tenantId) {
        if (candidates == null || candidates.isEmpty() || messages == null || messages.isEmpty()) {
            return null;
        }
        if (intentModel == null || intentModel.isBlank() || evalKey == null) {
            return null;
        }

        String userQuestion = promptTemplate.extractUserQuestion(messages);
        if (userQuestion.isBlank()) {
            log.warn("[IntentEval] Empty user question, skipping intent evaluation");
            return null;
        }

        String agentActivity = promptTemplate.extractAgentActivitySummary(messages);

        List<IntentConfig> catalog = catalogProvider.findAll(tenantId);

        String cacheKey = agentActivity.isBlank() ? userQuestion
                : userQuestion + "|" + agentActivity.substring(0, Math.min(100, agentActivity.length()));
        log.info("[IntentEval] question='{}' agentActivity='{}' cacheKey='{}'",
                userQuestion.substring(0, Math.min(60, userQuestion.length())),
                agentActivity.isBlank() ? "(none)" : agentActivity,
                cacheKey.substring(0, Math.min(120, cacheKey.length())));
        IntentResult cached = intentCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info("[IntentEval] Cache hit for key '{}', skipping eval",
                    cacheKey.substring(0, Math.min(80, cacheKey.length())));
            return cached;
        }

        String systemPrompt = promptTemplate.buildSystemPrompt(catalog);
        String userPrompt = promptTemplate.buildUserPrompt(candidates, userQuestion, agentActivity);

        log.debug("[IntentEval] ===== Prompt (Intent Classification) =====");
        log.debug("[IntentEval] model={}", intentModel);
        log.debug("[IntentEval] eval_key_id={}, provider={}", evalKey.getId(), evalKey.getProvider());
        log.debug("[IntentEval] --- system prompt ---\n{}", systemPrompt);
        log.debug("[IntentEval] --- user prompt ---\n{}", userPrompt);
        log.debug("[IntentEval] --- agent activity ---\n{}", agentActivity.isBlank() ? "(none)" : agentActivity);
        log.debug("[IntentEval] ==========================================");

        ApiKeyConfig evalKeyCopy = copyWithTimeout(evalKey, INTENT_TIMEOUT_MS);

        try {
            IntentResult result = callEvaluator(evalKeyCopy, intentModel, systemPrompt, userPrompt, candidates);
            if (result == null) return null;

            if (SPECIAL_INTENTS.contains(result.getIntent())) {
                boolean isAgentActive = !agentActivity.isBlank()
                        && (agentActivity.contains("正在修改代码") || agentActivity.contains("派发了子Agent"));
                if (isAgentActive) {
                    result.setScore(Math.max(60, result.getScore()));
                    if (result.getIntent() != null) {
                        intentCache.put(cacheKey, result);
                    }
                    log.info("[IntentEval] Special intent '{}' but agent active, score adjusted to {}",
                            result.getIntent(), result.getScore());
                    return result;
                }
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

    public ApiKeyConfig findEvalKey(List<ApiKeyConfig> candidates, String intentModel) {
        if (candidates == null || intentModel == null) return null;
        for (ApiKeyConfig key : candidates) {
            if (key.getModels() != null && key.getModels().contains(intentModel)) {
                return key;
            }
        }
        return null;
    }

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

    private String extractJson(String content) {
        content = content.trim();
        if (content.startsWith("{") && content.endsWith("}")) {
            return content;
        }
        String cleaned = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned;
        }
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
