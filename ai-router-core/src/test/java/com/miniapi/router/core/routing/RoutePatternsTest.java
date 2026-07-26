package com.miniapi.router.core.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RoutePatternsTest {

    @BeforeEach
    void resetCache() {
        RoutePatterns.clearCache();
    }

    @Test
    void matchesBehavesLikeStringMatches() {
        assertThat(RoutePatterns.matches("gpt-.*", "gpt-4o")).isTrue();
        assertThat(RoutePatterns.matches("gpt-.*", "claude-3")).isFalse();
        assertThat(RoutePatterns.matches("(claude|gpt)-\\d+.*", "claude-3-opus")).isTrue();
        // 完整匹配语义（与 String#matches 一致），非部分匹配
        assertThat(RoutePatterns.matches("gpt", "gpt-4o")).isFalse();
    }

    @Test
    void repeatedCallsReuseCompiledPattern() {
        // 行为验证：多次调用结果稳定（编译缓存复用不改变语义）
        for (int i = 0; i < 1000; i++) {
            assertThat(RoutePatterns.matches("deepseek-v\\d+(-flash)?", "deepseek-v4-flash")).isTrue();
        }
    }

    @Test
    void invalidRegexNeverThrowsAndNeverMatches() {
        // 此前非法正则会抛 PatternSyntaxException 导致请求 500
        assertThatCode(() -> RoutePatterns.matches("[unclosed", "model-a")).doesNotThrowAnyException();
        assertThat(RoutePatterns.matches("[unclosed", "model-a")).isFalse();
        assertThat(RoutePatterns.matches("*invalid", "anything")).isFalse();
    }

    @Test
    void nullOrEmptyInputsAreSafe() {
        assertThat(RoutePatterns.matches(null, "model")).isFalse();
        assertThat(RoutePatterns.matches("", "model")).isFalse();
        assertThat(RoutePatterns.matches(".*", null)).isFalse();
    }
}
