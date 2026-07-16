package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "model_config", autoResultMap = true)
public class ModelConfigDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String displayName;
    private String realName;
    private Long apiKeyId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
