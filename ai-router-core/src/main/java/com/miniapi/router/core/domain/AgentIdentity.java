package com.miniapi.router.core.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 身份标识领域对象。
 * <p>
 * 封装从请求头中提取的 Agent 身份信息，用于实现多 Agent 之间的路由隔离。
 * 支持标准头 X-Agent-Id / X-Agent-Type / X-Agent-Parent-Id，
 * 以及自动探测 OpenCode 等已知客户端。
 * </p>
 */
@Data
@Builder
public class AgentIdentity {

    /** Agent 唯一标识（通常为会话 ID），用于 session key 构造 */
    private String agentId;

    /** Agent 类型/分类（如 explore、build、general），为空时表示未分类 */
    private String agentType;

    /** 父 Agent 的标识 ID，仅子 Agent 存在此字段 */
    private String parentAgentId;

    /** 客户端名称（如 opencode、generic），用于日志和统计 */
    private String clientName;

    /** 是否为子 Agent（由父 Agent 派生的任务） */
    private boolean subAgent;

    /**
     * 返回复合会话键。
     * 用于 SessionRouteMemory、FailureTracker 等以会话粒度隔离的组件。
     */
    public String toSessionKey() {
        return agentId != null ? agentId : "unknown";
    }

    /**
     * 是否有有效的 Agent 身份标识。
     * 无身份时各组件的 session key 退化为 IP 地址。
     */
    public boolean hasIdentity() {
        return agentId != null && !agentId.isBlank();
    }
}
