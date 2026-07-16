package com.miniapi.router.core.domain;

import lombok.Data;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * 路由结果领域对象。
 * <p>
 * 包含路由决策后的输出信息，包括选中的上游 Key、匹配的规则、
 * 回退链路和使用策略等，并提供模型名解析等辅助方法。
 * </p>
 */
@Data
@Builder
public class RouteResult {

    /** 选中的上游 API Key 配置 */
    private ApiKeyConfig selectedKey;

    /** 选中的对外模型名 */
    private String selectedModel;

    /** 匹配到的路由规则 */
    private RouteRule matchedRule;

    /** 回退链路：按优先级排序的备用路由目标（Key + 模型） */
    private List<RouteTarget> fallbackChain;

    /** 使用的路由策略名称，如 weight、priority 等 */
    private String strategy;

    /** 匹配到的意图标签 */
    private String intent;

    /**
     * 判断是否存在回退链路。
     *
     * @return 当 fallbackChain 非空且不为空列表时返回 true
     */
    public boolean hasFallback() {
        return fallbackChain != null && !fallbackChain.isEmpty();
    }

    /**
     * 解析实际请求上游时应使用的模型名称。
     * <p>
     * 优先使用路由选中的模型名（selectedModel）。
     * 若未设置，回退到入站模型名通过 Key 的模型映射解析。
     * </p>
     *
     * @param inboundModel 入站请求指定的模型名
     * @return 实际向上游转发的模型名（真实模型名）
     */
    public String resolveUpstreamModel(String inboundModel) {
        // 优先使用路由选中的模型
        if (selectedModel != null && !selectedModel.isEmpty()) {
            return selectedModel;
        }
        // 回退：通过 Key 的模型映射解析
        if (selectedKey == null || selectedKey.getModelMapping() == null || selectedKey.getModelMapping().isEmpty()) {
            return inboundModel;
        }
        Map<String, String> mm = selectedKey.getModelMapping();
        String real = mm.get(inboundModel);
        return real != null ? real : mm.values().iterator().next();
    }
}
