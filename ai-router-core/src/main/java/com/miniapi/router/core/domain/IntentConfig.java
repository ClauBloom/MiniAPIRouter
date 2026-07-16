package com.miniapi.router.core.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 意图路由配置领域对象。
 * <p>
 * 定义某个业务意图对应的上游路由策略，包括目标 Key 列表、
 * 各 Key 的权重、排序等。支持默认意图和自定义意图两种模式。
 * </p>
 */
@Data
public class IntentConfig {

    /** 主键 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 意图标签，用于匹配识别 */
    private String label;

    /** 意图名称 */
    private String name;

    /** 意图描述 */
    private String description;

    /** @deprecated 使用 {@link #targetModels} */
    @Deprecated
    private List<Long> targetKeyIds;

    /** @deprecated 使用 {@link #modelWeights} */
    @Deprecated
    private Map<String, Integer> keyWeights;

    /** 该意图可路由的目标模型对外名列表 */
    private List<String> targetModels;

    /** 各模型的权重映射，key 为对外模型名，value 为权重值 */
    private Map<String, Integer> modelWeights;

    /** 排序序号 */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否为默认意图（兜底匹配） */
    private Boolean isDefault;

    /** 是否为自定义意图（用户自定义 vs 系统预设） */
    private Boolean customized;
}
