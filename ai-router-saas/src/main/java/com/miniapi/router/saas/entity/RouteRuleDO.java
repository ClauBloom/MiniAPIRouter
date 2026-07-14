package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模型路由规则数据对象（DO）。
 * 
 * <p>对应数据库表 model_route_rule，存储租户配置的模型路由规则。
 * 路由规则定义了如何将请求模型匹配到具体的上游 API Key，
 * 包括匹配方式、目标 Key 列表、负载均衡策略、故障转移等配置。
 * 
 * <p>使用 MyBatis-Plus 注解进行 ORM 映射：
 * <ul>
 *   <li>targetKeyIds 和 intentWeights 字段使用 JacksonTypeHandler 自动序列化/反序列化为 JSON</li>
 *   <li>createdAt 和 updatedAt 字段自动填充</li>
 *   <li>deleted 字段为逻辑删除标记</li>
 * </ul>
 */
@Data
@TableName(value = "model_route_rule", autoResultMap = true)
public class RouteRuleDO {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键ID，自增
    private Long tenantId;          // 所属租户ID
    private String ruleName;        // 规则名称
    private String matchType;       // 匹配类型（如 model=按模型名称匹配）
    private String matchPattern;    // 匹配模式（模型名称或正则表达式）
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> targetKeyIds; // 目标 API Key ID 列表（JSON 存储）
    private String strategy;        // 负载均衡策略（weight=加权，priority=优先级）
    private String intentModel;     // 意图识别模型名称
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Map<String, Integer>> intentWeights; // 意图权重映射（JSON 存储）
    private Integer fallbackEnabled; // 是否启用故障转移（1=启用，0=禁用）
    private Integer maxFallback;     // 最大故障转移次数
    private Integer priority;        // 规则优先级
    private Integer enabled;         // 规则是否启用（1=启用，0=禁用）
    private String description;      // 规则描述
    private String agentType;        // 可选 Agent 类型匹配模式（glob），为空时不限制
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;   // 创建时间（插入时自动填充）
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;   // 更新时间（插入和更新时自动填充）
    @TableLogic
    private Integer deleted;           // 逻辑删除标记（0=未删除，1=已删除）
}
