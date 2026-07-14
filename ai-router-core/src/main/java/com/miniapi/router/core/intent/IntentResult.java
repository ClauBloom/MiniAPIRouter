package com.miniapi.router.core.intent;

import lombok.Data;
import lombok.Builder;

/**
 * 意图评估结果，包含意图标签、复杂度评分、推理说明以及是否为特殊意图的标记。
 * <p>
 * 当识别到特殊意图（如无效继续指令、追问）时，调用方可据此决定是否走管线降级逻辑。
 */
@Data
@Builder
public class IntentResult {
    /** 意图标签名称，如 "code_generation"、"question_answering" 等 */
    private String intent;
    /** 复杂度评分，范围 1-100 */
    private int score;
    /** 模型给出的推理说明 */
    private String reasoning;
    /** 是否为特殊意图（无效继续指令、追问等），需要走降级逻辑 */
    @Builder.Default
    private boolean specialIntent = false;

    /**
     * 创建一个标记为特殊意图的结果。
     *
     * @param intent    意图标签
     * @param score     复杂度评分
     * @param reasoning 推理说明
     * @return 特殊意图评估结果
     */
    public static IntentResult special(String intent, int score, String reasoning) {
        IntentResult r = builder().intent(intent).score(score).reasoning(reasoning).build();
        r.specialIntent = true;
        return r;
    }
}
