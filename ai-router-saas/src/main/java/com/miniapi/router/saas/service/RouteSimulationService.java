package com.miniapi.router.saas.service;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.routing.RoutePatterns;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.SimulationRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic, no-charge explanation of how a request would be routed. */
@Service
public class RouteSimulationService {
    private final RouteRuleRepository rules;
    private final ApiKeyConfigRepository keys;
    private final ModelConfigRepository models;
    private final IntentCatalogProvider catalog;

    public RouteSimulationService(RouteRuleRepository rules, ApiKeyConfigRepository keys,
                                  ModelConfigRepository models, IntentCatalogProvider catalog) {
        this.rules = rules;
        this.keys = keys;
        this.models = models;
        this.catalog = catalog;
    }

    public Map<String, Object> simulate(SimulationRequest request) {
        Long tenantId = TenantContext.getTenantId();
        List<RouteRule> sorted = rules.findEnabledRules(tenantId).stream()
                .sorted(Comparator.comparingInt(r -> r.getPriority() != null ? r.getPriority() : 0))
                .toList();
        RouteRule matched = matchRule(sorted, request);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, String>> trace = new ArrayList<>();
        trace.add(step("request", request.model() == null ? "*" : request.model(), "complete"));
        if (matched == null) {
            result.put("matched", false);
            result.put("selected_model", null);
            result.put("fallback_order", List.of());
            result.put("trace", trace);
            return result;
        }
        result.put("matched", true);
        result.put("matched_rule_name", matched.getRuleName());
        trace.add(step("rule", matched.getRuleName(), "complete"));

        String intentLabel = request.intent();
        if (intentLabel != null) {
            trace.add(step("intent", intentLabel + (request.complexity() != null ? " / complexity " + request.complexity() : ""), "active"));
            result.put("evaluated_intent", intentLabel);
            IntentConfig config = catalog.findByLabel(tenantId, intentLabel);
            Map<String, Integer> weights = config != null ? config.getModelWeights() : Map.of();
            List<ModelConfig> candidates = modelCandidates(tenantId, matched, config);
            List<ModelConfig> eligible = candidates.stream()
                    .filter(m -> weight(m, weights) > 0).toList();
            ModelConfig selected = selectByWeight(eligible, weights, request.complexity());
            if (selected != null) {
                result.put("selected_model", selected.getDisplayName());
                result.put("fallback_order", eligible.stream()
                        .filter(m -> !m.getDisplayName().equals(selected.getDisplayName()))
                        .map(ModelConfig::getDisplayName).toList());
                trace.add(step("model", selected.getDisplayName(), "complete"));
            } else {
                result.put("selected_model", null);
                result.put("fallback_order", List.of());
                trace.add(step("model", "no eligible model", "failed"));
            }
            result.put("reasons", List.of("intent=" + intentLabel,
                    "complexity=" + (request.complexity() == null ? "n/a" : request.complexity())));
        } else {
            List<ApiKeyConfig> candidates = candidateKeys(tenantId, matched);
            ApiKeyConfig selected = candidates.stream().findFirst().orElse(null);
            result.put("selected_model", selected != null ? firstModel(selected) : null);
            result.put("fallback_order", candidates.stream().skip(1)
                    .map(this::firstModel).filter(Objects::nonNull).toList());
            result.put("reasons", List.of("strategy=" + (matched.getStrategy() == null ? "weight" : matched.getStrategy())));
            trace.add(step("model", result.get("selected_model") == null ? "none" : (String) result.get("selected_model"), "complete"));
        }
        result.put("trace", trace);
        return result;
    }

    private RouteRule matchRule(List<RouteRule> sorted, SimulationRequest request) {
        String model = request.model();
        for (RouteRule rule : sorted) {
            String type = rule.getMatchType();
            if (type == null) continue;
            if ("regex".equalsIgnoreCase(type)) {
                if (model != null && RoutePatterns.matches(rule.getMatchPattern(), model)) return rule;
            } else if (model != null && glob(rule.getMatchPattern(), model)) {
                return rule;
            }
        }
        return sorted.stream().filter(r -> "*".equals(r.getMatchPattern())).findFirst().orElse(null);
    }

    private boolean glob(String pattern, String text) {
        if (pattern == null || pattern.isEmpty()) return false;
        if ("*".equals(pattern) || "*/*".equals(pattern)) return true;
        if (pattern.endsWith("*")) return text.startsWith(pattern.substring(0, pattern.length() - 1));
        return pattern.equalsIgnoreCase(text);
    }

    private List<ModelConfig> modelCandidates(Long tenantId, RouteRule matched, IntentConfig config) {
        List<String> targetModels = config != null ? config.getTargetModels() : List.of();
        List<ModelConfig> all = models.findByTenantId(tenantId);
        if (targetModels == null || targetModels.isEmpty()) return all;
        List<ModelConfig> result = new ArrayList<>();
        for (String name : targetModels) {
            all.stream().filter(m -> name.equals(m.getDisplayName())).findFirst().ifPresent(result::add);
        }
        return result;
    }

    private int weight(ModelConfig m, Map<String, Integer> weights) {
        Integer w = weights.get(m.getDisplayName());
        return w != null ? w : 1;
    }

    private ModelConfig selectByWeight(List<ModelConfig> eligible, Map<String, Integer> weights, Integer complexity) {
        if (eligible.isEmpty()) return null;
        if (complexity == null) {
            return eligible.stream().max(Comparator.comparingInt((ModelConfig m) -> weight(m, weights))
                    .thenComparing(ModelConfig::getDisplayName)).orElse(null);
        }
        ModelConfig best = null;
        int bestWeight = -1;
        for (ModelConfig m : eligible.stream().sorted(Comparator.comparing(ModelConfig::getDisplayName)).toList()) {
            int w = weight(m, weights);
            if (w <= complexity && w > bestWeight) {
                bestWeight = w;
                best = m;
            }
        }
        return best;
    }

    private List<ApiKeyConfig> candidateKeys(Long tenantId, RouteRule matched) {
        if (matched.getTargetKeyIds() == null || matched.getTargetKeyIds().isEmpty()) {
            return keys.findByTenantId(tenantId).stream().filter(ApiKeyConfig::isEnabled).toList();
        }
        return keys.findByIds(matched.getTargetKeyIds()).stream()
                .filter(k -> Objects.equals(k.getTenantId(), tenantId))
                .filter(ApiKeyConfig::isEnabled).toList();
    }

    private String firstModel(ApiKeyConfig key) {
        Map<String, String> mapping = key.getModelMapping();
        return mapping != null && !mapping.isEmpty() ? mapping.keySet().iterator().next() : null;
    }

    private Map<String, String> step(String id, String detail, String status) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("id", id);
        s.put("detail", detail);
        s.put("status", status);
        return s;
    }
}
