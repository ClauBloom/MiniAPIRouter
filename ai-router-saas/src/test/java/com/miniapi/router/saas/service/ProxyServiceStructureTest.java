package com.miniapi.router.saas.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyServiceStructureTest {

    @Test
    void publishLogAcceptsStructuredContextAndOutcome() {
        Method publishLog = Arrays.stream(ProxyService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("publishLog"))
                .findFirst()
                .orElseThrow();

        assertThat(publishLog.getParameterCount()).isEqualTo(2);
    }
}
