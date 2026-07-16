-- MiniAPIRouter SaaS Database Schema
CREATE DATABASE IF NOT EXISTS miniapi_router CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE miniapi_router;

-- 租户表
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    plan VARCHAR(32) NOT NULL DEFAULT 'free',
    quota_limit BIGINT NOT NULL DEFAULT 1000000,
    quota_used BIGINT NOT NULL DEFAULT 0,
    quota_reset_day TINYINT NOT NULL DEFAULT 1,
    max_rps INT NOT NULL DEFAULT 10,
    status TINYINT NOT NULL DEFAULT 1,
    expires_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_code),
    KEY idx_tenant_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NULL,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    avatar VARCHAR(512) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'user',
    status TINYINT NOT NULL DEFAULT 1,
    last_login_at DATETIME NULL,
    last_login_ip VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_username_tenant (username, tenant_id, deleted),
    KEY idx_user_tenant (tenant_id, deleted),
    KEY idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 上游 API Key 配置表
CREATE TABLE IF NOT EXISTS api_key_config (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    protocol VARCHAR(16) NOT NULL DEFAULT 'openai',
    api_key_enc VARCHAR(512) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    models JSON NOT NULL, -- 模型映射（名称->真实模型名）
    priority INT NOT NULL DEFAULT 0,
    max_concurrent INT NOT NULL DEFAULT 10,
    qps_limit INT NOT NULL DEFAULT 0,
    timeout_ms INT NOT NULL DEFAULT 30000,
    retry_count INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    health_status VARCHAR(16) NOT NULL DEFAULT 'unknown',
    last_health_check_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_apikey_tenant (tenant_id, deleted),
    KEY idx_apikey_provider (provider, status),
    KEY idx_apikey_health (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 模型路由规则表
CREATE TABLE IF NOT EXISTS model_route_rule (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    match_type VARCHAR(32) NOT NULL DEFAULT 'model',
    match_pattern VARCHAR(512) NOT NULL,
    target_key_ids JSON NOT NULL,
    strategy VARCHAR(32) NOT NULL DEFAULT 'weight',
    intent_model VARCHAR(64) NULL,
    intent_weights JSON NULL,
    fallback_enabled TINYINT NOT NULL DEFAULT 1,
    max_fallback INT NOT NULL DEFAULT 2,
    priority INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(512) NULL,
    agent_type VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_rule_tenant (tenant_id, deleted, enabled),
    KEY idx_rule_priority (tenant_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 请求日志元数据表
CREATE TABLE IF NOT EXISTS request_log_meta (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    trace_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    client_ip VARCHAR(64) NULL,
    protocol VARCHAR(16) NOT NULL,
    model VARCHAR(64) NOT NULL,
    mapped_provider VARCHAR(32) NOT NULL,
    api_key_id BIGINT NULL,
    route_rule_id BIGINT NULL,
    intent VARCHAR(64) NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    ttft_ms INT NULL,
    status VARCHAR(16) NOT NULL,
    fallback_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(32) NULL,
    error_message VARCHAR(1024) NULL,
    prompt_storage_url VARCHAR(512) NULL,
    response_storage_url VARCHAR(512) NULL,
    agent_id VARCHAR(128) NULL,
    agent_type VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_log_tenant_time (tenant_id, created_at),
    KEY idx_log_trace (trace_id),
    KEY idx_log_status (tenant_id, status, created_at),
    KEY idx_log_model (tenant_id, model, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    real_name VARCHAR(128) NOT NULL,
    api_key_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_tenant_display (tenant_id, display_name),
    KEY idx_model_apikey (api_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
