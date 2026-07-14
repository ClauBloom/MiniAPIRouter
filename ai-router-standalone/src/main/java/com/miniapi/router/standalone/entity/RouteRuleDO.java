package com.miniapi.router.standalone.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 路由规则数据对象（DO）。
 * <p>
 * 对应数据库 model_route_rule 表，存储模型路由规则的配置信息。
 * 路由规则决定了请求如何匹配到目标 API Key，支持按模型匹配、按意图匹配等模式。
 * 包含降级策略、优先级、权重等配置。
 * </p>
 */
@Data
@TableName(value = "model_route_rule", autoResultMap = true)
public class RouteRuleDO {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键 ID，自增
    private Long tenantId;              // 租户 ID
    private String ruleName;            // 规则名称
    private String matchType;           // 匹配类型（model=按模型匹配, intent=按意图匹配）
    private String matchPattern;        // 匹配模式（如 * 表示匹配所有）
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> targetKeyIds;    // 目标 API Key ID 列表，以 JSON 存储
    private String strategy;            // 路由策略（weight=加权, priority=优先级）
    private String intentModel;         // 意图评估模型名称（用于意图路由）
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Map<String, Integer>> intentWeights; // 意图权重映射，以 JSON 存储
    private Integer fallbackEnabled;    // 是否启用降级（1=是, 0=否）
    private Integer maxFallback;        // 最大降级次数
    private Integer priority;           // 规则优先级（数字越小优先级越高）
    private Integer enabled;            // 是否启用（1=启用, 0=禁用）
    private String description;         // 规则描述
    private String agentType;           // 可选 Agent 类型匹配模式（glob），为空时不限制
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;    // 创建时间，插入时自动填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;    // 更新时间，插入和更新时自动填充
    @TableLogic
    private Integer deleted;            // 逻辑删除标志（0=未删除, 1=已删除）
}
