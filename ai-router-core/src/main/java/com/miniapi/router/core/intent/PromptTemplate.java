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

@Component
public class PromptTemplate {

    public String buildSystemPrompt(List<IntentConfig> catalog) {
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
                1. 大胆使用全量程（1-100），不要集中在中间段。真实工程场景中大量问题难度完全值得90+。
                2. 以下是下限参考，实际评分应根据具体情况向上浮动，宁高勿低。

                ## 复杂度评分参考（附校准示例）
                - 90-100：极高难度。以下任一情况均应打90+：①跨多模块/多系统协同的复杂工程问题；②分布式系统调试、性能瓶颈定位、安全漏洞修复；③需要深度领域知识的Bug排查（如并发竞态、内存泄漏、数据不一致）；④需要多步严密逻辑推导、形式化证明、复杂数学论证的推理任务；⑤多文件大规模重构且需保持功能不变。
                  示例：排查分布式系统中的数据一致性问题(92)、重构20+模块的遗留代码库保持向后兼容(90)、实现完整WebSocket网关含鉴权限流多协议转换(91)、形式化验证分布式一致性算法(97)、定位并修复高并发下的数据竞态Bug(93)
                - 75-89：复杂实现。需要同时满足多个约束条件的编程任务、系统架构设计。用户明确要求修bug/修复错误（如"有这个bug"、"运行报错"、"结果不对，帮我改"）通常进入此区间或更高——因为需要排查根本原因+分析已有代码+修复且不能引入新问题。
                  示例：实现带泛型+线程安全+TTL的LRU缓存(82)、设计多租户微服务架构(78)、用新框架重写单模块CRUD服务(85)
                - 60-74：功能实现（中等偏上）。用户要求实现某功能——涉及业务逻辑、接口、流程的变更或新增，包含需求理解+方案设计+编码实现+正确性验证。60-65为简单功能实现（单一接口/单一逻辑），66-74为复杂功能实现（多接口/多逻辑协作）。
                  示例：添加用户注册登录功能(62)、实现订单状态机(68)、对接第三方支付网关(73)
                - 40-59：创意与转换。有约束的创意写作、复杂文本翻译、长文摘要。
                  示例：写带修辞手法的抒情散文(45)、翻译技术文档(52)、总结万字长文(55)
                - 20-39：简单执行——编写文件操作。不涉及业务逻辑变更的文件创建、修改、删除、格式整理、复制粘贴、简单配置修改。
                  示例：创建新文件并写入模板代码(22)、修改配置文件(25)、重命名变量/方法(28)、删除无用文件(20)
                - 1-19：极简交互。日常闲聊、简单问候、一句话指令。
                  示例："你好"(5)、"今天天气怎么样"(12)、"帮我重启服务"(18)

                ## 修正规则
                1. 当用户要求修bug、指出错误或表达对当前结果不满意（如"有bug"、"报错"、"不对"、"有问题"、"能不能改好"等）时，在当前评估基础上自动上浮一个评分档位（最低为75）。
                   理由：修bug意味着之前的产出质量不合格，需要更高质量的模型介入；且bug修复本身需额外付出定位根因、上下文理解、回归避免的成本。
                2. 当用户情绪激动（如使用反问句、感叹号、指责语气、语气词"又"、"还"、"到底"等）时，额外再加5-10分。
                   理由：用户已失去耐心，必须调用更强模型一次解决问题，否则可能彻底流失。

                ## Agent活动状态修正规则（仅当"Agent最近活动"非空时适用）
                1. 如果Agent正在修改代码（edit/write）→ 最低75分，有跨文件操作时额外+5-10
                2. 如果Agent正在执行命令（bash）→ 最低70分
                3. 如果Agent派发了子Agent（task）→ 最低80分，任务已被扩展
                4. 如果Agent最近操作出现错误 → +10分
                5. 如果Agent仅读取代码未修改 → 不加分，按用户提问正常评估
                6. 如果Agent读取了3个以上不同文件 → 额外+5
                7. 当Agent处于活跃编码状态时，follow_up/invalid_continuation不再直接降级：如果Agent正在修改代码，follow_up应保持评分不低于上次

                ## 意图标签参考
                {{LABELS}},
                other(其他/无法归类),
                invalid_continuation(无效继续指令：如"继续"、"接着说"、"继续写"等纯粹要求继续、不含满意度评判的短语),
                follow_up(追问/不满意：如"然后呢"、"还有呢"、"为什么"、"具体一点"等简短追问，表示用户对前序回答不满足但未给出具体任务)

                ## 输出要求
                严格输出以下JSON格式，不要包含任何其他内容、不要使用markdown代码块：
                {"intent":"意图标签","score":复杂度分数,"reasoning":"一句话分析"}

                注意：
                1. 只输出JSON，不要有任何额外文字
                2. score必须是1-100之间的整数，禁止小数""";
        return template.replace("{{LABELS}}", labels.toString());
    }

    public String buildUserPrompt(List<?> candidates, String userQuestion, String agentActivitySummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户提问\n").append(userQuestion).append("\n\n");
        if (agentActivitySummary != null && !agentActivitySummary.isBlank()) {
            sb.append("## Agent最近活动\n").append(agentActivitySummary).append("\n\n");
        }
        sb.append("请结合用户提问和Agent活动状态评估复杂度(1-100)和意图。");
        return sb.toString();
    }

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

    public String extractAgentActivitySummary(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) return "";

        int scanCount = 0;
        int readCount = 0;
        int modifyCount = 0;
        int execCount = 0;
        int taskCount = 0;
        boolean hasError = false;
        Set<String> files = new HashSet<>();

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

            if ("tool".equals(role)) {
                Object content = msg.get("content");
                if (content instanceof String s && (s.contains("error") || s.contains("Error")
                        || s.contains("fail") || s.contains("Fail") || s.contains("exception")
                        || s.contains("Exception") || s.contains("出错") || s.contains("失败"))) {
                    hasError = true;
                }
            }
        }

        List<String> parts = new ArrayList<>();
        if (readCount > 0) parts.add("读取了" + readCount + "个文件");
        if (modifyCount > 0) parts.add("执行了" + modifyCount + "次编辑/写入");
        if (execCount > 0) parts.add("执行了" + execCount + "次命令");
        if (taskCount > 0) parts.add("派发了子Agent处理复杂任务");
        if (hasError) parts.add("最近操作出现错误");

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
        return result.length() > 150 ? result.substring(0, 147) + "..." : result;
    }

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
