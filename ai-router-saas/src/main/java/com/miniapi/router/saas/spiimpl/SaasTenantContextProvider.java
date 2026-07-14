package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.spi.TenantContextProvider;
import com.miniapi.router.saas.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * SaaS 租户上下文提供者
 * <p>
 * 实现 {@link TenantContextProvider} SPI 接口，将 SaaS 模块的 {@link TenantContext} 桥接到核心模块。
 * 核心模块通过此 SPI 接口获取当前请求的租户ID和用户ID，无需直接依赖 SaaS 模块的上下文。
 * </p>
 * <p>
 * 当租户ID为空时（如系统级操作），默认返回 1 作为租户ID。
 * </p>
 */
@Component
public class SaasTenantContextProvider implements TenantContextProvider {

    /**
     * 获取当前租户ID
     * <p>
     * 从 SaaS 租户上下文中获取租户ID，若为空则返回默认值 1。
     * </p>
     *
     * @return 租户ID
     */
    @Override
    public Long getTenantId() {
        Long id = TenantContext.getTenantId();
        return id != null ? id : 1L;
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，若不存在则返回 null
     */
    @Override
    public Long getUserId() {
        return TenantContext.getUserId();
    }
}
