package com.miniapi.router.core.spi;

import com.miniapi.router.core.config.CoreProperties;
import com.miniapi.router.core.domain.AgentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认 Agent 身份提取器。
 * <p>
 * 按优先级依次尝试以下方式提取 Agent 身份：
 * <ol>
 *   <li>标准 HTTP 头：{@code X-Agent-Id}、{@code X-Agent-Type}、{@code X-Agent-Parent-Id}（头名可配置）</li>
 *   <li>OpenCode 客户端自动探测：通过 {@code User-Agent} 前缀识别，从
 *       {@code X-Session-Id} 和 {@code x-parent-session-id} 提取</li>
 *   <li>无身份时返回 null，由调用方回退到 IP 作为会话标识</li>
 * </ol>
 * </p>
 */
@Component
public class DefaultAgentIdentityExtractor implements AgentIdentityExtractor {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentIdentityExtractor.class);

    /** OpenCode 客户端的 User-Agent 前缀 */
    private static final String OPENCODE_UA_PREFIX = "opencode/";

    private final CoreProperties coreProperties;

    public DefaultAgentIdentityExtractor(CoreProperties coreProperties) {
        this.coreProperties = coreProperties;
    }

    /**
     * 从 HTTP 请求中提取 Agent 身份。
     *
     * @param request HTTP 请求对象
     * @return Agent 身份信息，无可用身份时返回 null
     */
    @Override
    public AgentIdentity extract(HttpServletRequest request) {
        if (request == null) return null;

        /* 优先级 1: 标准请求头 X-Agent-Id、X-Agent-Type、X-Agent-Parent-Id */
        String agentId = request.getHeader(coreProperties.getAgent().getAgentIdHeader());
        if (agentId != null && !agentId.isBlank()) {
            String agentType = request.getHeader(coreProperties.getAgent().getAgentTypeHeader());
            String parentAgentId = request.getHeader(coreProperties.getAgent().getAgentParentHeader());
            boolean isSubAgent = parentAgentId != null && !parentAgentId.isBlank();
            log.debug("[AgentIdentity] Extracted from standard headers: agentId={}, type={}, parent={}",
                    agentId, agentType, parentAgentId);
            return AgentIdentity.builder()
                    .agentId(agentId)
                    .agentType(agentType != null && !agentType.isBlank() ? agentType : null)
                    .parentAgentId(parentAgentId)
                    .subAgent(isSubAgent)
                    .clientName(agentType != null ? agentType : "generic")
                    .build();
        }

        /* 优先级 2: OpenCode 客户端自动探测 */
        if (coreProperties.getAgent().isAutoDetectOpencode()) {
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.startsWith(OPENCODE_UA_PREFIX)) {
                String sessionId = request.getHeader("X-Session-Id");
                if (sessionId != null && !sessionId.isBlank()) {
                    String parentId = request.getHeader("x-parent-session-id");
                    boolean isSubAgent = parentId != null && !parentId.isBlank();
                    log.debug("[AgentIdentity] OpenCode detected: sessionId={}, parentId={}", sessionId, parentId);
                    return AgentIdentity.builder()
                            .agentId(sessionId)
                            .parentAgentId(parentId)
                            .subAgent(isSubAgent)
                            .clientName("opencode")
                            .build();
                }
            }
        }

        /* 无可用 Agent 身份，由调用方回退到 IP 作为会话标识 */
        log.debug("[AgentIdentity] No agent identity detected, falling back to IP-based session");
        return null;
    }
}
