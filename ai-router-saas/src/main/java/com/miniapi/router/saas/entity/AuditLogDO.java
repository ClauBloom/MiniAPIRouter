package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private Long actorTenantId;
    private Long targetTenantId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String traceId;
    private String detailsJson;
    private LocalDateTime createdAt;
}
