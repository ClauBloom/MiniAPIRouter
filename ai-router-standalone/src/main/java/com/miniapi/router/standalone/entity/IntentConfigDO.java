package com.miniapi.router.standalone.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 意图配置数据对象（DO）。
 * <p>
 * 对应数据库 intent_config 表，存储意图路由的配置信息。
 * 每个意图配置包含标签、名称、描述、目标 Key ID 列表和对应的权重。
 * 包含一个默认意图模板，编辑后会同步到未自定义的意图。
 * </p>
 */
@Data
@TableName(value = "intent_config", autoResultMap = true)
public class IntentConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;                    // 主键 ID，自增
    private Long tenantId;              // 租户 ID
    private String label;               // 意图标签（英文标识，如 reasoning, coding_review）
    private String name;                // 意图名称（中文显示名）
    private String description;         // 意图描述
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> targetKeyIds;    // 目标 API Key ID 列表，以 JSON 存储
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> keyWeights; // 各 Key 的权重映射，以 JSON 存储
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> targetModels;    // 目标模型对外名列表，以 JSON 存储
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> modelWeights; // 各模型权重映射，以 JSON 存储
    private Integer sortOrder;          // 排序顺序
    private Integer enabled;            // 是否启用（1=启用, 0=禁用）
    private Integer isDefault;          // 是否为默认意图模板（1=是, 0=否）
    private Integer customized;         // 是否已被用户自定义（1=是, 0=否，默认意图编辑后会同步到未自定义的意图）
    private String createdAt;           // 创建时间
    private String updatedAt;           // 更新时间
    @TableLogic
    private Integer deleted;            // 逻辑删除标志（0=未删除, 1=已删除）
}
