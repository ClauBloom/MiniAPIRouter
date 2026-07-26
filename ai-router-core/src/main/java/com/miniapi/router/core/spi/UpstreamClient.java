package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.ApiKeyConfig;

import java.io.BufferedReader;
import java.util.Map;

/**
 * 上游传输端口。核心编排仅依赖该契约，HTTP 客户端只是其中一种适配器。
 */
public interface UpstreamClient {

    record Response(int statusCode, String body, Map<String, String> headers) {}

    Response call(ApiKeyConfig key, String path, Map<String, Object> body);

    BufferedReader stream(ApiKeyConfig key, String path, Map<String, Object> body);
}
