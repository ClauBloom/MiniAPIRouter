package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.AgentIdentity;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Agent 身份提取器 SPI 接口。
 * <p>
 * 从 HTTP 请求中提取 Agent 身份信息（Agent ID、类型、父 Agent ID 等）。
 * 部署方可实现此接口以支持自定义的 Agent 识别逻辑。
 * 默认实现 {@code DefaultAgentIdentityExtractor} 支持标准请求头和 OpenCode 自动探测。
 * </p>
 */
public interface AgentIdentityExtractor {

    /**
     * 从 HTTP 请求中提取 Agent 身份。
     *
     * @param request HTTP 请求对象
     * @return Agent 身份信息，若无可用身份信息则返回 null（触发 IP 回退）
     */
    AgentIdentity extract(HttpServletRequest request);
}
