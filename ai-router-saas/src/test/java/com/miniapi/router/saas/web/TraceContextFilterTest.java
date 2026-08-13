package com.miniapi.router.saas.web;

import com.miniapi.router.saas.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextFilterTest {

    private TraceContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceContextFilter();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generatesTraceIdAndReturnsItInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inside = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> inside.set(TenantContext.getTraceId()));

        assertThat(inside.get()).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(TraceContextFilter.TRACE_HEADER)).isEqualTo(inside.get());
        assertThat(TenantContext.getTraceId()).isNull();
    }

    @Test
    void acceptsSafeClientTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inside = new AtomicReference<>();
        request.addHeader(TraceContextFilter.TRACE_HEADER, "request_01JABCDEF0123456789");

        filter.doFilter(request, response,
                (req, res) -> inside.set(TenantContext.getTraceId()));

        assertThat(inside.get()).isEqualTo("request_01JABCDEF0123456789");
        assertThat(response.getHeader(TraceContextFilter.TRACE_HEADER)).isEqualTo(inside.get());
    }

    @Test
    void replacesUnsafeClientTraceIdInsteadOfReflectingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inside = new AtomicReference<>();
        request.addHeader(TraceContextFilter.TRACE_HEADER, "unsafe trace\r\ninjected: value");

        filter.doFilter(request, response,
                (req, res) -> inside.set(TenantContext.getTraceId()));

        assertThat(inside.get()).matches("[0-9a-f]{32}");
        assertThat(inside.get()).doesNotContain("unsafe", "\r", "\n");
    }
}
