package com.miniapi.router.saas.service;

import com.miniapi.router.core.spi.LogSearchRepository;
import com.miniapi.router.saas.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogRouteTraceTest {
    private LogSearchRepository repository;
    private LogQueryService service;

    @BeforeEach void setUp() {
        repository=mock(LogSearchRepository.class);
        service=new LogQueryService(repository);
        TenantContext.setTenantId(10L);
    }
    @AfterEach void clear(){TenantContext.clear();}

    @Test void projectsOrderedTraceFromLogMetadata() {
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("model","qwen-max");
        detail.put("mapped_provider","openai");
        detail.put("route_rule_id",3L);
        detail.put("intent","coding_review");
        detail.put("fallback_count",1);
        detail.put("status","success");
        when(repository.getDetail(7L,10L)).thenReturn(detail);

        Map<String,Object> result=service.routeTrace(7L);

        assertThat(result).containsEntry("matched_rule_id",3L).containsEntry("intent","coding_review");
        List<Map<String,String>> trace=(List<Map<String,String>>) result.get("trace");
        assertThat(trace).extracting(step -> step.get("id"))
                .containsExactly("request","intent","rule","model","fallback","response");
        assertThat(trace.get(4).get("detail")).contains("1");
    }

    @Test void missingLogReturnsEmptyTraceWithoutThrowing() {
        when(repository.getDetail(7L,10L)).thenReturn(null);
        Map<String,Object> result=service.routeTrace(7L);
        assertThat(result.get("trace")).isNull();
    }
}
