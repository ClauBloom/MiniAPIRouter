package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 租户数据对象（DO）。
 * 
 * <p>对应数据库表 tenant，存储 SaaS 平台的租户信息。
 * 每个租户拥有独立的配额、限流、套餐等配置，
 * 所有 API Key 配置和路由规则均按租户进行隔离。
 * 
 * <p>使用 MyBatis-Plus 注解进行 ORM 映射：
 * <ul>
 *   <li>createdAt 和 updatedAt 字段自动填充</li>
 *   <li>deleted 字段为逻辑删除标记</li>
 * </ul>
 */
@Data
@TableName("tenant")
public class TenantDO {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键ID，自增
    private String tenantCode;      // 租户编码，唯一标识
    private String tenantName;      // 租户名称
    private String plan;            // 套餐类型（free=免费版，pro=专业版等）
    private Long quotaLimit;        // Token 配额上限
    private Long quotaUsed;         // 已使用 Token 配额
    private Integer quotaResetDay;  // 配额重置日（每月几号重置，1-28）
    private Integer maxRps;         // 最大每秒请求数
    private Integer status;         // 租户状态（1=启用，0=禁用）
    private LocalDateTime expiresAt;  // 租户过期时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;   // 创建时间（插入时自动填充）
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;   // 更新时间（插入和更新时自动填充）
    @TableLogic
    private Integer deleted;           // 逻辑删除标记（0=未删除，1=已删除）
}
