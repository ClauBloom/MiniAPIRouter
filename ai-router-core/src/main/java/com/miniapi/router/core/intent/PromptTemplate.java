package com.miniapi.router.core.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.miniapi.router.core.domain.IntentConfig;
import com.miniapi.router.core.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提示词模板引擎，负责构建意图评估所需的 system prompt 和 user prompt，
 * 同时提供从对话历史中提取用户提问和 Agent 活动摘要的工具方法。
 * <p>
 * system prompt 包含评分维度、评分参考、修正规则和意图标签目录等详细指导，
 * 引导大模型准确地进行意图分类和复杂度评分。
 */
@Component
public class PromptTemplate {

    /**
     * 构建意图评估的 system prompt，动态注入租户配置的意图标签目录。
     * 包含评分维度、校准示例、修正规则和 Agent 活动状态修正规则等详细指导。
     *
     * @param catalog 租户配置的意图分类列表
     * @return 完整的 system prompt 字符串
     */
    public String buildSystemPrompt(List<IntentConfig> catalog) {
        // 构建意图标签的逗号分隔列表
        StringBuilder labels = new StringBuilder();
        for (IntentConfig i : catalog) {
            if (!Boolean.TRUE.equals(i.getEnabled())) continue;
            if (labels.length() > 0) labels.append(",\n            ");
            labels.append(i.getLabel()).append("(").append(i.getName());
            if (i.getDescription() != null && !i.getDescription().isBlank()) {
                labels.append("/").append(i.getDescription());
            }
            labels.append(")");
        }
        String template = """
                你是AI路由调度专家。你的任务是将用户问题归类到最匹配的意图标签，并评估问题复杂度(1-100)。

                ## 评分维度（综合考量以下四点）
                1. 推理深度：需要多少步逻辑推导？是否需要形式化证明？
                2. 领域知识：需要多少专业背景知识？
                3. 输出复杂度：输出结构有多复杂？代码量多大？
                4. 约束条件：需要同时满足多少个约束？

                ## 评分原则
                1. 准确评估，不要刻意偏高或偏低。大多数日常编码任务的真实复杂度在20-60之间。
                2. 文件数量多不等于复杂度高。批量添加注释、批量修改格式、批量重命名等操作即使涉及几十个文件，复杂度仍然很低。
                3. 以下评分参考区间仅作为定性参考，实际评分应根据具体任务性质判断，不要盲目向上取整。

                ## 多模块/多文件区分规则（重要）
                区分"物理多文件"和"逻辑多模块"这两个完全不同的概念：
                - 物理多文件：操作多个文件，但文件之间无耦合关系、不需要理解文件间的接口契约或数据流。例如：扫描20个文件并添加注释、批量格式化100个文件、跨模块搜索特定内容。这类任务的复杂度取决于单文件操作的难度，不由文件数量决定。
                - 逻辑多模块：需要理解并修改多个模块之间的接口契约、数据流或依赖关系。例如：修改一个接口定义需要同步更新所有实现类、重构跨模块的公共抽象层、解决多模块间的循环依赖问题。此类任务因涉及跨模块耦合约束而真正复杂。

                ## 复杂度评分参考（附校准示例）
                - 90-100：极高难度。逻辑多模块耦合场景：跨系统的分布式调试与一致性修复、多模块接口契约重构、深度领域知识的极端Bug排查（如并发竞态、内存泄漏）。单纯物理上涉及多个文件通常不在这一区间。
                  示例：排查分布式系统中的数据一致性问题(92)、重构20+模块的遗留代码库且需保持接口兼容(90)、形式化验证分布式一致性算法(97)、定位并修复高并发下的数据竞态Bug(93)
                - 75-89：复杂实现。需要同时满足多个严格约束条件的编程任务、系统架构设计、涉及逻辑多模块耦合的功能实现或重构、需要深入分析根因的复杂bug修复。
                  示例：实现带泛型+线程安全+TTL的LRU缓存(82)、设计多租户微服务架构(78)、跨模块接口重构且需保持向后兼容(85)
                - 60-74：功能实现（中等偏上）。用户要求实现某功能--涉及业务逻辑、接口、流程的变更或新增。60-65为简单功能实现（单一接口/单一逻辑），66-74为复杂功能实现（多接口/多逻辑协作）。
                  示例：添加用户注册登录功能(62)、实现订单状态机(68)、对接第三方支付网关(73)
                - 40-59：创意与转换。有约束的创意写作、复杂文本翻译、长文摘要、需要一定理解力的文档编写。
                  示例：写带修辞手法的抒情散文(45)、翻译技术文档(52)、总结万字长文(55)、编写复杂技术设计文档(58)
                - 20-39：简单执行--文件操作与批处理。不涉及业务逻辑变更的文件创建、修改、删除、格式整理、添加注释、编写模板代码、简单配置修改。物理多文件类型的批量操作若单文件任务本身简单，通常在此区间；若单文件任务本身需要分析理解则适度上调。
                  示例：创建新文件并写入模板代码(22)、修改配置文件(25)、批量添加注释到多个文件(28)、扫描30+个文件并补充注释(25)、删除无用文件(20)
                - 1-19：极简交互。日常闲聊、简单问候、一句话指令。
                  示例："你好"(5)、"今天天气怎么样"(12)、"帮我重启服务"(18)

                ## 意图分类校准规则
                1. 添加注释、格式化代码、编写模板代码 -> simple_instruction（不是coding_review）
                2. 读取文件、搜索代码、探索项目结构 -> simple_instruction（不是coding_review）
                3. 创建文件、删除文件、重命名 -> simple_instruction（不是coding_review）
                4. 只有用户要求审查代码质量、排查bug、分析潜在问题 时才归类为 coding_review
                5. 只有需要多步逻辑推理、架构设计、方案论证时才归类为 reasoning
                6. 编写新功能/实现业务逻辑 -> coding（如果有此标签）或 coding_review

                ## 修正规则
                1. 当用户明确要求修bug、指出运行错误（如"有bug"、"运行报错"、"结果不对"）时，在当前评估基础上适当上浮（通常+10-15），但不必强制达到75。
                  注意：仅在用户明确表达了对当前结果的不满或错误时才适用，单纯的"修改"或"更新"不算bug修复。
                2. 当用户情绪激动（如使用反问句、感叹号、指责语气）时，额外+5分。

                ## Agent活动状态修正规则（仅当"Agent最近活动"非空时适用）
                  重要提示：Agent活动信息仅提供上下文参考，评分核心依据仍是用户提问本身的复杂度。不要因为Agent读取文件数量多、派发了子Agent、或操作中遇到文件读取错误就从Agent活动侧加分。
                  1. 如果Agent正在修改代码（edit/write）且用户要求的是复杂逻辑变更 -> 可适当+5-10分
                  2. 如果Agent正在修改代码（edit/write）但用户要求的是简单操作（注释、格式、模板） -> 不加分，按用户提问正常评估
                  3. 如果Agent正在执行命令（bash）-> 不直接加分
                  4. 如果Agent派发了子Agent（task）-> 不直接加分，评分应基于用户提问内容本身评估
                  5. 如果Agent仅读取代码未修改 -> 不加分
                  6. Agent操作中出现的文件读取失败等"错误"属于正常探索行为，不加分
                  7. Agent读取的文件数量多少不直接影响评分

                ## 意图标签参考
                {{LABELS}},
                other(其他/无法归类),
                invalid_continuation(无效继续/执行触发指令：不携带新任务信息的简短指令，其复杂度应由上文对话上下文决定而非指令本身。包括："继续"、"开始"、"执行"、"go"、"start"、"run"、"进行"、"begin"等纯粹触发上文任务的短语),
                follow_up(追问/不满意：如"然后呢"、"还有呢"、"为什么"、"具体一点"等简短追问，表示用户对前序回答不满足但未给出具体任务)

                ## 输出要求
                严格输出以下JSON格式，不要包含任何其他内容、不要使用markdown代码块：
                {"intent":"意图标签","score":复杂度分数,"reasoning":"一句话分析"}

                注意：
                1. 只输出JSON，不要有任何额外文字
                2. score必须是1-100之间的整数，禁止小数""";
        return template.replace("{{LABELS}}", labels.toString());
    }

    /**
     * 构建意图评估的 user prompt，包含用户提问和 Agent 最近活动摘要。
     *
     * @param candidates          候选列表（当前未在实际内容中使用，保留供扩展）
     * @param userQuestion        用户最新提问
     * @param agentActivitySummary Agent 最近活动的摘要文本
     * @return 完整的 user prompt 字符串
     */
    public String buildUserPrompt(List<?> candidates, String userQuestion, String agentActivitySummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户提问\n").append(userQuestion).append("\n\n");
        if (agentActivitySummary != null && !agentActivitySummary.isBlank()) {
            sb.append("## Agent最近活动\n").append(agentActivitySummary).append("\n\n");
        }
        sb.append("请结合用户提问和Agent活动状态评估复杂度(1-100)和意图。");
        return sb.toString();
    }

    /**
     * 构建包含完整对话历史的 prompt（预留方法，用于深度分析场景）。
     *
     * @param candidates 候选列表
     * @param messages   完整对话消息历史
     * @return 包含完整对话历史的 prompt 字符串
     */
    public String buildFullHistoryPrompt(List<?> candidates, List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 完整对话历史\n");
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            Object content = msg.get("content");
            if (content == null) continue;
            String roleLabel = "user".equals(role) ? "用户" : "assistant".equals(role) ? "助手" : role;
            sb.append("[").append(roleLabel).append("]: ").append(content.toString()).append("\n\n");
        }
        sb.append("\n请根据完整对话历史判断用户最新问题的真实意图并评估复杂度。");
        return sb.toString();
    }

    /**
     * 从对话历史中提取 Agent 最近活动的摘要信息。
     * 扫描最近的 6 条助手消息，统计读取、编辑、执行命令和派发子 Agent 的次数，
     * 并收集操作涉及的文件路径。
     *
     * @param messages 对话消息历史
     * @return Agent 活动摘要文本，若无活动则返回空字符串
     */
    public String extractAgentActivitySummary(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) return "";

        int scanCount = 0;
        int readCount = 0;
        int modifyCount = 0;
        int execCount = 0;
        int taskCount = 0;
        Set<String> files = new HashSet<>();

        // 从最新消息开始向前扫描，最多扫描 6 条
        for (int i = messages.size() - 1; i >= 0 && scanCount < 6; i--, scanCount++) {
            Map<String, Object> msg = messages.get(i);
            String role = (String) msg.get("role");

            if ("assistant".equals(role)) {
                Object tc = msg.get("tool_calls");
                if (tc instanceof List<?> toolCalls) {
                    for (Object tco : toolCalls) {
                        if (tco instanceof Map<?, ?> toolCall) {
                            Object funcObj = toolCall.get("function");
                            if (funcObj instanceof Map<?, ?> func) {
                                String name = (String) func.get("name");
                                if (name != null) {
                                    String lower = name.toLowerCase();
                                    // 按工具类型分类统计
                                    if (Set.of("read", "grep", "glob").contains(lower)) {
                                        readCount++;
                                    } else if (Set.of("edit", "write").contains(lower)) {
                                        modifyCount++;
                                    } else if ("bash".equals(lower)) {
                                        execCount++;
                                    } else if ("task".equals(lower)) {
                                        taskCount++;
                                    }
                                }
                                // 提取操作涉及的文件路径
                                Object argsObj = func.get("arguments");
                                if (argsObj != null) {
                                    String argsStr = null;
                                    if (argsObj instanceof String s) {
                                        argsStr = s;
                                    } else if (argsObj instanceof Map<?, ?> m) {
                                        try {
                                            argsStr = JsonUtils.toJson(m);
                                        } catch (Exception ignored) {
                                        }
                                    }
                                    if (argsStr != null && !argsStr.isBlank()) {
                                        try {
                                            JsonNode argsNode = JsonUtils.parse(argsStr);
                                            JsonNode fp = argsNode.get("filePath");
                                            if (fp != null && fp.isTextual()) {
                                                files.add(fp.asText());
                                            }
                                            JsonNode path = argsNode.get("path");
                                            if (path != null && path.isTextual()) {
                                                files.add(path.asText());
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        // 组装中文摘要文本
        List<String> parts = new ArrayList<>();
        if (readCount > 0) parts.add("读取了" + readCount + "个文件");
        if (modifyCount > 0) parts.add("执行了" + modifyCount + "次编辑/写入");
        if (execCount > 0) parts.add("执行了" + execCount + "次命令");
        if (taskCount > 0) parts.add("派发了子Agent处理复杂任务");

        if (parts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(String.join("、", parts)).append("。");
        if (modifyCount > 0) {
            sb.append("正在修改代码。");
        } else if (readCount > 0 && modifyCount == 0) {
            sb.append("仍在探索项目结构。");
        }
        if (files.size() > 1) {
            sb.append("涉及").append(files.size()).append("个不同文件。");
        }

        String result = sb.toString();
        // 限制摘要长度不超过 150 字符
        return result.length() > 150 ? result.substring(0, 147) + "..." : result;
    }

    /**
     * 从对话历史中提取最近一条用户提问内容。
     * 从最新消息向前查找，返回第一条角色为 "user" 的非空消息内容。
     *
     * @param messages 对话消息历史
     * @return 用户提问文本，若不存在则返回空字符串
     */
    public String extractUserQuestion(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            Object role = msg.get("role");
            Object content = msg.get("content");
            if ("user".equals(role) && content != null && !content.toString().isBlank()) {
                return content.toString().trim();
            }
        }
        return "";
    }
}
