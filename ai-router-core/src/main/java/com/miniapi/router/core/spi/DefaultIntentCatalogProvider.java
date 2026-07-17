package com.miniapi.router.core.spi;

import com.miniapi.router.core.domain.IntentConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认意图分类目录提供者。
 * <p>
 * 内置了一套默认的意图分类体系（推理思考、日常聊天、项目规划、简单执行、
 * 代码开发、长文本摘要、创意写作、结构化提取），作为 {@link IntentCatalogProvider} 的默认实现。
 * 当未配置自定义意图目录时，使用此默认目录。
 * </p>
 */
@Component
public class DefaultIntentCatalogProvider implements IntentCatalogProvider {

    /** 预构建的默认意图分类目录（静态不可变列表） */
    private static final List<IntentConfig> DEFAULT_CATALOG = buildDefaultCatalog();

    @Override
    public List<IntentConfig> findAll(Long tenantId) {
        return DEFAULT_CATALOG;
    }

    @Override
    public IntentConfig findByLabel(Long tenantId, String label) {
        if (label == null) return null;
        return DEFAULT_CATALOG.stream()
                .filter(i -> label.equals(i.getLabel()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public IntentConfig findDefault(Long tenantId) {
        return null;
    }

    /** 构建默认意图分类列表（8 种预定义分类） */
    private static List<IntentConfig> buildDefaultCatalog() {
        return List.of(
                intent("reasoning", "推理思考", "逻辑分析、数学计算、复杂推理", 1),
                intent("casual_chat", "日常聊天", "闲聊、问候、简单对话", 2),
                intent("planning", "项目规划", "架构设计、方案拆解、任务规划", 3),
                intent("simple_instruction", "简单执行", "指令跟随、格式转换、简单任务", 4),
                intent("coding_review", "代码开发与审查", "编程、调试、代码审查、技术问答", 5),
                intent("long_context_summary", "长文本处理与摘要", "摘要、总结、翻译、长文处理", 6),
                intent("creative_writing", "创意写作与角色扮演", "文案、创意写作、角色扮演、润色", 7),
                intent("structured_extraction", "结构化输出与数据提取", "结构化输出、信息抽取、数据整理", 8)
        );
    }

    /** 便捷构建单个 IntentConfig 对象的工厂方法 */
    private static IntentConfig intent(String label, String name, String description, int sortOrder) {
        IntentConfig c = new IntentConfig();
        c.setLabel(label);
        c.setName(name);
        c.setDescription(description);
        c.setSortOrder(sortOrder);
        c.setEnabled(true);
        return c;
    }
}
