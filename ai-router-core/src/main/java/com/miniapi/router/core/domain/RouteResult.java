package com.miniapi.router.core.domain;

import lombok.Data;
import lombok.Builder;
import java.util.List;

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

    /** 匹配到的路由规则 */
    private RouteRule matchedRule;

    /** 回退链路：按优先级排序的备用上游 Key 列表 */
    private List<ApiKeyConfig> fallbackChain;

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
     * 如果上游 Key 支持的模型列表包含入站模型，则直接使用入站模型；
     * 否则返回上游 Key 支持的第一个模型作为回退。若无上游 Key 或模型列表为空，
     * 直接返回入站模型。
     * </p>
     *
     * @param inboundModel 入站请求指定的模型名
     * @return 实际向上游转发的模型名
     */
    public String resolveUpstreamModel(String inboundModel) {
        if (selectedKey == null || selectedKey.getModels() == null || selectedKey.getModels().isEmpty()) {
            return inboundModel;
        }
        if (inboundModel != null && selectedKey.getModels().contains(inboundModel)) {
            return inboundModel;
        }
        return selectedKey.getModels().get(0);
    }
}
