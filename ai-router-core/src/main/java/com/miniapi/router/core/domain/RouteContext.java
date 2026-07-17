package com.miniapi.router.core.domain;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 路由上下文领域对象。
 * <p>
 * 封装一次路由决策所需的全部输入信息，包括租户标识、请求参数、
 * 匹配到的路由规则和候选上游 Key 列表等。
 * </p>
 */
@Data
@Builder
public class RouteContext {

    /** 租户 ID */
    private Long tenantId;

    /** 链路追踪 ID */
    private String traceId;

    /** 请求唯一标识 ID */
    private String requestId;

    /** 客户端 IP */
    private String clientIp;

    /** 入站请求的协议类型 */
    private String inboundProtocol;

    /** 请求的模型名称 */
    private String model;

    /** 对话消息列表，每条消息含 role 和 content 字段 */
    private List<Map<String, Object>> messages;

    /** 工具定义列表（function-calling tools），用于判断请求能力 */
    private List<Map<String, Object>> tools;

    /** 系统提示词 */
    private String systemPrompt;

    /** 其他请求参数，如 temperature、max_tokens 等 */
    private Map<String, Object> parameters;

    /** 是否为流式请求 */
    private boolean stream;

    /** 匹配到的路由规则 */
    private RouteRule matchedRule;

    /** 候选上游 API Key 列表 */
    private List<ApiKeyConfig> candidates;

    /** 匹配到的意图标签 */
    private String intent;

    /** Agent 身份标识，用于多 Agent 路由隔离 */
    private AgentIdentity agentIdentity;
}
