package com.miniapi.router.standalone.spiimpl;

import com.miniapi.router.core.spi.TenantContextProvider;
import org.springframework.stereotype.Component;

/**
 * 独立版租户上下文提供者实现。
 * <p>
 * 实现 TenantContextProvider SPI 接口，独立版只有一个固定租户（ID=1），
 * 用户 ID 固定为 0（表示无特定用户）。
 * </p>
 */
@Component
public class StandaloneTenantContextProvider implements TenantContextProvider {

    /**
     * 获取当前租户 ID。
     * 独立版固定返回 1。
     *
     * @return 租户 ID（固定为 1L）
     */
    @Override
    public Long getTenantId() {
        return 1L;
    }

    /**
     * 获取当前用户 ID。
     * 独立版固定返回 0（表示无特定用户）。
     *
     * @return 用户 ID（固定为 0L）
     */
    @Override
    public Long getUserId() {
        return 0L;
    }
}
