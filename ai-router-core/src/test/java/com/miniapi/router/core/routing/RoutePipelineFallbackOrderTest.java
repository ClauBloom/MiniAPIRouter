package com.miniapi.router.core.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteContext;
import com.miniapi.router.core.domain.RouteResult;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.intent.IntentEvaluator;
import com.miniapi.router.core.intent.IntentResult;
import com.miniapi.router.core.routing.strategy.PriorityStrategy;
import com.miniapi.router.core.routing.strategy.RouteStrategy;
import com.miniapi.router.core.routing.strategy.RouteStrategyRegistry;
import com.miniapi.router.core.routing.strategy.WeightStrategy;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutePipelineFallbackOrderTest {

    @Test
    void ordersFallbackByPriorityBeforeApplyingLimitAndPreservesTierOrder() {
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        ApiKeyConfigRepository keys = mock(ApiKeyConfigRepository.class);
        ModelConfigRepository models = mock(ModelConfigRepository.class);
        RouteRule rule = ruleWithPriorityFallback(2);
        List<ApiKeyConfig> candidates = List.of(
                key(1L, -10, "selected"),
                key(2L, 30, "low"),
                key(3L, 10, "mid-a"),
                key(4L, 10, "mid-b"));

        when(rules.findEnabledRules(1L)).thenReturn(List.of(rule));
        when(keys.findByTenantId(1L)).thenReturn(candidates);

        RoutePipeline pipeline = new RoutePipeline(
                rules, keys, mock(IntentEvaluator.class), mock(IntentCatalogProvider.class),
                new FailureTracker(), new SessionRouteMemory(), models,
                new RouteStrategyRegistry(List.of(new WeightStrategy(), new PriorityStrategy())),
                new UpstreamCooldownTracker());

        RouteResult result = pipeline.route(RouteContext.builder()
                .tenantId(1L)
                .model("unknown-model")
                .build());

        assertThat(result.getSelectedKey().getId()).isEqualTo(1L);
        assertThat(result.getFallbackChain())
                .extracting(target -> target.key().getId())
                .containsExactly(3L, 4L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordersNullPriorityFallbackForNonPriorityPrimaryStrategy() {
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        ApiKeyConfigRepository keys = mock(ApiKeyConfigRepository.class);
        ModelConfigRepository models = mock(ModelConfigRepository.class);
        RouteRule rule = ruleWithPriorityFallback(2);
        rule.setStrategy("weight");
        List<ApiKeyConfig> candidates = List.of(
                key(1L, 50, "selected"),
                key(2L, 30, "low"),
                key(3L, null, "implicit-top"),
                key(4L, 10, "mid"));
        RouteStrategy fixedWeight = mock(RouteStrategy.class);

        when(fixedWeight.name()).thenReturn("weight");
        when(fixedWeight.select(anyList())).thenAnswer(invocation ->
                ((List<ApiKeyConfig>) invocation.getArgument(0)).getFirst());
        when(rules.findEnabledRules(1L)).thenReturn(List.of(rule));
        when(keys.findByTenantId(1L)).thenReturn(candidates);

        RoutePipeline pipeline = new RoutePipeline(
                rules, keys, mock(IntentEvaluator.class), mock(IntentCatalogProvider.class),
                new FailureTracker(), new SessionRouteMemory(), models,
                new RouteStrategyRegistry(List.of(fixedWeight)), new UpstreamCooldownTracker());

        RouteResult result = pipeline.route(RouteContext.builder()
                .tenantId(1L)
                .model("unknown-model")
                .build());

        assertThat(result.getSelectedKey().getId()).isEqualTo(1L);
        assertThat(result.getFallbackChain())
                .extracting(target -> target.key().getId())
                .containsExactly(3L, 4L);
    }

    @Test
    void preservesIntentCatalogFallbackOrderRegardlessOfKeyPriority() {
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        ApiKeyConfigRepository keys = mock(ApiKeyConfigRepository.class);
        ModelConfigRepository models = mock(ModelConfigRepository.class);
        IntentEvaluator evaluator = mock(IntentEvaluator.class);
        IntentCatalogProvider catalog = mock(IntentCatalogProvider.class);
        RouteRule rule = ruleWithPriorityFallback(2);
        rule.setMatchType("intent");
        rule.setIntentModel("eval-model");
        ApiKeyConfig selectedKey = key(1L, 50, "selected");
        ApiKeyConfig firstFallbackKey = key(2L, 30, "catalog-first");
        ApiKeyConfig secondFallbackKey = key(3L, 1, "catalog-second");
        List<ApiKeyConfig> candidates = List.of(selectedKey, firstFallbackKey, secondFallbackKey);
        ModelConfig selectedModel = model(1L, "selected", 1L);
        ModelConfig firstFallbackModel = model(2L, "catalog-first", 2L);
        ModelConfig secondFallbackModel = model(3L, "catalog-second", 3L);
        IntentConfig intent = new IntentConfig();
        intent.setTargetModels(List.of("selected", "catalog-first", "catalog-second"));
        intent.setModelWeights(Map.of("selected", 100, "catalog-first", 10, "catalog-second", 20));

        when(rules.findEnabledRules(1L)).thenReturn(List.of(rule));
        when(keys.findByTenantId(1L)).thenReturn(candidates);
        when(keys.findById(1L)).thenReturn(selectedKey);
        when(models.findByDisplayName(1L, "selected")).thenReturn(selectedModel);
        when(models.findByDisplayName(1L, "catalog-first")).thenReturn(firstFallbackModel);
        when(models.findByDisplayName(1L, "catalog-second")).thenReturn(secondFallbackModel);
        when(evaluator.findEvalKey(anyList(), eq("eval-model"))).thenReturn(selectedKey);
        when(evaluator.evaluate(anyList(), anyList(), anyList(), eq("eval-model"),
                eq(selectedKey), eq(1L), isNull()))
                .thenReturn(IntentResult.builder().intent("code").score(100).build());
        when(catalog.findByLabel(1L, "code")).thenReturn(intent);

        RoutePipeline pipeline = new RoutePipeline(
                rules, keys, evaluator, catalog, new FailureTracker(), new SessionRouteMemory(), models,
                new RouteStrategyRegistry(List.of(new WeightStrategy(), new PriorityStrategy())),
                new UpstreamCooldownTracker());

        RouteResult result = pipeline.route(RouteContext.builder()
                .tenantId(1L)
                .model("unknown-model")
                .clientIp("127.0.0.1")
                .messages(List.of(Map.of("role", "user", "content", "write code")))
                .tools(List.of())
                .build());

        assertThat(result.getSelectedKey().getId()).isEqualTo(1L);
        assertThat(result.getFallbackChain())
                .extracting(target -> target.key().getId())
                .containsExactly(2L, 3L);
    }

    private RouteRule ruleWithPriorityFallback(int maxFallback) {
        RouteRule rule = new RouteRule();
        rule.setId(1L);
        rule.setTenantId(1L);
        rule.setRuleName("priority fallback");
        rule.setMatchType("model");
        rule.setMatchPattern("*");
        rule.setStrategy("priority");
        rule.setFallbackEnabled(true);
        rule.setMaxFallback(maxFallback);
        rule.setPriority(0);
        rule.setEnabled(true);
        return rule;
    }

    private ApiKeyConfig key(Long id, Integer priority, String model) {
        ApiKeyConfig key = new ApiKeyConfig();
        key.setId(id);
        key.setTenantId(1L);
        key.setName(model);
        key.setStatus(1);
        key.setHealthStatus("healthy");
        key.setPriority(priority);
        key.setModelMapping(Map.of(model, model + "-real"));
        return key;
    }

    private ModelConfig model(Long id, String displayName, Long apiKeyId) {
        ModelConfig model = new ModelConfig();
        model.setId(id);
        model.setTenantId(1L);
        model.setDisplayName(displayName);
        model.setRealName(displayName + "-real");
        model.setApiKeyId(apiKeyId);
        return model;
    }
}
