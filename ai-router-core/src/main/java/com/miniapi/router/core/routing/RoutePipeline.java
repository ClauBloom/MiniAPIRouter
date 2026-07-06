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

@Component
public class RoutePipeline {

    private static final Logger log = LoggerFactory.getLogger(RoutePipeline.class);

    private final RouteRuleRepository routeRuleRepository;
    private final ApiKeyConfigRepository apiKeyConfigRepository;
    private final HealthChecker healthChecker;
    private final IntentEvaluator intentEvaluator;
    private final com.miniapi.router.core.spi.IntentCatalogProvider intentCatalogProvider;
    private final FailureTracker failureTracker;
    private final SessionRouteMemory sessionRouteMemory;
    private final Map<String, RouteStrategy> strategies;

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
        this.strategies = Map.of(
                "weight", weightStrategy,
                "priority", priorityStrategy,
                "round_robin", roundRobinStrategy,
                "least_conn", leastConnStrategy
        );
    }

    public RouteResult route(RouteContext ctx) {
        Long tenantId = ctx.getTenantId();
        String model = ctx.getModel();

        List<RouteRule> rules = routeRuleRepository.findEnabledRules(tenantId);
        RouteRule matched = matchRule(rules, model);
        if (matched == null) {
            throw new RouterException("NO_ROUTE_MATCHED", "无匹配路由规则，模型: " + model, 404);
        }
        ctx.setMatchedRule(matched);

        List<ApiKeyConfig> allKeys;
        if (matched.getTargetKeyIds() == null || matched.getTargetKeyIds().isEmpty()) {
            allKeys = apiKeyConfigRepository.findByTenantId(ctx.getTenantId());
            log.info("[Route] Rule '{}' has empty target_key_ids, using all {} keys for tenant", 
                    matched.getRuleName(), allKeys.size());
        } else {
            allKeys = apiKeyConfigRepository.findByIds(matched.getTargetKeyIds());
        }
        List<ApiKeyConfig> candidates = allKeys.stream()
                .filter(ApiKeyConfig::isEnabled)
                .filter(k -> !"down".equalsIgnoreCase(k.getHealthStatus()))
                .filter(k -> supportsModel(k, model))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new RouterException("NO_AVAILABLE_UPSTREAM", "无可用上游", 503);
        }

        if ("intent".equalsIgnoreCase(matched.getMatchType()) && matched.getIntentModel() != null) {
            String sessionKey = ctx.getClientIp() != null ? ctx.getClientIp() : "unknown";

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

            IntentWeightResult iwr = resolveIntentWeights(matched, candidates, ctx);
            if (iwr != null && iwr.intent != null) {
                ctx.setIntent(iwr.intent);
                com.miniapi.router.core.domain.IntentConfig ic =
                        intentCatalogProvider.findByLabel(ctx.getTenantId(), iwr.intent);
                Map<String, Integer> kw = ic != null ? ic.getKeyWeights() : null;

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

    private ApiKeyConfig selectByScore(List<ApiKeyConfig> candidates, int score, Map<String, Integer> keyWeights) {
        if (candidates == null || candidates.isEmpty()) return null;
        List<ApiKeyConfig> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(k -> getEffectiveWeight(k, keyWeights)))
                .collect(Collectors.toList());
        int bestWeight = -1;
        List<ApiKeyConfig> ties = new ArrayList<>();
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
            return ties.size() == 1 ? ties.get(0) : ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
        }
        return sorted.get(0);
    }

    private int getEffectiveWeight(ApiKeyConfig k, Map<String, Integer> keyWeights) {
        if (keyWeights != null && keyWeights.containsKey(String.valueOf(k.getId()))) {
            Integer w = keyWeights.get(String.valueOf(k.getId()));
            if (w != null && w > 0) {
                return w;
            }
        }
        Integer kw = k.getWeight();
        return kw != null && kw > 0 ? kw : 1;
    }

    private RouteResult buildResult(RouteRule matched, ApiKeyConfig selected, List<ApiKeyConfig> candidates, String intent, String strategyName) {
        List<ApiKeyConfig> fallbackChain = new ArrayList<>();
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

    private IntentWeightResult resolveIntentWeights(RouteRule rule, List<ApiKeyConfig> candidates,
                                                     RouteContext ctx) {
        List<ApiKeyConfig> allEnabledKeys = apiKeyConfigRepository.findByTenantId(ctx.getTenantId()).stream()
                .filter(ApiKeyConfig::isEnabled)
                .filter(k -> !"down".equalsIgnoreCase(k.getHealthStatus()))
                .collect(Collectors.toList());
        ApiKeyConfig evalKey = intentEvaluator.findEvalKey(allEnabledKeys, rule.getIntentModel());
        if (evalKey == null) {
            log.warn("[Route] No suitable key for intent model {}", rule.getIntentModel());
            return null;
        }

        IntentResult intentResult = intentEvaluator.evaluate(
                candidates, ctx.getMessages(), rule.getIntentModel(), evalKey, ctx.getTenantId());

        if (intentResult == null) {
            return null;
        }

        if (intentResult.isSpecialIntent()) {
            IntentWeightResult result = new IntentWeightResult();
            result.specialIntent = true;
            result.score = intentResult.getScore();
            result.reasoning = intentResult.getReasoning();
            return result;
        }

        IntentWeightResult result = new IntentWeightResult();
        result.intent = intentResult.getIntent();
        result.score = intentResult.getScore();
        result.reasoning = intentResult.getReasoning();
        return result;
    }

    private static class IntentWeightResult {
        String intent;
        int score;
        String reasoning;
        boolean specialIntent;
    }

    private RouteRule matchRule(List<RouteRule> rules, String model) {
        if (rules == null || rules.isEmpty()) return null;
        List<RouteRule> sorted = rules.stream()
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 0))
                .collect(Collectors.toList());
        for (RouteRule rule : sorted) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            String matchType = rule.getMatchType();
            if (matchType == null) continue;
            if ("model".equalsIgnoreCase(matchType) || "intent".equalsIgnoreCase(matchType)) {
                if (matchGlob(rule.getMatchPattern(), model)) return rule;
            } else if ("regex".equalsIgnoreCase(matchType)) {
                if (model.matches(rule.getMatchPattern())) return rule;
            }
        }
        for (RouteRule rule : sorted) {
            if ("model".equalsIgnoreCase(rule.getMatchType()) && "*".equals(rule.getMatchPattern())) {
                return rule;
            }
        }
        return null;
    }

    private boolean matchGlob(String pattern, String text) {
        if (pattern == null || pattern.isEmpty()) return false;
        if ("*".equals(pattern) || "*/*".equals(pattern)) return true;
        if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equalsIgnoreCase(text);
    }

    private boolean supportsModel(ApiKeyConfig key, String model) {
        if (key.getModels() == null || key.getModels().isEmpty()) {
            return true;
        }
        if (model == null || model.isEmpty() || "auto".equalsIgnoreCase(model)) {
            return true;
        }
        return key.getModels().stream()
                .anyMatch(m -> matchGlob(m, model));
    }
}
