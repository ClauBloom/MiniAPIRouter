# 技术债务与向后兼容清单

> 本文档记录所有因数据迁移、接口演进产生的历史遗留代码/字段/约束，
> 用于大版本更新时集中移除，取消向后兼容。

---

## 1. 已废弃字段（下一个大版本移除）

### 1.1 `IntentConfig.targetKeyIds` / `IntentConfig.keyWeights`

| 文件 | 行 |
|------|----|
| `ai-router-core/.../domain/IntentConfig.java` | 32-34, 36-38 |

- `targetKeyIds` (`List<Long>`) → 替换为 `targetModels` (`List<String>`)
- `keyWeights` (`Map<String, Integer>`) → 替换为 `modelWeights` (`Map<String, Integer>`)
- DO 层 `IntentConfigDO` 同样包含两套字段，移除旧字段后需一并清理

**清理范围：**
- 所有读取/写入 `targetKeyIds`/`keyWeights` 的代码
- `IntentConfigDO` 中的对应列
- 数据库 `intent_config` 表中的 `target_key_ids`、`key_weights` 列

### 1.2 `RouteRule.targetKeyIds`

| 文件 | 行 |
|------|----|
| `ai-router-core/.../domain/RouteRule.java` | 34 |

- `IntentConfig` 已迁移到 model-based 命名，但 `RouteRule` 从未迁移
- 仍使用 `targetKeyIds`（Key ID 列表），无 `targetModels` 字段
- 大版本需同步迁移到 model-based

---

## 2. 启动时数据迁移（6 个步骤）

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../config/DataInitializer.java` | 97-291 |

以下迁移代码在**每次应用启动时执行**，大版本移除后可直接删掉：

| # | 方法 | 行 | 说明 |
|---|------|----|------|
| 1 | `migrateIntentSchema()` | 97-109 | 添加 `is_default`、`customized` 列 |
| 2 | `migrateModelConfigTable()` | 114-129 | 创建 `model_config` 表 |
| 3 | `migrateIntentModelColumns()` | 134-146 | 添加 `target_models`、`model_weights` 列 |
| 4 | `migrateModelsToModelConfig()` | 153-205 | 将 `models` JSON 迁移到 `model_config` 表 |
| 5 | `migrateIntentKeyToModel()` | 230-263 | 将 `target_key_ids` → `target_models` |
| 6 | `migrateAgentColumns()` | 270-291 | 添加 Agent 隔离列 |

---

## 3. 数据库 Schema 债务

### 3.1 `api_key_config.models` NOT NULL 约束

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../spiimpl/SqliteApiKeyConfigRepository.java` | 252-253 |
| `ai-router-standalone/.../entity/ApiKeyConfigDO.java` | 28-29 |

- 模型数据已迁移到 `model_config` 表
- `models` 列仍有 `NOT NULL` 约束，每次插入时写入空 `{}` 占位
- 大版本：删除 `models` 列

### 3.2 `ApiKeyConfigDO.modelMapping` 弱类型

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../entity/ApiKeyConfigDO.java` | 29 |
| `ai-router-saas/.../entity/ApiKeyConfigDO.java` | 35 |

- 类型为 `Object`（兼容旧 JSON 数组 `["a","b"]` 和新 JSON 对象 `{"a":"x"}`）
- 移除 `models` 列后，此字段可直接删除

---

## 4. 死代码

### 4.1 `SqliteApiKeyConfigRepository.convertModelMapping()`

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../spiimpl/SqliteApiKeyConfigRepository.java` | 282-301 |

- 声明了但从未在文件中被调用
- 目的是兼容新旧 `models` 列格式，移除列后可删除

### 4.2 `ConfigService.alignTargetKeyIds()`

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../service/ConfigService.java` | 488-496 |

- 从未被调用，仅在 `alignTargetModels()` 有对应新版本
- 删除 `targetKeyIds`/`keyWeights` 后此方法无存在必要

### 4.3 `ConfigService.cascadeToNonCustomized()`

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../service/ConfigService.java` | 515-528 |

- 旧版 Key-ID 级联方法
- 新方法 `cascadeToNonCustomizedModels()` 已替代
- 删除旧字段后一并删除

---

## 5. 默认意图回退缺陷

### 5.1 `DefaultIntentCatalogProvider.findDefault()` 返回 null

| 文件 | 行 |
|------|----|
| `ai-router-core/.../spi/DefaultIntentCatalogProvider.java` | 39-41 |

- 无条件返回 `null`
- 调用方 `RoutePipeline`（第 178 行）需额外 null 检查
- 大版本：要么实现此方法，要么删除 SPI 接口中的 `findDefault()`

---

## 6. 硬编码的特殊意图

### 6.1 `IntentEvaluator.SPECIAL_INTENTS`

| 文件 | 行 |
|------|----|
| `ai-router-core/.../intent/IntentEvaluator.java` | 64 |

- `Set.of("invalid_continuation", "follow_up")` 硬编码在代码中
- 这些意图标签在 `intent_config` 表中无对应记录
- 大版本应考虑通过配置方式管理

### 6.2 `RoutePipeline` 特殊意图兜底逻辑

| 文件 | 行 |
|------|----|
| `ai-router-core/.../routing/RoutePipeline.java` | 175-179, 228-250 |

- 特殊意图找不到配置时回退到 `findDefault()` → 返回 null → 再 null 检查
- 大版本应简化此链路

---

## 7. `selectByScore()` 与 `selectModelByScore()` 共存

| 文件 | 行 |
|------|----|
| `ai-router-core/.../routing/RoutePipeline.java` | 300-326, 342-365 |

- `selectByScore()` 使用旧版 Key-ID 权重机制
- `selectModelByScore()` 使用新版模型名称权重机制
- 删除 `keyWeights` 后可删除 `selectByScore()` 及相关方法

---

## 8. 种子数据写入废弃字段

| 文件 | 行 |
|------|----|
| `ai-router-standalone/.../config/DataInitializer.java` | 309-310, 350-351, 371, 393 |

- `seed()` 和 `seedIntents()` 仍初始化 `targetKeyIds`/`keyWeights`
- `createIntentRoute()` 和 `createAutoRoute()` 仍设置 `RouteRule.targetKeyIds`
- 移除废弃字段后更新

---

## 清理策略

建议在下一个大版本（2.0）中：

1. **停止写入**废弃字段，先观察运行是否正常
2. **数据库迁移**：移除 `api_key_config.models`、`intent_config.target_key_ids`、`intent_config.key_weights`
3. **删除代码**：所有标注 `@Deprecated` 的字段及其读写逻辑
4. **删除启动迁移**：`DataInitializer` 中 6 个 migrate 方法
5. **删除死代码**：`convertModelMapping()`、`alignTargetKeyIds()`、`cascadeToNonCustomized()`
6. **修复缺陷**：实现 `DefaultIntentCatalogProvider.findDefault()` 或删除 SPI 方法
7. **简化逻辑**：`RoutePipeline` 中特殊意图兜底链路
