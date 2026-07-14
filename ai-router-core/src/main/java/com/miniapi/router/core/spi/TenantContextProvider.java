package com.miniapi.router.core.spi;

/**
 * 租户上下文提供者接口（SPI 扩展点）。
 * <p>
 * 从当前请求上下文中提取租户 ID 和用户 ID，实现多租户数据隔离。
 * 典型实现从 JWT Token、请求头或 Session 中解析租户和用户信息。
 * </p>
 */
public interface TenantContextProvider {

    /** 获取当前请求的租户 ID */
    Long getTenantId();

    /** 获取当前请求的用户 ID */
    Long getUserId();
}
