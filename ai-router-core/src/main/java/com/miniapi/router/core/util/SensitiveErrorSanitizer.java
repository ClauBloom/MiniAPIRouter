package com.miniapi.router.core.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks endpoint and credential details before an upstream error is returned to a client. */
public final class SensitiveErrorSanitizer {

    private static final Pattern URL = Pattern.compile("(?i)\\bhttps?://[^\\s\"'<>]+");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)\\b(Bearer\\s+)[A-Za-z0-9._~+/-]+={0,2}");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\b(sk-)[A-Za-z0-9_-]+");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w.])");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<![\\w@.-])(?:[a-z0-9](?:[a-z0-9-]{0,62})\\.)+"
                    + "[a-z](?:[a-z0-9-]{0,61}[a-z0-9])(?![\\w.-])");
    private static final String URL_TRAILING_PUNCTUATION = ".,;!?)]}";
    private static final Set<String> COUNTRY_SECOND_LEVEL = Set.of(
            "ac", "co", "com", "edu", "gov", "net", "org");

    private SensitiveErrorSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String sanitized = replace(URL, message, SensitiveErrorSanitizer::maskUrl);
        sanitized = BEARER.matcher(sanitized).replaceAll("$1***");
        sanitized = API_KEY.matcher(sanitized).replaceAll("$1***");
        sanitized = IPV4.matcher(sanitized).replaceAll("***.***.***.***");
        return replace(DOMAIN, sanitized, SensitiveErrorSanitizer::maskDomain);
    }

    private static String maskUrl(String value) {
        String scheme = value.regionMatches(true, 0, "https://", 0, 8) ? "https" : "http";
        int urlEnd = value.length();
        while (urlEnd > 0 && URL_TRAILING_PUNCTUATION.indexOf(value.charAt(urlEnd - 1)) >= 0) {
            urlEnd--;
        }
        String trailing = value.substring(urlEnd);
        try {
            URI uri = URI.create(value.substring(0, urlEnd));
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return scheme + "://***" + trailing;
            }

            StringBuilder masked = new StringBuilder(scheme)
                    .append("://***");
            String suffix = publicSuffix(host);
            if (!suffix.isEmpty()) {
                masked.append('.').append(suffix);
            }
            appendMaskedPath(masked, uri.getRawPath());
            appendMaskedQuery(masked, uri.getRawQuery());
            if (uri.getRawFragment() != null) {
                masked.append("#***");
            }
            return masked.append(trailing).toString();
        } catch (IllegalArgumentException ignored) {
            return scheme + "://***" + trailing;
        }
    }

    private static void appendMaskedPath(StringBuilder masked, String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return;
        }
        if ("/".equals(rawPath)) {
            masked.append('/');
            return;
        }
        for (String part : rawPath.split("/")) {
            if (!part.isEmpty()) {
                masked.append("/***");
            }
        }
    }

    private static void appendMaskedQuery(StringBuilder masked, String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return;
        }
        StringJoiner query = new StringJoiner("&");
        for (String parameter : rawQuery.split("&")) {
            int separator = parameter.indexOf('=');
            String name = separator >= 0 ? parameter.substring(0, separator) : parameter;
            query.add(name + "=***");
        }
        masked.append('?').append(query);
    }

    private static String maskDomain(String domain) {
        String[] labels = domain.split("\\.");
        int suffixLabels = suffixLabelCount(labels);
        StringJoiner masked = new StringJoiner(".");
        for (int i = 0; i < labels.length - suffixLabels; i++) {
            masked.add("***");
        }
        for (int i = labels.length - suffixLabels; i < labels.length; i++) {
            masked.add(labels[i]);
        }
        return masked.toString();
    }

    private static String publicSuffix(String host) {
        if (IPV4.matcher(host).matches()) {
            return "***.***.***";
        }
        String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
        if (labels.length == 1) {
            return "";
        }
        int suffixLabels = suffixLabelCount(labels);
        StringJoiner suffix = new StringJoiner(".");
        for (int i = labels.length - suffixLabels; i < labels.length; i++) {
            suffix.add(labels[i]);
        }
        return suffix.toString();
    }

    private static int suffixLabelCount(String[] labels) {
        if (labels.length >= 2
                && labels[labels.length - 1].length() == 2
                && COUNTRY_SECOND_LEVEL.contains(labels[labels.length - 2].toLowerCase(Locale.ROOT))) {
            return 2;
        }
        return 1;
    }

    private static String replace(Pattern pattern, String input, Function<String, String> replacement) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.apply(matcher.group())));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
