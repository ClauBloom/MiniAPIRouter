package com.miniapi.router.core.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模型配置领域对象。
 * <p>
 * 每个模型是一个独立实体，对外模型名在租户内唯一。
 * 通过 apiKeyId 关联所属的 API Key 配置。
 * </p>
 */
@Data
public class ModelConfig {

    /** 主键 ID */
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 对外模型名，租户内唯一 */
    private String displayName;

    /** 真实模型名，发送给上游 API */
    private String realName;

    /** 所属 API Key ID */
    private Long apiKeyId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;
}
