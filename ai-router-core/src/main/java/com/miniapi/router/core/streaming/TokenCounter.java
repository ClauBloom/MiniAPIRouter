package com.miniapi.router.core.streaming;

/**
 * Token 估算器：对文本进行启发式 token 数量估算（不等同于精确的 tokenizer）。
 * 针对中文字符（CJK）和非中文字符采用不同的折算比例，提供近似的 token 数量，
 * 用于用量统计、日志记录等场景。
 *
 * <p>估算规则：
 * <ul>
 *   <li>CJK 字符（U+4E00~U+9FFF 及其他 >0x7F 字符）：每 1.5 个字符计 1 token</li>
 *   <li>其他字符（ASCII/拉丁等）：每 4 个字符计 1 token</li>
 * </ul>
 */
public class TokenCounter {

    private TokenCounter() {}

    /**
     * 估算文本的 token 数量
     * @param text 待估算的文本
     * @return 估算的 token 数
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;      // CJK 及宽字符计数
        int other = 0;    // ASCII 等窄字符计数
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;          // 中日韩统一表意文字
            else if (c > 0x7F) cjk++;                        // 其他非 ASCII 字符也按 CJK 估算
            else other++;
        }
        /* CJK 字符约 1.5 个/Token，英文约 4 个/Token，向上取整 */
        return (int) Math.ceil(cjk / 1.5 + other / 4.0);
    }

    /**
     * 估算消息 JSON 字符串的 token 数量（prompt 令牌估算）
     * @param messagesJson 消息列表的 JSON 字符串
     * @return 估算的 token 数
     */
    public static int estimateMessages(String messagesJson) {
        if (messagesJson == null) return 0;
        return estimate(messagesJson);
    }
}
