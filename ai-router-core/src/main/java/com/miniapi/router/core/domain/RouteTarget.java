package com.miniapi.router.core.domain;

/**
 * 路由目标：将选中的模型与其所属 Key 绑定，供代理层直接使用。
 *
 * @param key         上游 API Key 配置
 * @param displayName 对外模型名
 * @param realName    真实模型名（发给上游）
 */
public record RouteTarget(ApiKeyConfig key, String displayName, String realName) {}
