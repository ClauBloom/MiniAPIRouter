-- SQLite schema for MiniAPIRouter Standalone
-- Uses CREATE TABLE IF NOT EXISTS for idempotent init

CREATE TABLE IF NOT EXISTS api_key_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    name TEXT NOT NULL,
    provider TEXT NOT NULL,
    protocol TEXT NOT NULL DEFAULT 'openai',
    api_key_enc TEXT NOT NULL,
    base_url TEXT NOT NULL,
    models TEXT NOT NULL, -- 模型映射 JSON（名称->真实模型名）
    priority INTEGER NOT NULL DEFAULT 0,
    max_concurrent INTEGER NOT NULL DEFAULT 10,
    qps_limit INTEGER NOT NULL DEFAULT 0,
    timeout_ms INTEGER NOT NULL DEFAULT 30000,
    retry_count INTEGER NOT NULL DEFAULT 1,
    status INTEGER NOT NULL DEFAULT 1,
    health_status TEXT NOT NULL DEFAULT 'unknown',
    last_health_check_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS model_route_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    rule_name TEXT NOT NULL,
    match_type TEXT NOT NULL DEFAULT 'model',
    match_pattern TEXT NOT NULL,
    target_key_ids TEXT NOT NULL,
    strategy TEXT NOT NULL DEFAULT 'weight',
    intent_model TEXT,
    intent_weights TEXT,
    fallback_enabled INTEGER NOT NULL DEFAULT 1,
    max_fallback INTEGER NOT NULL DEFAULT 2,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    description TEXT,
    agent_type TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS request_log_meta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    user_id INTEGER,
    trace_id TEXT NOT NULL,
    request_id TEXT NOT NULL,
    client_ip TEXT,
    protocol TEXT NOT NULL,
    model TEXT NOT NULL,
    mapped_provider TEXT NOT NULL,
    api_key_id INTEGER,
    route_rule_id INTEGER,
    intent TEXT,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    latency_ms INTEGER NOT NULL DEFAULT 0,
    ttft_ms INTEGER,
    status TEXT NOT NULL,
    fallback_count INTEGER NOT NULL DEFAULT 0,
    error_code TEXT,
    error_message TEXT,
    prompt_storage_url TEXT,
    response_storage_url TEXT,
    agent_id TEXT,
    agent_type TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_log_tenant_time ON request_log_meta(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_log_trace ON request_log_meta(trace_id);

CREATE TABLE IF NOT EXISTS intent_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    label TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    target_key_ids TEXT NOT NULL DEFAULT '[]',
    key_weights TEXT NOT NULL DEFAULT '{}',
    target_models TEXT NOT NULL DEFAULT '[]',
    model_weights TEXT NOT NULL DEFAULT '{}',
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    is_default INTEGER NOT NULL DEFAULT 0,
    customized INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_intent_tenant ON intent_config(tenant_id, deleted, enabled);

CREATE TABLE IF NOT EXISTS model_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    display_name TEXT NOT NULL,
    real_name TEXT NOT NULL,
    api_key_id INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(tenant_id, display_name)
);
CREATE INDEX IF NOT EXISTS idx_model_apikey ON model_config(api_key_id);
