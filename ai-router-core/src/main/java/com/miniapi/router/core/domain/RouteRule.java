package com.miniapi.router.core.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 路由规则领域对象。
 * <p>
 * 定义一条路由规则的匹配条件和转发策略，支持按模型名、正则匹配等方式
 * 将请求路由到指定的上游 Key 列表，并可配置回退和意图权重。
 * </p>
 */
@Data
public class RouteRule {

    /** 主键 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 规则名称 */
    private String ruleName;

    /** 匹配类型：exact（精确匹配）、regex（正则匹配）等 */
    private String matchType;

    /** 匹配模式/表达式 */
    private String matchPattern;

    /** 匹配后可路由的目标 API Key ID 列表 */
    private List<Long> targetKeyIds;

    /** 路由策略：weight（权重）、priority（优先级）、round_robin（轮询）等 */
    private String strategy;

    /** 意图识别模型名称 */
    private String intentModel;

    /**
     * 意图权重配置。
     * <p>
     * 外层 key 为意图标签，内层 key 为 API Key ID，value 为权重值。
     * 例如：{"general": {"1": 50, "2": 50}}
     * </p>
     */
    private Map<String, Map<String, Integer>> intentWeights;

    /** 是否启用回退降级 */
    private Boolean fallbackEnabled;

    /** 最大回退次数 */
    private Integer maxFallback;

    /** 规则优先级，数值越小优先级越高 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 规则描述 */
    private String description;

    /** 可选 Agent 类型匹配模式（glob），为空时不限制 Agent 类型 */
    private String agentType;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;
}
