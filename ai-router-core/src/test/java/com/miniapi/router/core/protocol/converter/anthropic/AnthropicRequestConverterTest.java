package com.miniapi.router.core.protocol.converter.anthropic;

import com.miniapi.router.core.protocol.UnifiedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnthropicRequestConverter 消息归一化测试：
 * 验证 OpenAI 风格的多轮工具调用历史能被正确转换为 Anthropic 协议格式。
 */
public class AnthropicRequestConverterTest {

    private final AnthropicRequestConverter converter = new AnthropicRequestConverter();

    @Test
    @DisplayName("buildUpstreamRequest: 应将 OpenAI 风格多轮工具调用历史转换为 Anthropic tool_use/tool_result 格式")
    void should_normalizeOpenAiToolUseHistory_to_anthropicBlocks() {
        // Arrange: user -> assistant(tool_calls) -> tool 结果
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "查看当前目录文件");

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "bash");
        fn.put("arguments", "{\"command\": \"ls -la\"}");
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call_001");
        toolCall.put("type", "function");
        toolCall.put("function", fn);

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "我来执行命令。");
        assistantMsg.put("tool_calls", List.of(toolCall));

        Map<String, Object> toolResultMsg = new LinkedHashMap<>();
        toolResultMsg.put("role", "tool");
        toolResultMsg.put("tool_call_id", "call_001");
        toolResultMsg.put("content", "total 0\ndrwxr-xr-x 2 root root 4096 .");

        UnifiedRequest req = new UnifiedRequest();
        req.setModel("claude-3-5-sonnet");
        req.setMessages(List.of(userMsg, assistantMsg, toolResultMsg));
        req.setStream(true);

        // Act
        Map<String, Object> body = converter.buildUpstreamRequest(req, "anthropic");

        // Assert
        assertThat(body.get("model")).isEqualTo("claude-3-5-sonnet");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages).hasSize(3);

        // 1. user 消息原样保留
        assertThat(messages.get(0).get("role")).isEqualTo("user");
        assertThat(messages.get(0).get("content")).isEqualTo("查看当前目录文件");

        // 2. assistant: tool_calls -> tool_use 内容块
        Map<String, Object> normalizedAssistant = messages.get(1);
        assertThat(normalizedAssistant.get("role")).isEqualTo("assistant");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assistantBlocks = (List<Map<String, Object>>) normalizedAssistant.get("content");
        assertThat(assistantBlocks).hasSize(2);
        assertThat(assistantBlocks.get(0).get("type")).isEqualTo("text");
        assertThat(assistantBlocks.get(0).get("text")).isEqualTo("我来执行命令。");
        assertThat(assistantBlocks.get(1).get("type")).isEqualTo("tool_use");
        assertThat(assistantBlocks.get(1).get("id")).isEqualTo("call_001");
        assertThat(assistantBlocks.get(1).get("name")).isEqualTo("bash");
        @SuppressWarnings("unchecked")
        Map<String, Object> toolInput = (Map<String, Object>) assistantBlocks.get(1).get("input");
        assertThat(toolInput).containsEntry("command", "ls -la");

        // 3. tool 结果 -> user 消息中的 tool_result 内容块
        Map<String, Object> normalizedResult = messages.get(2);
        assertThat(normalizedResult.get("role")).isEqualTo("user");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultBlocks = (List<Map<String, Object>>) normalizedResult.get("content");
        assertThat(resultBlocks).hasSize(1);
        assertThat(resultBlocks.get(0).get("type")).isEqualTo("tool_result");
        assertThat(resultBlocks.get(0).get("tool_use_id")).isEqualTo("call_001");
        assertThat(resultBlocks.get(0).get("content")).isEqualTo("total 0\ndrwxr-xr-x 2 root root 4096 .");
    }

    @Test
    @DisplayName("buildUpstreamRequest: 应将内联 system 消息合并至独立 system 字段")
    void should_extractInlineSystemMessage_to_systemField() {
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是一个专业助手。");

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "你好");

        UnifiedRequest req = new UnifiedRequest();
        req.setModel("claude-3-5-sonnet");
        req.setMessages(List.of(systemMsg, userMsg));
        req.setSystemPrompt("全局系统提示词。");
        req.setStream(true);

        Map<String, Object> body = converter.buildUpstreamRequest(req, "anthropic");

        // system = systemPrompt + "\n\n" + 内联 system
        assertThat(body.get("system")).isEqualTo("全局系统提示词。\n\n你是一个专业助手。");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("role")).isEqualTo("user");
    }

    @Test
    @DisplayName("buildUpstreamRequest: 应将 OpenAI function 工具定义转换为 Anthropic input_schema 格式")
    void should_convertOpenAiToolDefinition_to_anthropicSchema() {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "bash");
        fn.put("description", "执行 shell 命令");
        fn.put("parameters", Map.of("type", "object", "properties", Map.of()));
        Map<String, Object> openAiTool = new LinkedHashMap<>();
        openAiTool.put("type", "function");
        openAiTool.put("function", fn);

        UnifiedRequest req = new UnifiedRequest();
        req.setModel("claude-3-5-sonnet");
        req.setMessages(List.of());
        req.setTools(List.of(openAiTool));
        req.setStream(true);

        Map<String, Object> body = converter.buildUpstreamRequest(req, "anthropic");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name")).isEqualTo("bash");
        assertThat(tools.get(0).get("description")).isEqualTo("执行 shell 命令");
        assertThat(tools.get(0).get("input_schema")).isEqualTo(Map.of("type", "object", "properties", Map.of()));
        assertThat(tools.get(0)).doesNotContainKey("type");
        assertThat(tools.get(0)).doesNotContainKey("function");
    }
}
