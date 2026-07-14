package com.miniapi.router.core.streaming;

import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 上游流式 HTTP 客户端：封装对上游 AI API（OpenAI/Anthropic 等）的 HTTP 调用。
 * 支持非流式（同步一次性请求）和流式（SSE 长连接读取）两种模式。
 * 内置空闲超时机制和认证头注入。
 */
@Component
public class UpstreamStreamClient {

    private static final Logger log = LoggerFactory.getLogger(UpstreamStreamClient.class);

    /** HTTP/2 客户端，连接超时 10 秒 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 非流式请求结果 */
    public record NonStreamResult(int statusCode, String body, Map<String, String> headers) {}

    /**
     * 发起非流式上游调用（同步，一次性返回完整响应体）。
     * @param key  目标 API Key 配置
     * @param path 请求路径（如 /v1/chat/completions）
     * @param body 请求体 key-value
     * @return 状态码、响应体和响应头
     */
    public NonStreamResult callUpstream(ApiKeyConfig key, String path, Map<String, Object> body) {
        String url = buildUrl(key.getBaseUrl(), path);
        int timeoutMs = key.getTimeoutMs() != null ? key.getTimeoutMs() : 30000;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body), StandardCharsets.UTF_8));

        addAuthHeaders(builder, key);

        String requestModel = (String) body.get("model");
        log.info("[Upstream] >>> {} {} model={} timeout={}ms", key.getProvider(), url, requestModel, timeoutMs);
        log.debug("[Upstream] >>> request body:\n{}", JsonUtils.toJson(body));

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String respBody = response.body();
            int status = response.statusCode();
            /* 日志中截断过长的响应体 */
            String truncated = respBody != null && respBody.length() > 500 ? respBody.substring(0, 500) : respBody;
            log.info("[Upstream] <<< {} status={} body={}", key.getProvider(), status, truncated);
            /* 提取响应头 */
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((k, v) -> headers.put(k, v.isEmpty() ? "" : v.get(0)));
            return new NonStreamResult(status, respBody, headers);
        } catch (Exception e) {
            log.warn("[Upstream] <<< {} FAILED: {}", key.getProvider(), e.getMessage());
            throw new RuntimeException("Upstream call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 发起流式上游调用（SSE 长连接），返回 BufferedReader 逐行读取。
     * 使用 IdleTimeoutInputStream 防止长时间无数据导致的连接挂起。
     * @param key  目标 API Key 配置
     * @param path 请求路径
     * @param body 请求体
     * @return 可逐行读取 SSE 数据的 BufferedReader
     */
    public BufferedReader streamUpstream(ApiKeyConfig key, String path, Map<String, Object> body) {
        String url = buildUrl(key.getBaseUrl(), path);
        int idleTimeoutMs = key.getTimeoutMs() != null ? key.getTimeoutMs() : 30000;
        /* 总超时 = 空闲超时 × 10，最低 10 分钟 */
        long totalTimeoutMs = Math.max((long) idleTimeoutMs * 10, 600_000L);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(totalTimeoutMs))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(body), StandardCharsets.UTF_8));

        addAuthHeaders(builder, key);

        String requestModel = (String) body.get("model");
        log.info("[Upstream] >>> {} {} model={} idle_timeout={}ms total_timeout={}ms",
                key.getProvider(), url, requestModel, idleTimeoutMs, totalTimeoutMs);
        log.debug("[Upstream] >>> request body:\n{}", JsonUtils.toJson(body));

        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            log.info("[Upstream] <<< {} status={}", key.getProvider(), status);
            if (status != 200) {
                /* 非 200 状态码时读取错误响应体 */
                String errBody;
                try (var is = response.body()) {
                    errBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                String truncated = errBody != null && errBody.length() > 500 ? errBody.substring(0, 500) : errBody;
                log.warn("[Upstream] <<< {} ERROR body={}", key.getProvider(), truncated);
                throw new RuntimeException("Upstream returned " + status + ": " + errBody);
            }
            /* 包装为带空闲超时的 InputStream */
            InputStream idleStream = new IdleTimeoutInputStream(response.body(), idleTimeoutMs);
            return new BufferedReader(new InputStreamReader(idleStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[Upstream] <<< {} FAILED: {}", key.getProvider(), e.getMessage());
            throw new RuntimeException("Stream upstream failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Upstream] <<< {} INTERRUPTED", key.getProvider());
            throw new RuntimeException("Stream upstream interrupted", e);
        }
    }

    /**
     * 空闲超时输入流包装器：在底层 InputStream 长时间无数据到达时自动关闭连接。
     * 使用虚拟线程（Virtual Thread）监控最近一次读取时间，超过空闲超时则关闭流。
     */
    private static class IdleTimeoutInputStream extends InputStream {
        private final InputStream delegate;       // 被包装的原始输入流
        private final long idleTimeoutMs;         // 空闲超时时间（毫秒）
        private volatile long lastReadTime;       // 最近一次成功读取的时间戳
        private final Thread watcher;             // 监控线程

        IdleTimeoutInputStream(InputStream delegate, long idleTimeoutMs) {
            this.delegate = delegate;
            this.idleTimeoutMs = idleTimeoutMs;
            this.lastReadTime = System.currentTimeMillis();
            /* 启动虚拟线程定期检查空闲时间 */
            this.watcher = Thread.ofVirtual().start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(Math.min(idleTimeoutMs / 2, 1000));
                        if (System.currentTimeMillis() - lastReadTime > idleTimeoutMs) {
                            delegate.close();
                            return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (IOException ignored) {
                        return;
                    }
                }
            });
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result != -1) {
                lastReadTime = System.currentTimeMillis();  // 更新最后读取时间
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = delegate.read(b, off, len);
            if (result != -1) {
                lastReadTime = System.currentTimeMillis();  // 更新最后读取时间
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            watcher.interrupt();   // 中断监控线程
            delegate.close();
        }
    }

    /**
     * 根据 API Key 的协议类型添加认证头：
     * Anthropic 使用 x-api-key + anthropic-version，
     * OpenAI 及其他使用 Authorization: Bearer。
     */
    private void addAuthHeaders(HttpRequest.Builder builder, ApiKeyConfig key) {
        String protocol = key.getProtocol() != null ? key.getProtocol() : "openai";
        if ("anthropic".equalsIgnoreCase(protocol)) {
            builder.header("x-api-key", key.getApiKey());
            builder.header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + key.getApiKey());
        }
    }

    /**
     * 构建完整请求 URL：将 baseUrl 与请求路径拼接。
     * 处理 baseUrl 末尾斜杠、路径重复、版本号等边界情况。
     */
    private static String buildUrl(String baseUrl, String defaultPath) {
        if (baseUrl == null || baseUrl.isBlank()) return defaultPath;
        String trimmed = baseUrl.replaceAll("/+$", "");   // 去除末尾斜杠

        URI uri = URI.create(trimmed);
        String urlPath = uri.getPath();
        /* 去掉路径中的版本号前缀（如 /v1）用于拼接比较 */
        String endpoint = defaultPath.replaceFirst("/v\\d+", "");

        if (trimmed.endsWith(endpoint)) {
            return trimmed;
        }

        if (urlPath != null && !urlPath.isEmpty() && !"/".equals(urlPath)) {
            return trimmed + endpoint;
        }

        return trimmed + defaultPath;
    }
}
