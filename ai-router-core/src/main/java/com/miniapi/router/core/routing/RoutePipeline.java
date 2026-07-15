package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.*;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.intent.IntentEvaluator;
import com.miniapi.router.core.intent.IntentResult;
import com.miniapi.router.core.routing.strategy.*;
import com.miniapi.router.core.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 路由管道：核心路由编排器，串联规则匹配、健康检查、意图评估、失败回退和策略选择。
 * 负责从请求上下文出发，经过多级过滤和评估，最终确定目标上游 API Key。
 *
 * <p>流程：
 * <ol>
 *   <li>匹配租户的路由规则（model/glob/regex）</li>
 *   <li>根据规则获取候选 Key，过滤启用的、健康的、支持模型的</li>
 *   <li>若为 intent 类型规则，进行 AI 意图评估，按意图权重选择</li>
 *   <li>失败时回退到缓存的上次成功路由</li>
 *   <li>非 intent 规则使用配置的常规策略（weight/round_robin 等）</li>
 * </ol>
 */
@Component
public class RoutePipeline {

    private static final Logger log = LoggerFactory.getLogger(RoutePipeline.class);

    private final RouteRuleRepository routeRuleRepository;
    private final ApiKeyConfigRepository apiKeyConfigRepository;
    private final HealthChecker healthChecker;
    private final IntentEvaluator intentEvaluator;                          // 意图评估器，负责调用 AI 模型分析用户意图
    private final com.miniapi.router.core.spi.IntentCatalogProvider intentCatalogProvider; // 意图目录提供者
    private final FailureTracker failureTracker;                            // 失败追踪器
    private final SessionRouteMemory sessionRouteMemory;                    // 会话路由记忆
    private final Map<String, RouteStrategy> strategies;                    // 策略名称 -> 策略实现 映射

    /**
     * 构造路由管道，注入所有依赖并注册可用策略。
     * Spring 会自动注入所有 @Component 策略实现。
     */
    public RoutePipeline(RouteRuleRepository routeRuleRepository,
                         ApiKeyConfigRepository apiKeyConfigRepository,
                         HealthChecker healthChecker,
                         IntentEvaluator intentEvaluator,
                         com.miniapi.router.core.spi.IntentCatalogProvider intentCatalogProvider,
                         FailureTracker failureTracker,
                         SessionRouteMemory sessionRouteMemory,
                         WeightStrategy weightStrategy,
                         PriorityStrategy priorityStrategy,
                         RoundRobinStrategy roundRobinStrategy,
                         LeastConnStrategy leastConnStrategy) {
        this.routeRuleRepository = routeRuleRepository;
        this.apiKeyConfigRepository = apiKeyConfigRepository;
        this.healthChecker = healthChecker;
        this.intentEvaluator = intentEvaluator;
        this.intentCatalogProvider = intentCatalogProvider;
        this.failureTracker = failureTracker;
        this.sessionRouteMemory = sessionRouteMemory;
        /* 注册所有可用策略 */
        this.strategies = Map.of(
                "weight", weightStrategy,
                "priority", priorityStrategy,
                "round_robin", roundRobinStrategy,
                "least_conn", leastConnStrategy
        );
    }

    /**
     * 核心路由方法：根据请求上下文执行完整路由流程
     * @param ctx 路由上下文，包含租户 ID、模型名称、消息列表等
     * @return 路由结果，包含选中的 Key、匹配的规则、回退链等
     */
    public RouteResult route(RouteContext ctx) {
        Long tenantId = ctx.getTenantId();
        String model = ctx.getModel();

        /* 1. 匹配路由规则 */
        List<RouteRule> rules = routeRuleRepository.findEnabledRules(tenantId);
        RouteRule matched = matchRule(rules, model, ctx);
        if (matched == null) {
            throw new RouterException("NO_ROUTE_MATCHED", "无匹配路由规则，模型: " + model, 404);
        }
        ctx.setMatchedRule(matched);

        /* 2. 获取候选 API Key：若规则未指定目标 Key，则使用租户下所有 Key */
        List<ApiKeyConfig> allKeys;
        if (matched.getTargetKeyIds() == null || matched.getTargetKeyIds().isEmpty()) {
            allKeys = apiKeyConfigRepository.findByTenantId(ctx.getTenantId());
            log.info("[Route] Rule '{}' has empty target_key_ids, using all {} keys for tenant", 
                    matched.getRuleName(), allKeys.size());
        } else {
            allKeys = apiKeyConfigRepository.findByIds(matched.getTargetKeyIds());
        }
        /* 3. 过滤：模型名在数据库中存在时走精确匹配，不存在时全部 Key 为候选（走意图路由） */
        boolean modelKnown = model != null && !model.isEmpty()
                && allKeys.stream().anyMatch(k -> k.getModelMapping() != null && k.getModelMapping().containsKey(model));
        List<ApiKeyConfig> candidates = allKeys.stream()
                .filter(ApiKeyConfig::isEnabled)
                .filter(k -> !"down".equalsIgnoreCase(k.getHealthStatus()))
                .filter(k -> !modelKnown || supportsModel(k, model))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new RouterException("NO_AVAILABLE_UPSTREAM", "无可用上游", 503);
        }

        /* 4. 意图路由分支：若规则匹配类型为 intent，则进行 AI 意图评估 */
        if ("intent".equalsIgnoreCase(matched.getMatchType()) && matched.getIntentModel() != null) {
            /* 以 Agent 身份或客户端 IP 作为会话标识 */
            String sessionKey = buildSessionKey(ctx);

            /* 4a. 失败回退检查：若该会话已达失败阈值，使用缓存路由 */
            if (failureTracker.shouldFallback(sessionKey)) {
                SessionRouteMemory.CachedResult cached = sessionRouteMemory.getLastSuccess(sessionKey);
                if (cached != null) {
                    ApiKeyConfig cachedKey = candidates.stream()
                            .filter(k -> k.getId().equals(cached.keyId()))
                            .findFirst().orElse(null);
                    if (cachedKey != null) {
                        log.info("[Route] ┌─ Cached(failure×{}) ──────────────────────────────",
                                failureTracker.getFailureCount(sessionKey));
                        log.info("[Route] │ ▶ {} (key={})", cachedKey.getName(), cached.keyId());
                        log.info("[Route] └──────────────────────────────────────────────────");
                        failureTracker.resetFailures(sessionKey);
                        return buildResult(matched, cachedKey, candidates, cached.intent(), "intent_cached");
                    }
                }
            }

            /* 4b. 调用意图评估模型分析用户意图，获取意图权重 */
            IntentWeightResult iwr = resolveIntentWeights(matched, candidates, ctx);
            if (iwr != null && iwr.intent != null) {
                ctx.setIntent(iwr.intent);
                com.miniapi.router.core.domain.IntentConfig ic =
                        intentCatalogProvider.findByLabel(ctx.getTenantId(), iwr.intent);
                Map<String, Integer> kw = ic != null ? ic.getKeyWeights() : null;

                /* 根据意图配置缩小候选 Key 范围：优先用 keyWeights，否则用 targetKeyIds */
                if (kw != null && !kw.isEmpty()) {
                    candidates = candidates.stream()
                            .filter(k -> kw.containsKey(String.valueOf(k.getId())))
                            .collect(Collectors.toList());
                } else if (ic != null && ic.getTargetKeyIds() != null && !ic.getTargetKeyIds().isEmpty()) {
                    List<ApiKeyConfig> filtered = candidates.stream()
                            .filter(k -> ic.getTargetKeyIds().contains(k.getId()))
                            .collect(Collectors.toList());
                    if (!filtered.isEmpty()) {
                        candidates = filtered;
                    }
                }

                String models = candidates.stream()
                        .map(k -> String.format("%d:%s(w=%d)", k.getId(), k.getName(), getEffectiveWeight(k, kw)))
                        .collect(Collectors.joining("  "));
                log.info("[Route] ┌─ {}({}) ──────────────────────────────────────────",
                        iwr.intent, iwr.score);
                log.info("[Route] │ {}", models);

                ApiKeyConfig intentSelected = selectByScore(candidates, iwr.score, kw);
                if (intentSelected != null) {
                    log.info("[Route] │ ▶ {} (key={})", intentSelected.getName(), intentSelected.getId());
                    log.info("[Route] └──────────────────────────────────────────────────");
                    failureTracker.resetFailures(sessionKey);
                    sessionRouteMemory.recordSuccess(sessionKey, intentSelected, iwr.intent, iwr.score);
                    return buildResult(matched, intentSelected, candidates, iwr.intent, "intent_score");
                }
            }

            /* 4c. 特殊意图处理：无法精确分类的意图，优先使用缓存 */
            if (iwr != null && iwr.specialIntent) {
                SessionRouteMemory.CachedResult cached = sessionRouteMemory.getLastSuccess(sessionKey);
                if (cached != null) {
                    ApiKeyConfig cachedKey = candidates.stream()
                            .filter(k -> k.getId().equals(cached.keyId()))
                            .findFirst().orElse(null);
                    if (cachedKey != null) {
                        log.info("[Route] ┌─ {} → cached ──────────────────────",
                                iwr.reasoning);
                        log.info("[Route] │ ▶ {} (key={})", cachedKey.getName(), cached.keyId());
                        log.info("[Route] └──────────────────────────────────────────────────");
                        return buildResult(matched, cachedKey, candidates, cached.intent(), "intent_cached");
                    }
                }
                log.info("[Route] ┌─ {} (no cache) ─────────────────────────", iwr.reasoning);
                log.info("[Route] └──────────────────────────────────────────────────");
            } else {
                /* 4d. 意图评估失败：累加失败次数，若达阈值则回退缓存 */
                failureTracker.incrementFailure(sessionKey);
                if (failureTracker.shouldFallback(sessionKey)) {
                    SessionRouteMemory.CachedResult cached = sessionRouteMemory.getLastSuccess(sessionKey);
                    if (cached != null) {
                        ApiKeyConfig cachedKey = candidates.stream()
                                .filter(k -> k.getId().equals(cached.keyId()))
                                .findFirst().orElse(null);
                        if (cachedKey != null) {
                            log.info("[Route] ┌─ Cached(failure×{}) ──────────────────────────────",
                                    failureTracker.getFailureCount(sessionKey));
                            log.info("[Route] │ ▶ {} (key={})", cachedKey.getName(), cached.keyId());
                            log.info("[Route] └──────────────────────────────────────────────────");
                            failureTracker.resetFailures(sessionKey);
                            return buildResult(matched, cachedKey, candidates, cached.intent(), "intent_cached");
                        }
                    }
                }
                log.info("[Route] ┌─ Eval Failed ───────────────────────────────────────");
                log.info("[Route] └──────────────────────────────────────────────────");
            }
        }

        /* 5. 常规策略路由：使用规则配置的策略（默认为 weight） */
        RouteStrategy strategy = strategies.getOrDefault(
                matched.getStrategy() != null ? matched.getStrategy() : "weight",
                strategies.get("weight"));
        ApiKeyConfig selected = strategy.select(candidates);
        if (selected == null) {
            throw new RouterException("NO_AVAILABLE_UPSTREAM", "无可用上游", 503);
        }
        log.info("[Route] ★ strategy='{}' selected: key_id={} name={}",
                strategy.name(), selected.getId(), selected.getName());
        return buildResult(matched, selected, candidates, ctx.getIntent(), strategy.name());
    }

    /**
     * 按评分选择最匹配的 Key：根据意图评分和 Key 权重，选择权重不超过评分且最大的 Key。
     * 若有多个同权重 Key，则随机选取。
     */
    private ApiKeyConfig selectByScore(List<ApiKeyConfig> candidates, int score, Map<String, Integer> keyWeights) {
        if (candidates == null || candidates.isEmpty()) return null;
        /* 按有效权重升序排序 */
        List<ApiKeyConfig> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(k -> getEffectiveWeight(k, keyWeights)))
                .collect(Collectors.toList());
        int bestWeight = -1;
        List<ApiKeyConfig> ties = new ArrayList<>();
        /* 找到权重不超过评分且最大的 Key（最佳匹配） */
        for (ApiKeyConfig k : sorted) {
            int w = getEffectiveWeight(k, keyWeights);
            if (w <= score) {
                if (w > bestWeight) {
                    bestWeight = w;
                    ties.clear();
                    ties.add(k);
                } else if (w == bestWeight) {
                    ties.add(k);
                }
            }
        }
        if (!ties.isEmpty()) {
            /* 若存在并列则随机选择 */
            return ties.size() == 1 ? ties.get(0) : ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
        }
        return sorted.get(0);
    }

    /** 获取 Key 的有效权重：优先使用意图权重映射表中的值，否则默认使用 1 */
    private int getEffectiveWeight(ApiKeyConfig k, Map<String, Integer> keyWeights) {
        if (keyWeights != null && keyWeights.containsKey(String.valueOf(k.getId()))) {
            Integer w = keyWeights.get(String.valueOf(k.getId()));
            if (w != null && w > 0) {
                return w;
            }
        }
        return 1;
    }

    /** 构建路由结果对象，包含选中的 Key、匹配规则、回退链等 */
    private RouteResult buildResult(RouteRule matched, ApiKeyConfig selected, List<ApiKeyConfig> candidates, String intent, String strategyName) {
        List<ApiKeyConfig> fallbackChain = new ArrayList<>();
        /* 若启用回退，则构建除选中 Key 外的候选列表作为回退链，上限 maxFallback 个 */
        if (Boolean.TRUE.equals(matched.getFallbackEnabled())) {
            fallbackChain = candidates.stream()
                    .filter(k -> !k.getId().equals(selected.getId()))
                    .collect(Collectors.toList());
            int maxFallback = matched.getMaxFallback() != null ? matched.getMaxFallback() : 2;
            if (fallbackChain.size() > maxFallback) {
                fallbackChain = fallbackChain.subList(0, maxFallback);
            }
        }
        return RouteResult.builder()
                .selectedKey(selected)
                .matchedRule(matched)
                .fallbackChain(fallbackChain)
                .strategy(strategyName)
                .intent(intent)
                .build();
    }

    /**
     * 解析意图权重：调用意图评估模型分析用户消息，返回意图标签和评分。
     * @return IntentWeightResult 或 null（无可用评估 Key 时）。
     */
    private IntentWeightResult resolveIntentWeights(RouteRule rule, List<ApiKeyConfig> candidates,
                                                      RouteContext ctx) {
        /* 获取租户下所有可用 Key，用于查找适合评估的 Key */
        List<ApiKeyConfig> allEnabledKeys = apiKeyConfigRepository.findByTenantId(ctx.getTenantId()).stream()
                .filter(ApiKeyConfig::isEnabled)
                .filter(k -> !"down".equalsIgnoreCase(k.getHealthStatus()))
                .collect(Collectors.toList());
        ApiKeyConfig evalKey = intentEvaluator.findEvalKey(allEnabledKeys, rule.getIntentModel());
        if (evalKey == null) {
            log.warn("[Route] No suitable key for intent model {}", rule.getIntentModel());
            return null;
        }

        /* 调用意图评估器进行 AI 意图分析 */
        String agentId = ctx.getAgentIdentity() != null ? ctx.getAgentIdentity().getAgentId() : null;
        IntentResult intentResult = intentEvaluator.evaluate(
                candidates, ctx.getMessages(), rule.getIntentModel(), evalKey, ctx.getTenantId(), agentId);

        if (intentResult == null) {
            return null;
        }

        /* 特殊意图：评估模型无法精确分类，标记 specialIntent */
        if (intentResult.isSpecialIntent()) {
            IntentWeightResult result = new IntentWeightResult();
            result.specialIntent = true;
            result.score = intentResult.getScore();
            result.reasoning = intentResult.getReasoning();
            return result;
        }

        /* 正常意图：返回意图标签和评分供后续路由使用 */
        IntentWeightResult result = new IntentWeightResult();
        result.intent = intentResult.getIntent();
        result.score = intentResult.getScore();
        result.reasoning = intentResult.getReasoning();
        return result;
    }

    /** 意图权重结果内部类 */
    private static class IntentWeightResult {
        String intent;          // 意图标签
        int score;              // 意图评分
        String reasoning;       // 评估模型的推理输出
        boolean specialIntent;  // 是否为无法精确分类的特殊意图
    }

    /**
     * 构建复合会话键：优先使用 Agent 身份，无身份时回退到客户端 IP。
     * 格式为 {@code tenantId:agentId} 或 {@code tenantId:clientIp}。
     *
     * @param ctx 路由上下文
     * @return 复合会话键
     */
    private String buildSessionKey(RouteContext ctx) {
        AgentIdentity identity = ctx.getAgentIdentity();
        if (identity != null && identity.hasIdentity()) {
            return ctx.getTenantId() + ":" + identity.toSessionKey();
        }
        String ip = ctx.getClientIp();
        return ip != null ? ctx.getTenantId() + ":" + ip : "unknown";
    }

    /**
     * 匹配路由规则：按优先级排序后，依次尝试 glob/模式匹配或正则匹配。
     * 支持可选的 Agent 类型过滤：若规则配置了 agentType，则请求的 Agent 类型必须匹配。
     * 若没有精确匹配，则回退到通配符 "*" 规则。
     *
     * @param rules  可用路由规则列表
     * @param model  请求的模型名称
     * @param ctx    路由上下文（用于获取 Agent 身份进行类型匹配）
     * @return 匹配的规则，无匹配时返回 null
     */
    private RouteRule matchRule(List<RouteRule> rules, String model, RouteContext ctx) {
        if (rules == null || rules.isEmpty()) return null;
        /* 按优先级升序排序（值越小优先级越高） */
        List<RouteRule> sorted = rules.stream()
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 0))
                .collect(Collectors.toList());
        /* 第一轮：精确匹配（含 Agent 类型过滤） */
        for (RouteRule rule : sorted) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            String matchType = rule.getMatchType();
            if (matchType == null) continue;
            /* Agent 类型过滤：规则配置了 agentType 时，请求的 Agent 类型必须匹配 */
            if (rule.getAgentType() != null && !rule.getAgentType().isBlank()) {
                String agentType = ctx.getAgentIdentity() != null
                        ? ctx.getAgentIdentity().getAgentType() : null;
                if (agentType == null || !matchGlob(rule.getAgentType(), agentType)) {
                    continue;
                }
            }
            if ("model".equalsIgnoreCase(matchType) || "intent".equalsIgnoreCase(matchType)) {
                if (matchGlob(rule.getMatchPattern(), model)) return rule;
            } else if ("regex".equalsIgnoreCase(matchType)) {
                if (model.matches(rule.getMatchPattern())) return rule;
            }
        }
        /* 第二轮：通配符回退 */
        for (RouteRule rule : sorted) {
            if ("model".equalsIgnoreCase(rule.getMatchType()) && "*".equals(rule.getMatchPattern())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Glob 模式匹配：支持通配符 * 前缀匹配和精确匹配。
     * "*" 或 "*\/*" 匹配任意模型；"gpt-*" 匹配以 "gpt-" 开头的模型。
     */
    private boolean matchGlob(String pattern, String text) {
        if (pattern == null || pattern.isEmpty()) return false;
        if ("*".equals(pattern) || "*/*".equals(pattern)) return true;
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equalsIgnoreCase(text);
    }

    /**
     * 判断指定 Key 是否支持请求的模型。
     * 若 Key 未配置模型映射，则支持所有模型；若模型为空，也视为匹配。
     */
    private boolean supportsModel(ApiKeyConfig key, String model) {
        Map<String, String> mm = key.getModelMapping();
        if (mm == null || mm.isEmpty()) {
            return true;
        }
        if (model == null || model.isEmpty()) {
            return true;
        }
        return mm.containsKey(model);
    }
}
