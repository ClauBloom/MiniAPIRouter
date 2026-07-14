package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.IntentConfig;
import java.util.List;

/**
 * 意图分类目录提供者接口（SPI 扩展点）。
 * <p>
 * 定义意图分类的查询契约，路由引擎通过此接口获取可用的意图类别列表，
 * 并据此将用户请求路由到最匹配的 AI 模型/API。
 * 默认实现为 {@link DefaultIntentCatalogProvider}。
 * </p>
 */
public interface IntentCatalogProvider {

    /** 查询指定租户下所有可用的意图分类列表 */
    List<IntentConfig> findAll(Long tenantId);

    /** 根据意图标签名查找指定租户下的单个意图配置 */
    IntentConfig findByLabel(Long tenantId, String label);
}
