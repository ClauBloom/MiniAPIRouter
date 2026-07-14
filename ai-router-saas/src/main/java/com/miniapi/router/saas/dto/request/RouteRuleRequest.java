package com.miniapi.router.saas.dto.request;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 路由规则请求 DTO。
 * 
 * <p>用于创建或更新模型路由规则时的请求参数封装。
 * 定义了请求模型的匹配方式、目标 API Key 列表、负载均衡策略、
 * 故障转移配置等路由规则信息。
 */
@Data
public class RouteRuleRequest {
    private String ruleName;        // 规则名称，用于标识和区分不同路由规则
    private String matchType = "model"; // 匹配类型，默认按模型名称匹配
    private String matchPattern;    // 匹配模式（如模型名称或正则表达式）
    private List<Long> targetKeyIds; // 目标 API Key ID 列表，请求将路由到这些 Key
    private String strategy = "weight"; // 负载均衡策略，默认为加权轮询
    private String intentModel;     // 意图识别模型，用于智能路由场景
    private Map<String, Map<String, Integer>> intentWeights; // 意图权重映射，按意图分配不同 Key 的权重
    private Boolean fallbackEnabled = true; // 是否启用故障转移
    private Integer maxFallback = 2;     // 最大故障转移次数
    private Integer priority = 0;        // 规则优先级，数值越大优先匹配
    private String description;     // 规则描述信息
    private String agentType;       // 可选 Agent 类型匹配模式（glob），为空时不限制
}
