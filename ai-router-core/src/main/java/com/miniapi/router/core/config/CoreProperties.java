package com.miniapi.router.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MiniAPI 路由器核心配置属性类。
 * <p>
 * 从 application.yml 中以 {@code miniapi.router} 为前缀读取配置项，
 * 提供加解密密钥、日志存储路径、上游代理地址和认证令牌等核心参数。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "miniapi.router")
public class CoreProperties {

    /** AES 加解密密钥 */
    private String cryptoSecret = "";

    /** 日志与请求/响应体 Blob 存储路径 */
    private String blobStoragePath = "./data/logs";

    /** 上游代理服务的基础 URL，如 HTTP 代理地址 */
    private String proxyBaseUrl = "";

    /** 访问本路由器的认证令牌 */
    private String authToken = "sk-miniapi-standalone";

    /** Agent 身份提取相关配置 */
    private AgentConfig agent = new AgentConfig();

    /**
     * Agent 身份提取配置。
     * 控制标准请求头名称、OpenCode 自动探测开关和 IP 回退行为。
     */
    @Data
    public static class AgentConfig {
        /** 标准 Agent ID 请求头名称 */
        private String agentIdHeader = "X-Agent-Id";

        /** 标准 Agent 类型请求头名称 */
        private String agentTypeHeader = "X-Agent-Type";

        /** 标准父 Agent ID 请求头名称 */
        private String agentParentHeader = "X-Agent-Parent-Id";

        /** 是否自动探测 OpenCode 客户端（通过 User-Agent + X-Session-Id） */
        private boolean autoDetectOpencode = true;

        /** 无 Agent 身份信息时是否回退到 IP 作为会话标识 */
        private boolean fallbackToIp = true;
    }
}
