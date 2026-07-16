# 意图路由模型级配置设计

> 日期: 2026-07-16
> 状态: 已批准，待实现

## 背景

当前系统中，一个 API Key 可配置多个模型（`ApiKeyConfig.modelMapping` 是 `Map<displayName, realName>`），但意图路由配置（`IntentConfig`）引用的是 **API Key ID**（`targetKeyIds` / `keyWeights`），而非模型。

这导致一个核心问题：当一个 Key 配置了多个模型时，意图路由无法区分这些模型。例如一个 OpenAI Key 同时有 `gpt-4o`、`gpt-4o-mini`、`o1`，意图路由只能选到 Key 粒度，无法表达"推理走 o1，闲聊走 gpt-4o-mini"。

此外，当前路由管线在意图路由前会先用**客户端请求的模型名**过滤候选 Key（`RoutePipeline.java:102-108`），意图路由只在支持该模型的 Key 间选择，进一步限制了模型级选择能力。

整个设计隐含假设"1 Key = 1 模型"（README 示例也是如此），而 `modelMapping` 打破了这一假设。

## 目标

- 意图路由配置引用**模型**（对外模型名），而非 API Key
- 对外模型名在租户内**全局唯一**，DB 级约束
- 客户端传入精确匹配的模型名时直接调用该模型；传入未知模型名时走意图路由由路由器选模型

## 方案概述

将模型从 `ApiKeyConfig` 的 JSON 字段提升为独立实体（`model_config` 表），DB 强制唯一约束。`IntentConfig` 和 `RouteResult` 从引用 Key 改为引用模型名。路由流程改为：精确匹配直连 + 意图路由选模型再解析 Key。

---

## 1. 数据模型

### 1.1 新增 `model_config` 表

**Standalone (SQLite):**

```sql
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
```

**SaaS (MariaDB):**

```sql
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    real_name VARCHAR(128) NOT NULL,
    api_key_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_display (tenant_id, display_name),
    KEY idx_model_apikey (api_key_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 1.2 新增领域对象 `ModelConfig`

```java
// core/domain/ModelConfig.java
@Data
public class ModelConfig {
    private Long id;
    private Long tenantId;
    private String displayName;   // 对外模型名，租户内唯一
    private String realName;      // 发给上游的真实模型名
    private Long apiKeyId;        // 所属 API Key
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 1.3 新增 SPI `ModelConfigRepository`

```java
// core/spi/ModelConfigRepository.java
public interface ModelConfigRepository {
    List<ModelConfig> findByTenantId(Long tenantId);
    ModelConfig findByDisplayName(Long tenantId, String displayName);
    List<ModelConfig> findByApiKeyId(Long apiKeyId);
    void save(ModelConfig model);
    void deleteByApiKeyId(Long apiKeyId);
}
```

两个宿主各提供实现：
- Standalone: `SqliteModelConfigRepository`
- SaaS: `MybatisModelConfigRepository`

### 1.4 `ApiKeyConfig` 变更

- **停止使用** `api_key_config.models` 列：数据迁移到 model_config 后，该列保留但不再写入（向后兼容），后续版本再物理删除
- `ApiKeyConfig.modelMapping` **保留为派生字段**：加载 Key 时从 model_config 填充，使现有引用 `key.getModelMapping()` 的代码无需改动
- Key 的 CRUD：创建/更新 Key 时，模型列表写入 model_config（含唯一性校验），不再写 JSON 列
- 保存时若模型名与其他 Key 冲突，返回 409 Conflict

### 1.5 `IntentConfig` 变更

```java
// 旧字段（废弃，保留列但不读取）
private List<Long> targetKeyIds;
private Map<String, Integer> keyWeights;

// 新字段
private List<String> targetModels;          // 对外模型名列表
private Map<String, Integer> modelWeights;  // 模型名 -> 权重/能力阈值
```

DB 列变更（standalone `intent_config` 表）：
- 新增 `target_models TEXT NOT NULL DEFAULT '[]'`
- 新增 `model_weights TEXT NOT NULL DEFAULT '{}'`
- 旧列 `target_key_ids`、`key_weights` 保留，标记废弃

### 1.6 `RouteResult` 变更

新增字段：

```java
private String selectedModel;  // 选中的对外模型名
```

代理层用此字段（而非客户端传入的模型名）解析 real_name 并转发。

---

## 2. 路由流程

### 2.1 新路由入口逻辑

`RoutePipeline.route()` 改造：

```
客户端请求 (model=X)
  │
  ├─ model_config 中按 display_name=X 查找
  │   ├─ 命中 -> 直接路由：用该模型所属 Key 转发，real_name 来自 model_config
  │   │         RouteResult.selectedModel = X
  │   │         （仍匹配路由规则以获取 fallback 配置，但跳过意图评估）
  │   │
  │   └─ 未命中 -> 走路由规则匹配（现有流程）
  │       ├─ 匹配到 intent 规则 -> 意图路由（见 2.2）
  │       └─ 匹配到普通规则 -> 常规策略路由（weight/priority/...）
```

### 2.2 意图路由选模型

`RoutePipeline` 中 intent 分支改造：

1. **意图评估**不变：仍调用 `IntentEvaluator` 得到 `intent` + `score`
2. **缩小候选**：从 IntentConfig 取 `targetModels` + `modelWeights`，通过 `ModelConfigRepository` 解析为 `List<ModelConfig>` 候选，过滤掉所属 Key 禁用/不健康的
3. **选择**：`selectByScore` 逻辑不变，操作对象从 `ApiKeyConfig` 改为 `ModelConfig`--用 `modelWeights` 中的阈值与 `score` 比较，选出最匹配的模型
4. **解析 Key**：选中模型的 `apiKeyId` -> 查 `ApiKeyConfig` -> `RouteResult.selectedKey` + `selectedModel`

### 2.3 代理层适配

代理层当前用客户端传入的 model 名 + `selectedKey.modelMapping` 解析 real name。改为：

- 优先用 `RouteResult.selectedModel`（意图路由或直连选出的模型名）
- 通过 `ModelConfigRepository.findByDisplayName()` 取 `realName` 转发
- `RouteResult.selectedKey` 仍用于 baseUrl / apiKey / protocol

### 2.4 意图评估模型查找

`IntentEvaluator.findEvalKey()` 当前遍历候选 Key 的 modelMapping 找意图模型。改为通过 `ModelConfigRepository.findByDisplayName(tenantId, intentModel)` 直接定位所属 Key。

---

## 3. 故障转移

### 3.1 Fallback Chain 改为模型级

`RouteResult.fallbackChain` 从 `List<ApiKeyConfig>` 改为 `List<ModelConfig>`：

- **直连场景**（精确匹配）：fallback chain 为空（该模型只属一个 Key，无同模型备用）。若该 Key 不可用，降级到意图路由重新选模型。
- **意图路由场景**：fallback chain = 同一 IntentConfig 中未被选中的其他候选模型（按权重排序，截取 `maxFallback` 个）

### 3.2 转发失败时的处理

```
请求失败 (超时/5xx)
  │
  ├─ fallbackChain 中还有模型?
  │   ├─ 是 -> 取下一个 ModelConfig，解析其 Key，用该模型重试
  │   └─ 否 -> 降级：对原始请求重新走意图路由（跳过已失败的模型）
  │            若仍失败，返回 503
```

### 3.3 健康检查联动

不变。Key 级别的 `HealthChecker` 仍标记 Key 为 down。选模型时过滤掉所属 Key 为 down 的模型。

---

## 4. 前端变更

### 4.1 API Key 配置弹窗

模型映射的 UI 不变（名称 -> 真实名 的行列表），保存时后端写入 model_config。加载 Key 时从 model_config 填充回 modelMapping 供前端显示。用户无感知。

唯一新增：保存时若模型名重复（与其他 Key 冲突），后端返回 409，前端 toast 提示"对外模型名 `xxx` 已存在"。

### 4.2 意图配置弹窗（核心改动）

`renderIntentTargetList` 改为列出**模型**：

```
目标模型 (权重 0-100)
┌─────────────────────────────────────────────┐
│ glm-5.2          (Key: GLM旗舰)     [70]  ✕ │
│ qwen-3.7-max     (Key: 通义千问)     [25]  ✕ │
│ deepseek-v4-pro  (Key: DeepSeek)     [  ]  ✕ │
└─────────────────────────────────────────────┘
[下拉: 选择模型] [+ 添加]
```

- 添加下拉列出所有 model_config 中的模型（显示 `displayName (Key名)`），不再列 Key
- 权重输入框绑定 `displayName` 而非 `keyId`
- `saveIntent` 提交 `target_models` + `model_weights` 而非 `target_key_ids` + `key_weights`

### 4.3 意图配置列表展示

`renderIntents` 中"目标 Key"改为"目标模型"，显示模型名列表；"模型权重"显示 `modelName:weight`。

---

## 5. 数据迁移与版本兼容

### 5.1 启动时自动迁移

在 `DataInitializer` 中新增迁移步骤，幂等执行：

1. 检查 `model_config` 表是否存在 -> 不存在则建表
2. 遍历所有 `api_key_config` 记录的 `models` JSON 列：
   - 旧格式数组 `["a","b"]` -> `real_name = display_name`
   - 新格式对象 `{"a":"x","b":"y"}` -> 正常映射
3. 逐条 `INSERT INTO model_config`：
   - 遇到 UNIQUE 冲突（同名模型已存在）-> 跳过并记 WARN 日志
   - 冲突的模型在 model_config 中只保留第一个，后续需用户手动处理
4. 迁移 `intent_config`：
   - `target_key_ids` -> 通过 Key 的 model_config 反查出模型名，写入 `target_models`
   - `key_weights {"keyId": w}` -> `model_weights {"modelName": w}`（取该 Key 下第一个模型）
   - 旧列 `target_key_ids` / `key_weights` 保留但不再读取
5. `api_key_config.models` 列保留但不再写入（降级为历史遗留，后续版本可删）

### 5.2 迁移冲突处理

旧数据中同一个对外模型名可能存在于多个 Key（之前无约束）。迁移策略：

- **自动**：model_config 只保留第一个遇到的，其余跳过 + WARN 日志
- **管理界面提示**：迁移后若检测到被跳过的重复模型，在 Key 列表页对受影响 Key 标黄提示"模型 `xxx` 因重名未注册，请修改对外名称"
- **不阻断启动**：迁移冲突不阻止程序运行，只影响该模型无法被精确匹配/意图路由引用

### 5.3 intent_config 列迁移

standalone 的 `intent_config` 表新增 `target_models` 和 `model_weights` 两列（TEXT/JSON），旧列 `target_key_ids`、`key_weights` 保留但标记废弃。`DataInitializer` 已有给该表补列的先例（`is_default`/`customized`），沿用同样模式。

### 5.4 SaaS 模块

SaaS 当前没有 `intent_config` 表和意图配置 CRUD。本次只新增 `model_config` 表 + `ModelConfigRepository` 实现 + Key CRUD 改造。意图配置的 SaaS 移植不在本次范围内（保持现状：SaaS 用 `DefaultIntentCatalogProvider` 硬编码目录）。

---

## 影响范围汇总

| 模块 | 变更项 |
|------|--------|
| **core** | 新增 `ModelConfig` 领域对象、`ModelConfigRepository` SPI；`IntentConfig` 字段变更；`RouteResult` 新增 `selectedModel`；`RoutePipeline` 路由流程改造；`IntentEvaluator.findEvalKey` 改用 ModelConfigRepository |
| **standalone** | 新增 `model_config` 表（schema.sql）；`ModelConfigDO` + Mapper + `SqliteModelConfigRepository`；`IntentConfigDO` 字段变更；`ConfigService` Key CRUD 改造 + 意图 CRUD 适配；`DataInitializer` 迁移逻辑；前端 config.js 意图配置 UI 改造 |
| **saas** | 新增 `model_config` 表（schema.sql）；`ModelConfigDO` + Mapper + `MybatisModelConfigRepository`；`ApiKeyConfigService` Key CRUD 改造 |
| **代理层** | 转发时用 `RouteResult.selectedModel` 解析 real_name |

## 不在范围内

- SaaS 模块的意图配置 CRUD 移植
- `RouteRule.targetKeyIds` / `intentWeights` 改造（非 intent 路由仍用 Key 级，intent 路由的候选来自 IntentConfig）
- per-model 独立启禁用（模型可用性跟随所属 Key）
- `api_key_config.models` 列的物理删除（本次仅停止写入，保留向后兼容）
