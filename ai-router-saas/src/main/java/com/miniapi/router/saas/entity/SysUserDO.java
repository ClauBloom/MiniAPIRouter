package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户数据对象（DO）。
 * 
 * <p>对应数据库表 sys_user，存储 SaaS 平台的用户账户信息。
 * 每个用户关联一个租户，具有角色（super_admin/tenant_admin 等），
 * 用于控制访问权限。密码使用 BCrypt 加密存储。
 * 
 * <p>使用 MyBatis-Plus 注解进行 ORM 映射：
 * <ul>
 *   <li>createdAt 和 updatedAt 字段自动填充</li>
 *   <li>deleted 字段为逻辑删除标记</li>
 * </ul>
 */
@Data
@TableName("sys_user")
public class SysUserDO {
    @TableId(type = IdType.AUTO)
    private Long id;                // 主键ID，自增
    private Long tenantId;          // 所属租户ID（0表示平台级超级管理员）
    private String username;        // 用户名，登录用
    private String password;        // BCrypt 加密后的密码
    private String nickname;        // 昵称，用于显示
    private String email;           // 邮箱地址
    private String phone;           // 手机号码
    private String avatar;          // 头像 URL
    private String role;            // 角色（super_admin=超级管理员，tenant_admin=租户管理员）
    private Integer status;         // 账户状态（1=启用，0=禁用）
    private LocalDateTime lastLoginAt; // 最近一次登录时间
    private String lastLoginIp;        // 最近一次登录 IP
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;   // 创建时间（插入时自动填充）
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;   // 更新时间（插入和更新时自动填充）
    @TableLogic
    private Integer deleted;           // 逻辑删除标记（0=未删除，1=已删除）
}
