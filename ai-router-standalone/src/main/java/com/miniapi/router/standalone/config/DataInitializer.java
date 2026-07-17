package com.miniapi.router.standalone.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniapi.router.core.domain.ApiKeyConfig;
import com.miniapi.router.core.domain.ModelConfig;
import com.miniapi.router.core.domain.RouteRule;
import com.miniapi.router.core.spi.ApiKeyConfigRepository;
import com.miniapi.router.core.spi.ModelConfigRepository;
import com.miniapi.router.core.spi.RouteRuleRepository;
import com.miniapi.router.core.util.JsonUtils;
import com.miniapi.router.standalone.entity.IntentConfigDO;
import com.miniapi.router.standalone.mapper.IntentConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据初始化器。
 * <p>
 * 在应用启动时执行数据迁移和初始化操作：
 * 1. 迁移 intent_config 表结构（添加 is_default 和 customized 列）
 * 2. 初始化默认意图配置和预设意图
 * 3. 创建默认路由规则（意图路由和自动路由）
 * 4. 从首次启动向导数据创建 API Key
 * 5. 打印启动横幅信息
 * </p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final Long TENANT_ID = 1L; // 独立版固定租户 ID

    private final ApiKeyConfigRepository keyRepository; // API Key 仓储
    private final RouteRuleRepository ruleRepository;   // 路由规则仓储
    private final IntentConfigMapper intentMapper;      // 意图配置 Mapper
    private final ModelConfigRepository modelConfigRepository; // 模型配置仓储
    private final JdbcTemplate jdbcTemplate;            // 用于执行原生 SQL（表结构迁移）

    public DataInitializer(ApiKeyConfigRepository keyRepository, RouteRuleRepository ruleRepository,
                           IntentConfigMapper intentMapper, ModelConfigRepository modelConfigRepository,
                           DataSource dataSource) {
        this.keyRepository = keyRepository;
        this.ruleRepository = ruleRepository;
        this.intentMapper = intentMapper;
        this.modelConfigRepository = modelConfigRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 应用启动时执行的初始化逻辑。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        migrateIntentSchema(); // 迁移意图表结构
        migrateModelConfigTable();
        migrateIntentModelColumns();
        migrateModelsToModelConfig();
        dropLegacyIntentColumns(); // 删除 intent_config 旧列 target_key_ids / key_weights
        migrateAgentColumns(); // 迁移 Agent 隔离相关列
        seedIntents();         // 初始化意图数据

        // 检查并创建默认路由规则
        List<RouteRule> existingRules = ruleRepository.findByTenantId(TENANT_ID);
        boolean hasIntentRoute = existingRules.stream()
                .anyMatch(r -> "Intent Route".equals(r.getRuleName()));
        boolean hasAutoRoute = existingRules.stream()
                .anyMatch(r -> "Auto Route".equals(r.getRuleName()));
        if (!hasIntentRoute) createIntentRoute(); // 创建意图路由规则
        if (!hasAutoRoute) createAutoRoute();     // 创建自动路由规则

        // 如果首次启动向导有配置数据，则创建对应的 API Key
        if (SetupWizard.hasSetupData()) {
            createKeyFromSetup();
            SetupWizard.deleteSetupData(); // 使用后删除临时配置文件
        }

        printStartupBanner(); // 打印启动横幅
    }

    /**
     * 迁移 intent_config 表结构。
     * 检查并添加 is_default 和 customized 列（用于版本兼容性升级）。
     */
    private void migrateIntentSchema() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(intent_config)");
        boolean hasIsDefault = columns.stream().anyMatch(c -> "is_default".equals(c.get("name")));
        boolean hasCustomized = columns.stream().anyMatch(c -> "customized".equals(c.get("name")));
        if (!hasIsDefault) {
            jdbcTemplate.execute("ALTER TABLE intent_config ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0");
            log.info("[Migration] Added is_default column to intent_config");
        }
        if (!hasCustomized) {
            jdbcTemplate.execute("ALTER TABLE intent_config ADD COLUMN customized INTEGER NOT NULL DEFAULT 0");
            log.info("[Migration] Added customized column to intent_config");
        }
    }

    /**
     * 创建 model_config 表（如果不存在）。
     */
    private void migrateModelConfigTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS model_config (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tenant_id INTEGER NOT NULL DEFAULT 1,
                display_name TEXT NOT NULL,
                real_name TEXT NOT NULL,
                api_key_id INTEGER NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(tenant_id, display_name)
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_model_apikey ON model_config(api_key_id)");
        log.info("[Migration] model_config table ready");
    }

    /**
     * 迁移 intent_config 表：添加 target_models 和 model_weights 列。
     */
    private void migrateIntentModelColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(intent_config)");
        boolean hasTargetModels = columns.stream().anyMatch(c -> "target_models".equals(c.get("name")));
        boolean hasModelWeights = columns.stream().anyMatch(c -> "model_weights".equals(c.get("name")));
        if (!hasTargetModels) {
            jdbcTemplate.execute("ALTER TABLE intent_config ADD COLUMN target_models TEXT NOT NULL DEFAULT '[]'");
            log.info("[Migration] Added target_models column to intent_config");
        }
        if (!hasModelWeights) {
            jdbcTemplate.execute("ALTER TABLE intent_config ADD COLUMN model_weights TEXT NOT NULL DEFAULT '{}'");
            log.info("[Migration] Added model_weights column to intent_config");
        }
    }

    /**
     * 将 api_key_config.models JSON 列中的模型数据迁移到 model_config 表。
     * 幂等：跳过已存在的模型名。
     */
    @SuppressWarnings("unchecked")
    private void migrateModelsToModelConfig() {
        // 检查 model_config 是否已有数据（已迁移过则跳过）
        Long existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_config", Long.class);
        if (existingCount != null && existingCount > 0) {
            log.info("[Migration] model_config already has {} rows, skipping model migration", existingCount);
            return;
        }

        List<Map<String, Object>> keys = jdbcTemplate.queryForList(
                "SELECT id, tenant_id, models FROM api_key_config WHERE deleted = 0");
        int migrated = 0;
        int skipped = 0;
        for (Map<String, Object> keyRow : keys) {
            Long keyId = ((Number) keyRow.get("id")).longValue();
            Long tenantId = ((Number) keyRow.get("tenant_id")).longValue();
            String modelsJson = (String) keyRow.get("models");
            if (modelsJson == null || modelsJson.isBlank()) continue;

            try {
                JsonNode node = JsonUtils.parse(modelsJson);
                if (node.isObject()) {
                    // 新格式：{"name": "realName", ...}
                    var fields = node.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        String displayName = entry.getKey();
                        String realName = entry.getValue().asText(displayName);
                        if (tryInsertModel(tenantId, displayName, realName, keyId)) {
                            migrated++;
                        } else {
                            skipped++;
                            log.warn("[Migration] Skipped duplicate model '{}' for key_id={}", displayName, keyId);
                        }
                    }
                } else if (node.isArray()) {
                    // 旧格式：["name1", "name2"]
                    for (JsonNode item : node) {
                        String name = item.asText();
                        if (tryInsertModel(tenantId, name, name, keyId)) {
                            migrated++;
                        } else {
                            skipped++;
                            log.warn("[Migration] Skipped duplicate model '{}' for key_id={}", name, keyId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[Migration] Failed to parse models JSON for key_id={}: {}", keyId, e.getMessage());
            }
        }
        log.info("[Migration] Migrated {} models to model_config ({} skipped due to duplicates)", migrated, skipped);
    }

    /**
     * 尝试插入一条模型记录，遇到唯一约束冲突时返回 false。
     */
    private boolean tryInsertModel(Long tenantId, String displayName, String realName, Long apiKeyId) {
        try {
            ModelConfig mc = new ModelConfig();
            mc.setTenantId(tenantId);
            mc.setDisplayName(displayName);
            mc.setRealName(realName);
            mc.setApiKeyId(apiKeyId);
            modelConfigRepository.save(mc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除 intent_config 表中已废弃的 target_key_ids / key_weights 列。
     * 这些列已被 target_models / model_weights 取代。
     * 幂等：列不存在时跳过。
     */
    private void dropLegacyIntentColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(intent_config)");
        boolean hasTargetKeyIds = columns.stream().anyMatch(c -> "target_key_ids".equals(c.get("name")));
        boolean hasKeyWeights = columns.stream().anyMatch(c -> "key_weights".equals(c.get("name")));
        if (hasTargetKeyIds) {
            try {
                jdbcTemplate.execute("ALTER TABLE intent_config DROP COLUMN target_key_ids");
                log.info("[Migration] Dropped target_key_ids column from intent_config");
            } catch (Exception e) {
                log.warn("[Migration] Failed to drop target_key_ids column: {}", e.getMessage());
            }
        }
        if (hasKeyWeights) {
            try {
                jdbcTemplate.execute("ALTER TABLE intent_config DROP COLUMN key_weights");
                log.info("[Migration] Dropped key_weights column from intent_config");
            } catch (Exception e) {
                log.warn("[Migration] Failed to drop key_weights column: {}", e.getMessage());
            }
        }
    }

    /**
     * 迁移 Agent 隔离相关列。
     * 为 model_route_rule 表添加 agent_type 列，为 request_log_meta 表添加 agent_id 和 agent_type 列。
     */
    private void migrateAgentColumns() {
        // model_route_rule 添加 agent_type 列
        List<Map<String, Object>> ruleCols = jdbcTemplate.queryForList("PRAGMA table_info(model_route_rule)");
        boolean ruleHasAgentType = ruleCols.stream().anyMatch(c -> "agent_type".equals(c.get("name")));
        if (!ruleHasAgentType) {
            jdbcTemplate.execute("ALTER TABLE model_route_rule ADD COLUMN agent_type TEXT");
            log.info("[Migration] Added agent_type column to model_route_rule");
        }

        // request_log_meta 添加 agent_id 和 agent_type 列
        List<Map<String, Object>> logCols = jdbcTemplate.queryForList("PRAGMA table_info(request_log_meta)");
        boolean logHasAgentId = logCols.stream().anyMatch(c -> "agent_id".equals(c.get("name")));
        boolean logHasAgentType = logCols.stream().anyMatch(c -> "agent_type".equals(c.get("name")));
        if (!logHasAgentId) {
            jdbcTemplate.execute("ALTER TABLE request_log_meta ADD COLUMN agent_id TEXT");
            log.info("[Migration] Added agent_id column to request_log_meta");
        }
        if (!logHasAgentType) {
            jdbcTemplate.execute("ALTER TABLE request_log_meta ADD COLUMN agent_type TEXT");
            log.info("[Migration] Added agent_type column to request_log_meta");
        }
    }

    /**
     * 初始化意图配置数据。
     * 如果没有默认意图，则创建默认意图模板。
     * 如果只有默认意图，则创建预设的意图配置（推理、聊天、规划等）。
     */
    private void seedIntents() {
        // 检查是否已有默认意图
        Long defaultCount = intentMapper.selectCount(
                new LambdaQueryWrapper<IntentConfigDO>().eq(IntentConfigDO::getIsDefault, 1));
        if (defaultCount == null || defaultCount == 0) {
            // 创建默认意图模板
            IntentConfigDO dft = new IntentConfigDO();
            dft.setTenantId(TENANT_ID);
            dft.setLabel("default");
            dft.setName("默认意图路由");
            dft.setDescription("作为其他意图的默认模板，编辑后会同步到未自定义的意图");
            dft.setTargetModels(List.of());
            dft.setModelWeights(new LinkedHashMap<>());
            dft.setSortOrder(0);
            dft.setEnabled(1);
            dft.setIsDefault(1);
            dft.setCustomized(0);
            intentMapper.insert(dft);
            log.info("[Init] Seeded default intent config");
        }

        // 如果只有默认意图，则创建预设意图
        Long count = intentMapper.selectCount(null);
        if (count != null && count > 1) return;
        int order = 1;
        seed("reasoning", "推理思考", "逻辑分析、数学计算、复杂推理", order++);
        seed("casual_chat", "日常聊天", "闲聊、问候、简单对话", order++);
        seed("planning", "项目规划", "架构设计、方案拆解、任务规划", order++);
        seed("simple_instruction", "简单执行", "指令跟随、格式转换、简单任务", order++);
        seed("coding_review", "代码开发与审查", "编程、调试、代码审查、技术问答", order++);
        seed("long_context_summary", "长文本处理与摘要", "摘要、总结、翻译、长文处理", order++);
        seed("creative_writing", "创意写作与角色扮演", "文案、创意写作、角色扮演、润色", order++);
        seed("structured_extraction", "结构化输出与数据提取", "结构化输出、信息抽取、数据整理", order++);
        log.info("[Init] Seeded {} intent configs", order - 1);
    }

    /**
     * 创建单个预设意图配置。
     *
     * @param label       意图标签（英文标识）
     * @param name        意图名称（中文显示名）
     * @param description 意图描述
     * @param sortOrder   排序顺序
     */
    private void seed(String label, String name, String description, int sortOrder) {
        IntentConfigDO dO = new IntentConfigDO();
        dO.setTenantId(TENANT_ID);
        dO.setLabel(label);
        dO.setName(name);
        dO.setDescription(description);
        dO.setTargetModels(List.of());
        dO.setModelWeights(new LinkedHashMap<>());
        dO.setSortOrder(sortOrder);
        dO.setEnabled(1);
        dO.setIsDefault(0);
        dO.setCustomized(0);
        intentMapper.insert(dO);
    }

    /**
     * 创建意图路由规则。
     * 按意图路由：意图评估模型分析请求意图，从 intent_config 表读取意图目录与用户配置的模型权重进行路由。
     */
    private void createIntentRoute() {
        RouteRule rule = new RouteRule();
        rule.setTenantId(TENANT_ID);
        rule.setRuleName("Intent Route");
        rule.setMatchType("intent");
        rule.setMatchPattern("*");
        rule.setTargetKeyIds(List.of());
        rule.setStrategy("weight");
        rule.setIntentModel("deepseek-v4-flash");
        rule.setFallbackEnabled(true);
        rule.setMaxFallback(2);
        rule.setPriority(10);
        rule.setEnabled(true);
        rule.setDescription("按意图路由：意图评估模型分析请求意图，从 intent_config 表读取意图目录与用户配置的模型权重，路由到最适合的模型；未配置时继承 Auto Route 的全部 Key 与默认权重。");
        ruleRepository.save(rule);
        log.info("[Init] Intent route created (intent catalog from intent_config table)");
    }

    /**
     * 创建自动路由规则。
     * 自动路由到所有已配置的 API Key，使用加权策略。
     */
    private void createAutoRoute() {
        RouteRule rule = new RouteRule();
        rule.setTenantId(TENANT_ID);
        rule.setRuleName("Auto Route");
        rule.setMatchType("model");
        rule.setMatchPattern("*");
        rule.setTargetKeyIds(List.of());
        rule.setStrategy("weight");
        rule.setFallbackEnabled(true);
        rule.setMaxFallback(2);
        rule.setPriority(100);
        rule.setEnabled(true);
        rule.setDescription("Auto-routes to all configured API keys.");
        ruleRepository.save(rule);
        log.info("[Init] Auto route created");
    }

    /**
     * 从首次启动向导的配置数据创建 API Key。
     * 读取 .setup-wizard.json 文件，解析供应商、API Key、Base URL、模型等信息。
     */
    private void createKeyFromSetup() {
        try {
            String json = SetupWizard.readSetupData();
            if (json == null) return;

            var node = JsonUtils.parse(json);

            // 构建 API Key 配置
            ApiKeyConfig config = new ApiKeyConfig();
            config.setTenantId(TENANT_ID);
            config.setName(node.path("provider").asText("deepseek") + " Key");
            config.setProvider(node.path("provider").asText("deepseek"));
            // 根据供应商判断协议
            config.setProtocol("anthropic".equalsIgnoreCase(config.getProvider()) ? "anthropic" : "openai");
            config.setApiKey(node.path("api_key").asText());
            config.setBaseUrl(node.path("base_url").asText());
            // 解析模型列表，构建模型映射（名称=真实模型名，用户可后续在 UI 修改）
            Map<String, String> modelMapping = new LinkedHashMap<>();
            node.path("models").forEach(m -> {
                String modelName = m.asText();
                modelMapping.put(modelName, modelName);
            });
            config.setModelMapping(modelMapping);
            // 设置默认参数
            config.setPriority(0);
            config.setMaxConcurrent(10);
            config.setTimeoutMs(30000);
            config.setRetryCount(1);
            config.setStatus(1);
            config.setHealthStatus("unknown");

            keyRepository.save(config);
            // 将模型写入 model_config 表
            if (config.getModelMapping() != null && !config.getModelMapping().isEmpty()) {
                for (Map.Entry<String, String> entry : config.getModelMapping().entrySet()) {
                    ModelConfig mc = new ModelConfig();
                    mc.setTenantId(TENANT_ID);
                    mc.setDisplayName(entry.getKey());
                    mc.setRealName(entry.getValue());
                    mc.setApiKeyId(config.getId());
                    modelConfigRepository.save(mc);
                }
            }
            log.info("[Init] API Key '{}' created from setup wizard (provider={}, model_mapping={})",
                    config.getName(), config.getProvider(), config.getModelMapping());
        } catch (Exception e) {
            log.warn("[Init] Failed to create key from setup data: {}", e.getMessage());
        }
    }

    /**
     * 打印启动横幅信息。
     * 显示服务地址、认证 Token、API Key 数量、管理接口和代理接口路径。
     * 如果没有配置 API Key，则显示添加 API Key 的示例命令。
     */
    private void printStartupBanner() {
        List<ApiKeyConfig> keys = keyRepository.findByTenantId(TENANT_ID);
        long activeKeys = keys.stream().filter(k -> k.getStatus() != null && k.getStatus() == 1).count();

        String authToken = System.getProperty("miniapi.router.auth-token", "sk-miniapi-standalone");
        String port = System.getProperty("server.port", "9090");

        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║       MiniAPIRouter Standalone 已就绪      ║");
        System.out.println("  ╠══════════════════════════════════════════╣");
        System.out.printf( "  ║  服务地址  : http://localhost:%-11s║%n", port);
        System.out.printf( "  ║  认证 Token : %-30s║%n", truncate(authToken, 30));
        System.out.printf( "  ║  API Keys  : %-30d║%n", activeKeys);
        System.out.println("  ╠══════════════════════════════════════════╣");
        System.out.println("  ║  管理接口: /api/v1/config/*              ║");
        System.out.println("  ║  代理接口: /v1/chat/completions           ║");
        System.out.println("  ║            /v1/messages                   ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        // 如果没有活跃的 API Key，显示添加示例
        if (activeKeys == 0) {
            System.out.println();
            System.out.println("  ⚠ 尚未配置 API Key。请通过 API 添加:");
            System.out.println("    curl -X POST http://localhost:" + port + "/api/v1/config/api-keys \\");
            System.out.println("      -H 'Authorization: Bearer " + authToken + "' \\");
            System.out.println("      -H 'Content-Type: application/json' \\");
            System.out.println("      -d '{\"name\":\"My Key\",\"provider\":\"deepseek\",\"api_key\":\"sk-xxx\",\"base_url\":\"https://api.deepseek.com\",\"model_mapping\":{\"deepseek\":\"deepseek-v4-flash\"}}'");
        }
        System.out.println();
    }

    /**
     * 截断字符串到指定长度，超出部分用 "..." 替代。
     *
     * @param s   原始字符串
     * @param max 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
