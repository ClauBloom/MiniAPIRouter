package com.miniapi.router.core.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 路由规则正则匹配工具：编译结果缓存 + 非法正则防护。
 * <p>
 * 此前 {@code model.matches(pattern)} 会在每次请求时重新编译 {@link Pattern}，
 * 且非法正则会抛出 {@link PatternSyntaxException} 导致该规则命中的所有请求 500。
 * 本工具将编译结果按模式串缓存（编译一次、多次复用），
 * 非法正则仅告警一次并视为"永不匹配"，请求继续尝试后续规则。
 * </p>
 */
public final class RoutePatterns {

    private static final Logger log = LoggerFactory.getLogger(RoutePatterns.class);

    /**
     * 模式串 -> 编译结果 的缓存。
     * 规则数量由管理端配置、天然有限；maximumSize 仅为防御规则频繁改写导致的堆积。
     * Optional.empty() 表示该模式串非法（编译失败），缓存住以避免重复编译与重复告警。
     */
    private static final Cache<String, Optional<Pattern>> CACHE = Caffeine.newBuilder()
            .maximumSize(512)
            .build();

    private RoutePatterns() {
    }

    /**
     * 判断文本是否完整匹配指定正则（等价于 {@code text.matches(pattern)}，但复用编译结果）。
     *
     * @param pattern 正则模式串（来自路由规则配置）
     * @param text    待匹配文本（模型名等）
     * @return 匹配返回 true；模式串为空、非法或不匹配时返回 false
     */
    public static boolean matches(String pattern, String text) {
        if (pattern == null || pattern.isEmpty() || text == null) {
            return false;
        }
        Optional<Pattern> compiled = CACHE.get(pattern, RoutePatterns::compile);
        return compiled != null && compiled.isPresent() && compiled.get().matcher(text).matches();
    }

    /** 编译模式串；失败时告警并返回 empty（该结果会被缓存，避免每次请求重复告警） */
    private static Optional<Pattern> compile(String pattern) {
        try {
            return Optional.of(Pattern.compile(pattern));
        } catch (PatternSyntaxException e) {
            log.warn("[RoutePatterns] Invalid regex in route rule, treated as no-match: '{}' ({})",
                    pattern, e.getMessage());
            return Optional.empty();
        }
    }

    /** 清空编译缓存（仅供测试使用） */
    static void clearCache() {
        CACHE.invalidateAll();
    }
}
