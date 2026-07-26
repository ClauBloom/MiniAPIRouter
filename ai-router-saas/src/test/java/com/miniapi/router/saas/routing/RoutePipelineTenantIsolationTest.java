package com.miniapi.router.saas.routing;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.RouteContext;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.core.intent.IntentEvaluator;
import com.miniapi.router.core.routing.FailureTracker;
import com.miniapi.router.core.routing.RoutePipeline;
import com.miniapi.router.core.routing.SessionRouteMemory;
import com.miniapi.router.core.routing.UpstreamCooldownTracker;
import com.miniapi.router.core.routing.strategy.LeastConnStrategy;
import com.miniapi.router.core.routing.strategy.PriorityStrategy;
import com.miniapi.router.core.routing.strategy.RoundRobinStrategy;
import com.miniapi.router.core.routing.strategy.WeightStrategy;
import com.miniapi.router.core.routing.strategy.RouteStrategyRegistry;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutePipelineTenantIsolationTest {

    @Test
    void routeRejectsTargetKeyOwnedByAnotherTenant() {
        RouteRuleRepository ruleRepository = mock(RouteRuleRepository.class);
        ApiKeyConfigRepository keyRepository = mock(ApiKeyConfigRepository.class);
        ModelConfigRepository modelRepository = mock(ModelConfigRepository.class);
        RoutePipeline pipeline = new RoutePipeline(
                ruleRepository,
                keyRepository,
                mock(IntentEvaluator.class),
                mock(IntentCatalogProvider.class),
                mock(FailureTracker.class),
                mock(SessionRouteMemory.class),
                modelRepository,
                new RouteStrategyRegistry(List.of(
                        new WeightStrategy(), new PriorityStrategy(),
                        new RoundRobinStrategy(), new LeastConnStrategy())),
                new UpstreamCooldownTracker());

        RouteRule rule = new RouteRule();
        rule.setId(3L);
        rule.setTenantId(10L);
        rule.setRuleName("default");
        rule.setMatchType("model");
        rule.setMatchPattern("*");
        rule.setTargetKeyIds(List.of(99L));
        rule.setStrategy("priority");
        rule.setEnabled(true);
        ApiKeyConfig foreignKey = new ApiKeyConfig();
        foreignKey.setId(99L);
        foreignKey.setTenantId(20L);
        foreignKey.setStatus(1);
        foreignKey.setHealthStatus("healthy");

        when(ruleRepository.findEnabledRules(10L)).thenReturn(List.of(rule));
        when(keyRepository.findByIds(List.of(99L))).thenReturn(List.of(foreignKey));

        RouteContext context = RouteContext.builder().tenantId(10L).model("model-a").build();
        assertThatThrownBy(() -> pipeline.route(context))
                .isInstanceOfSatisfying(RouterException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("NO_AVAILABLE_UPSTREAM");
                    assertThat(error.getHttpStatus()).isEqualTo(503);
                });
    }
}
