package com.miniapi.router.core.api;

import com.miniapi.router.core.domain.AgentIdentity;

import java.util.Map;

/** Core 对外的稳定代理请求契约，不包含 Servlet 或宿主实现类型。 */
public record RouterRequest(
        Long tenantId,
        String inboundProtocol,
        Map<String, Object> body,
        String clientApiKey,
        String clientIp,
        AgentIdentity agentIdentity,
        String traceId,
        String requestId
) {}
