# MiniAPIRouter — AI API 智能路由器

## 概述

MiniAPIRouter 是一个轻量级的 AI API 智能路由网关。它位于客户端与上游 AI 提供商（DeepSeek、OpenAI、Anthropic 等）之间，统一接收 OpenAI 和 Anthropic 协议的请求，根据**意图识别 + 多策略路由**智能地分发到最适合的模型，降低调用成本、提升响应质量。

```
客户端请求 ──▶  MiniAPIRouter  ──▶  上游模型（DeepSeek / GPT / Claude ...）
                    │
                    ├── 意图评估：分析请求意图与复杂度
                    ├── 路由决策：权重 / 优先级 / 轮询 / 最少连接 / 意图匹配
                    ├── 协议转换：OpenAI ⇄ Anthropic 自动互转
                    └── 故障转移：上游超时自动切换备用 Key
```

## 核心场景

| 场景 | 说明 |
|------|------|
| **多模型统一接入** | 对外暴露一个 OpenAI 兼容端点，后端可对接任意模型，客户端无需关心具体供应商 |
| **成本优化** | 简单问题路由到低成本模型，复杂推理路由到强模型，避免大材小用 |
| **高可用** | 配置多个 API Key，单 Key 限流/故障时自动切换到备用 Key，支持流式故障转移 |
| **灰度/迁移** | 通过权重或优先级路由，逐步将流量从旧模型迁移到新模型 |
| **多租户隔离**（SaaS） | JWT 认证 + 租户级路由规则，每个租户独立配置和配额管理 |

## 快速开始

### 1. 下载与安装

从 [Releases](https://github.com/example/miniapirouter/releases) 页面下载对应系统的压缩包：

- **Windows 用户**：下载 `MiniAPIRouter-win32-x64.zip`
- **其他系统**：下载 `ai-router-standalone-1.0.0.jar`（需要自行安装 Java 21）

Windows 用户解压后会得到一个 `MiniAPIRouter.exe` 文件，把它放到你喜欢的文件夹即可。

### 2. 启动程序

**Windows**：双击 `MiniAPIRouter.exe`。

**JAR 版**：打开终端（命令提示符），进入 JAR 文件所在目录，运行：

```bash
java -jar ai-router-standalone-1.0.0.jar
```

### 3. 初次配置

首次启动会弹出配置向导，按顺序填写：

1. **选择 AI 供应商** — 如 DeepSeek、OpenAI、Anthropic 等
2. **输入 API Key** — 从供应商官网获取的密钥
3. **输入 API 地址** — 通常用默认值即可
4. **选择模型** — 输入你想使用的模型名称（如 `deepseek-v4-flash`）
5. **设置端口** — 默认 `9090`，一般不需要修改
6. **设置认证密码** — 用于保护管理页面，建议修改

确认信息无误后回车，程序就会正式启动。

> 如果暂时不想配置，可以加上 `--skip-setup` 参数跳过向导，稍后在网页中设置。

### 4. 打开管理界面

启动成功后，打开浏览器访问 **[http://localhost:9090](http://localhost:9090)**。

你会看到一个管理后台，可以在里面：

- **添加和管理 API Key** — 把你的 AI 供应商密钥添加进来
- **调整路由规则** — 配置哪些模型处理哪些请求
- **发送测试消息** — 直接在线测试 AI 对话
- **查看日志和统计** — 查看请求记录和使用量

### 5. 在其他软件中使用

启动后，MiniAPIRouter 会在 `http://localhost:9090` 上提供一个兼容 OpenAI 格式的 API 地址。你可以在任何支持 OpenAI API 的软件中这样配置：

| 设置项 | 填写内容 |
|--------|----------|
| API 地址 | `http://localhost:9090/v1` |
| API Key | `sk-miniapi-standalone`（或你在向导中设置的密码） |
| 模型 | 任意添加过的模型名称（如 `deepseek-v4-flash`） |

例如在 ChatGPT Next、OpenCat、LobeChat 等第三方客户端中，将 API 地址指向 MiniAPIRouter，即可统一管理多个 AI 供应商的调用。

### 6. 停止程序

- **Windows 二进制版**：直接关闭程序窗口
- **JAR 版**：在终端中按 `Ctrl+C`

## 环境配置

项目通过 `.env` 文件管理敏感配置，程序启动时自动在**工作目录**查找该文件。

**Standalone 用户**：首次启动的交互式向导会自动生成并保存加密密钥，无需手动创建 `.env` 文件。对于源码构建或高级场景，可通过 `.env` 覆盖默认值。

**SaaS 用户**：需要配置以下环境变量：

| 变量 | 说明 |
|------|------|
| `SAAS_DB_URL` | MariaDB JDBC 连接串 |
| `SAAS_DB_USERNAME` | 数据库用户名 |
| `SAAS_DB_PASSWORD` | 数据库密码 |
| `SAAS_REDIS_HOST` | Redis 主机地址 |
| `SAAS_REDIS_PASSWORD` | Redis 密码 |
| `SAAS_CRYPTO_SECRET` | API Key 加密密钥，需 **32 字节以上** |
| `SAAS_JWT_SECRET` | JWT 签名密钥，需 **32 字节以上** |
| `SAAS_ADMIN_DEFAULT_PASSWORD` | 超级管理员初始密码（首次启动创建） |
| `SAAS_DEMO_ADMIN_DEFAULT_PASSWORD` | Demo 租户管理员初始密码（首次启动创建）

## 核心特性

| 特性 | 说明 |
|------|------|
| **多协议代理** | 接收 OpenAI `/v1/chat/completions` 和 Anthropic `/v1/messages` 格式，自动转换 |
| **智能路由** | 权重随机、优先级故障转移、轮询、最少连接数、意图评分路由 |
| **意图路由** | 调用评估模型分析请求意图，按能力阈值匹配最合适的模型 |
| **流式代理** | SSE 全双工流式传输，keepalive 心跳，流式故障转移 |
| **故障转移** | 上游超时/失败时自动切换到下一个可用 Key |
| **限流** | 令牌桶算法（Caffeine 本地实现） |
| **日志审计** | 每次请求的完整日志记录与检索 |

## 路由策略

| 策略 | 说明 |
|------|------|
| `weight` | 加权随机选择 |
| `priority` | 按优先级排序，高优先级的 Key 优先 |
| `round_robin` | 轮询 |
| `least_conn` | 最少活跃连接数 |
| `intent_weight` | 意图评估得分 × 权重（每次请求即时计算） |

## 路由规则详解

系统启动时会自动创建两条默认规则，它们各有分工：

### Auto Route（自动路由）

这是**兜底规则**，负责处理所有请求。它的匹配模式是 `*`（通配），意味着任何请求都会被它接住。默认使用加权随机策略，在所有启用的 API Key 中按权重分配。

**保留 Auto Route 的原因**：即使你不配置意图路由，系统也能正常工作。它是最低限度的路由保障，确保每个请求都有 Key 可用。

### Intent Route（意图路由）

这是**智能规则**，优先级比 Auto Route 更高（数字越小优先级越高）。它会先分析请求的意图和复杂度，再决定用哪个模型。

**工作流程**：
1. 收到请求后，调用评估模型分析用户意图（如"推理"、"闲聊"、"代码"等）
2. 根据意图类型和复杂度评分，匹配最适合的模型
3. 如果意图路由失败或超时，自动降级到 Auto Route

**保留两条规则的原因**：Intent Route 负责智能分发，Auto Route 负责兜底保障。两者配合，既实现了智能路由，又保证了高可用。

### 配置意图调度

在管理界面的「意图配置」页面，你可以看到系统内置的 8 个意图分类：

| 意图标签 | 说明 |
|----------|------|
| `reasoning` | 推理思考 — 逻辑分析、数学计算、复杂推理 |
| `casual_chat` | 日常聊天 — 闲聊、问候、简单对话 |
| `planning` | 项目规划 — 架构设计、方案拆解、任务规划 |
| `simple_instruction` | 简单执行 — 指令跟随、格式转换、简单任务 |
| `coding_review` | 代码开发与审查 — 编程、调试、代码审查 |
| `long_context_summary` | 长文本处理与摘要 — 摘要、总结、翻译 |
| `creative_writing` | 创意写作与角色扮演 — 文案、创意写作 |
| `structured_extraction` | 结构化输出与数据提取 — 信息抽取、数据整理 |

每个意图可以配置**模型权重**，表示该意图下各模型的优先级。权重越高，该模型被选中的概率越大。

**配置示例**：

假设你有五个 API Key，覆盖不同能力和成本层级：

| Key | 模型             | 能力 | 成本 |
|-----|----------------|------|------|
| Key 1 | GLM 5.2        | 国产旗舰，最强推理与代码 | 💰💰💰💰 |
| Key 2 | Qwen 3.7-Max   | 次旗舰，综合能力强 | 💰💰💰 |
| Key 3 | DeepSeek V4 Pro | 综合性价比之选 | 💰💰 |
| Key 4 | Kimi K2        | 超长上下文，创意写作 | 💰💰 |
| Key 5 | GLM-4-Flash    | 低成本快速响应 | 💰 |

你可以这样配置意图权重：

| 意图 | Key 1 | Key 2 | Key 3 | Key 4 | Key 5 | 路由策略 |
|------|-------|-------|-------|-------|-------|---------|
| `reasoning`（推理） | 70 | 25 | 5 | 0 | 0 | 旗舰模型扛复杂推理 |
| `coding_review`（代码） | 50 | 30 | 15 | 5 | 0 | 代码任务前两者为主 |
| `planning`（规划） | 30 | 40 | 20 | 10 | 0 | 架构设计偏重逻辑型 |
| `long_context_summary`（长文本） | 0 | 20 | 20 | 50 | 10 | K2 超长上下文优势 |
| `creative_writing`（创意写作） | 0 | 10 | 20 | 60 | 10 | 创意任务 K2 优先 |
| `structured_extraction`（结构化） | 0 | 20 | 40 | 30 | 10 | 结构化偏轻量模型 |
| `simple_instruction`（简单指令） | 0 | 0 | 15 | 15 | 70 | 简单任务走低成本 |
| `casual_chat`（闲聊） | 0 | 0 | 10 | 0 | 90 | 闲聊几乎全部走最便宜 |

这样配置的效果：

- **复杂推理**（"证明黎曼猜想的最新进展"）→ GLM 5.2（70%）或 Qwen 3.7-Max（25%），结果质量有保障
- **代码审查**（"帮我 review 这段 Rust 代码"）→ 主要走 GLM 5.2 和 Qwen 3.7-Max，少数简单问题走 DeepSeek
- **闲聊**（"今天天气怎么样"）→ 90% 走最便宜的 GLM-4-Flash
- **长文本处理**（"总结这篇 10 万字论文"）→ 50% 走 Kimi K2（超长上下文），不会跑到不支持长文的模型
- **创意写作**（"写一篇科幻小说"）→ 60% 走 Kimi K2，创意类任务更擅长
- **简单指令**（"把这段文本转成 JSON"）→ 70% 走 GLM-4-Flash，用最便宜的方式完成

相比只用单一旗舰模型（如 GLM 5.2），这种配置可节省 **60%-85%** 的 API 调用成本，同时复杂推理和代码任务仍由最强的模型完成。

**复杂度评分机制**：

除了意图分类，系统还会评估请求的复杂度（1-100 分）。每个 Key 可以设置一个**能力阈值**（weight 字段），只有当复杂度评分 ≥ 阈值时，该 Key 才会被选中。

例如：
- Key 1 权重设为 60 → 只有复杂度 ≥ 60 的请求才会被选中
- Key 2 权重设为 20 → 复杂度 ≥ 20 的请求都可能被选中

这样可以实现"简单问题用便宜模型，复杂问题用贵模型"的成本优化策略。

## API 端点

### 管理接口（`/api/v1`）

| 路径 | 说明 |
|------|------|
| `GET    /system/health` | 健康检查（公开） |
| `GET    /system/version` | 版本信息（公开） |
| `GET    /config/api-keys` | API Key 列表 |
| `POST   /config/api-keys` | 创建 API Key |
| `PUT    /config/api-keys/{id}` | 更新 API Key |
| `DELETE /config/api-keys/{id}` | 删除 API Key |
| `PATCH  /config/api-keys/{id}/status` | 启用/禁用 |
| `GET    /config/route-rules` | 路由规则列表 |
| `POST   /config/route-rules` | 创建路由规则 |
| `GET    /config/intents` | 意图配置 |
| `GET    /logs` | 日志查询 |
| `GET    /dashboard/summary` | 仪表盘摘要 |

### 代理接口（`/v1`）

| 路径 | 说明 |
|------|------|
| `POST /v1/chat/completions` | OpenAI 格式（流式 + 非流式） |
| `POST /v1/messages` | Anthropic 格式（流式 + 非流式） |

所有接口请求 Header 需携带 `Authorization: Bearer <auth-token>` 或 `x-api-key`。

---

# 开发

## 项目结构

```
ai-router-parent/
├── ai-router-core/           # 核心库（非应用）
│   └── src/main/java/com/miniapi/router/core/
│       ├── spi/              # 12 个 SPI 接口（缓存、存储、限流、日志等）
│       ├── routing/          # 路由流水线 + 5 种策略
│       ├── protocol/         # OpenAI / Anthropic 协议转换器
│       ├── streaming/        # SSE 流式代理
│       ├── intent/           # 意图评估与路由
│       └── domain/           # 领域模型
├── ai-router-standalone/     # 单体宿主（推荐）
│   └── 零外部依赖：SQLite + Caffeine + 本地文件
└── ai-router-saas/           # SaaS 宿主（MariaDB + Redis + JWT）
```

## 设计理念

- **插件式 SPI 架构**：核心库定义接口，宿主按需提供实现。Standalone 用 SQLite + Caffeine 零依赖运行，SaaS 用 MariaDB + Redis 支持生产
- **协议透明**：内部统一请求/响应模型，支持 OpenAI 和 Anthropic 协议互转，流式与非流式代理
- **虚拟线程**：全链路采用 Java 21 虚拟线程，大幅降低高并发下的资源消耗

## 技术栈

- Java 21 + 虚拟线程
- Spring Boot 3.3.5 + Spring WebFlux (WebClient)
- Jackson（snake_case 序列化）
- MyBatis-Plus 3.5.7
- Caffeine 3.1.8
- JJWT 0.12.6（SaaS）
- MariaDB JDBC（SaaS）/ SQLite JDBC（Standalone）

## 从源码构建

```bash
# 构建全部模块
mvn clean package -DskipTests

# 仅构建 Standalone（含依赖）
mvn clean package -pl ai-router-standalone -am -DskipTests
```

> 本项目不包含测试套件（无 `src/test` 目录），`mvn test` 不会验证任何内容。

## IDEA 配置

1. Run Configuration → `Working directory` 设为 `$PROJECT_DIR$`（项目根目录）
2. 确保 `.env` 文件在项目根目录，Spring Boot 通过 `spring.config.import=optional:file:.env[.properties]` 自动加载

## SPI 机制

`ai-router-core` 定义了 12 个 SPI 接口，每个宿主模块提供自己的实现：

| SPI 接口 | Standalone 实现 | SaaS 实现 |
|----------|----------------|-----------|
| `CacheService` | CaffeineCacheService | RedisCacheService |
| `RateLimiter` | CaffeineRateLimiter | RedisRateLimiter |
| `LogRepository` | SqliteLogRepository | MybatisLogRepository |
| `ApiKeyConfigRepository` | SqliteApiKeyConfigRepository | MybatisApiKeyConfigRepository |
| `BlobStorage` | LocalFileBlobStorage | LocalFileBlobStorage |
| `HealthChecker` | PassiveHealthChecker | ScheduledHealthChecker |
| `EventPublisher` | LocalEventPublisher | AsyncEventPublisher |

## SaaS 环境配置

SaaS 模式需要以下环境变量：

| 变量 | 说明 |
|------|------|
| `SAAS_DB_URL` | MariaDB JDBC 连接串 |
| `SAAS_DB_USERNAME` | 数据库用户名 |
| `SAAS_DB_PASSWORD` | 数据库密码 |
| `SAAS_REDIS_HOST` | Redis 主机地址 |
| `SAAS_REDIS_PASSWORD` | Redis 密码 |
| `SAAS_CRYPTO_SECRET` | API Key 加密密钥，需 **32 字节以上** |
| `SAAS_JWT_SECRET` | JWT 签名密钥，需 **32 字节以上** |
| `SAAS_ADMIN_DEFAULT_PASSWORD` | 超级管理员初始密码（首次启动创建） |
| `SAAS_DEMO_ADMIN_DEFAULT_PASSWORD` | Demo 租户管理员初始密码（首次启动创建） |
