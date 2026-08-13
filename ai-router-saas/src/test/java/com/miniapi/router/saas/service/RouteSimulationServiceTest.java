package com.miniapi.router.saas.service;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.IntentCatalogProvider;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.SimulationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteSimulationServiceTest {
    private RouteRuleRepository rules;
    private ApiKeyConfigRepository keys;
    private ModelConfigRepository models;
    private IntentCatalogProvider catalog;
    private RouteSimulationService service;

    @BeforeEach void setUp() {
        rules=mock(RouteRuleRepository.class);keys=mock(ApiKeyConfigRepository.class);
        models=mock(ModelConfigRepository.class);catalog=mock(IntentCatalogProvider.class);
        service=new RouteSimulationService(rules,keys,models,catalog);
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void deterministicallyExplainsIntentRoutingWithoutPaidEvaluation() {
        RouteRule rule=rule(1L,"Code tasks","intent","*",List.of(7L));
        when(rules.findEnabledRules(10L)).thenReturn(List.of(rule));
        ApiKeyConfig key=new ApiKeyConfig();key.setId(7L);key.setTenantId(10L);key.setStatus(1);key.setName("primary");
        when(keys.findByIds(List.of(7L))).thenReturn(List.of(key));
        IntentConfig intent=new IntentConfig();intent.setLabel("coding_review");
        intent.setTargetModels(List.of("qwen-max","glm-5"));intent.setModelWeights(Map.of("qwen-max",80,"glm-5",40));
        when(catalog.findByLabel(10L,"coding_review")).thenReturn(intent);
        ModelConfig qwen=model("qwen-max",7L);ModelConfig glm=model("glm-5",7L);
        when(models.findByTenantId(10L)).thenReturn(List.of(qwen,glm));

        Map<String,Object> result=service.simulate(new SimulationRequest("*","coding_review",90,null));

        assertThat(result.get("matched_rule_name")).isEqualTo("Code tasks");
        assertThat(result.get("selected_model")).isEqualTo("qwen-max");
        assertThat(result.get("evaluated_intent")).isEqualTo("coding_review");
        assertThat(result.get("fallback_order")).asList().containsExactly("glm-5");
        assertThat(result).containsKey("trace");
        assertThat(result.get("trace").toString()).contains("coding_review");
    }

    private RouteRule rule(Long id,String name,String matchType,String pattern,List<Long> targets){
        RouteRule r=new RouteRule();r.setId(id);r.setTenantId(10L);r.setRuleName(name);
        r.setMatchType(matchType);r.setMatchPattern(pattern);r.setTargetKeyIds(targets);
        r.setStrategy("weight");r.setFallbackEnabled(true);r.setMaxFallback(2);r.setPriority(0);r.setEnabled(true);return r;
    }
    private ModelConfig model(String displayName,Long keyId){ModelConfig m=new ModelConfig();m.setTenantId(10L);m.setDisplayName(displayName);m.setRealName(displayName);m.setApiKeyId(keyId);return m;}
}
