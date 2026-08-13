package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "intent_config", autoResultMap = true)
public class IntentConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String label;
    private String name;
    private String description;
    private List<String> targetModels;
    private Map<String, Integer> modelWeights;
    private Integer sortOrder;
    private Integer enabled;
    private Integer isDefault;
    private Integer customized;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
