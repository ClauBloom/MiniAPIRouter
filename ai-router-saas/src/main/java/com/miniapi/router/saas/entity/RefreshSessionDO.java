package com.miniapi.router.saas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Persisted, revocable refresh session. Raw tokens are never stored. */
@Data
@TableName("refresh_session")
public class RefreshSessionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tenantId;
    private String tokenHash;
    private String userAgentHash;
    private String ipAddress;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long replacedById;
    private LocalDateTime createdAt;
}
