package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_adjustment")
public class QuotaAdjustmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long actorUserId;
    private Long beforeLimit;
    private Long afterLimit;
    private String reason;
    private LocalDateTime createdAt;
}
