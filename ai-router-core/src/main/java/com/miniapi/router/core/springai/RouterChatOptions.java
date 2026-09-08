package com.miniapi.router.core.springai;

import com.miniapi.router.core.domain.AgentIdentity;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路由器专属 Spring AI ChatOptions（理念 D）。
 * 除标准模型参数与工具调用字段外，提供租户、Agent、意图提示及上游额外字段。
 */
public class RouterChatOptions implements ToolCallingChatOptions {

    private String model;
    private Double frequencyPenalty;
    private Integer maxTokens;
    private Double presencePenalty;
    private List<String> stopSequences;
    private Double temperature;
    private Integer topK;
    private Double topP;
    private List<ToolCallback> toolCallbacks = new ArrayList<>();
    private Set<String> toolNames = new HashSet<>();
    private Map<String, Object> toolContext = new HashMap<>();
    private Boolean internalToolExecutionEnabled;

    @Nullable private Long tenantId;
    @Nullable private AgentIdentity agentIdentity;
    @Nullable private String intentHint;
    @Nullable private String clientApiKey;
    @Nullable private Map<String, Object> extraBody;

    @Override public String getModel() { return model; }
    public void setModel(@Nullable String value) { model = value; }
    @Override public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(@Nullable Double value) { frequencyPenalty = value; }
    @Override public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(@Nullable Integer value) { maxTokens = value; }
    @Override public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(@Nullable Double value) { presencePenalty = value; }
    @Override public List<String> getStopSequences() { return stopSequences; }
    public void setStopSequences(@Nullable List<String> value) { stopSequences = value; }
    @Override public Double getTemperature() { return temperature; }
    public void setTemperature(@Nullable Double value) { temperature = value; }
    @Override public Integer getTopK() { return topK; }
    public void setTopK(@Nullable Integer value) { topK = value; }
    @Override public Double getTopP() { return topP; }
    public void setTopP(@Nullable Double value) { topP = value; }
    @Override public List<ToolCallback> getToolCallbacks() { return List.copyOf(toolCallbacks); }
    public void setToolCallbacks(List<ToolCallback> value) { toolCallbacks = value != null ? new ArrayList<>(value) : new ArrayList<>(); }
    @Override public Set<String> getToolNames() { return Set.copyOf(toolNames); }
    public void setToolNames(Set<String> value) { toolNames = value != null ? new HashSet<>(value) : new HashSet<>(); }
    @Override public Map<String, Object> getToolContext() { return Map.copyOf(toolContext); }
    public void setToolContext(Map<String, Object> value) { toolContext = value != null ? new HashMap<>(value) : new HashMap<>(); }
    @Override public Boolean getInternalToolExecutionEnabled() { return internalToolExecutionEnabled; }
    public void setInternalToolExecutionEnabled(@Nullable Boolean value) { internalToolExecutionEnabled = value; }

    @Nullable public Long getTenantId() { return tenantId; }
    public void setTenantId(@Nullable Long value) { tenantId = value; }
    @Nullable public AgentIdentity getAgentIdentity() { return agentIdentity; }
    public void setAgentIdentity(@Nullable AgentIdentity value) { agentIdentity = value; }
    @Nullable public String getIntentHint() { return intentHint; }
    public void setIntentHint(@Nullable String value) { intentHint = value; }
    @Nullable public String getClientApiKey() { return clientApiKey; }
    public void setClientApiKey(@Nullable String value) { clientApiKey = value; }
    @Nullable public Map<String, Object> getExtraBody() { return extraBody; }
    public void setExtraBody(@Nullable Map<String, Object> value) { extraBody = value != null ? new LinkedHashMap<>(value) : null; }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ChatOptions> T copy() {
        RouterChatOptions copy = new RouterChatOptions();
        copy.setModel(model); copy.setFrequencyPenalty(frequencyPenalty); copy.setMaxTokens(maxTokens);
        copy.setPresencePenalty(presencePenalty);
        copy.setStopSequences(stopSequences != null ? new ArrayList<>(stopSequences) : null);
        copy.setTemperature(temperature); copy.setTopK(topK); copy.setTopP(topP);
        copy.setToolCallbacks(toolCallbacks); copy.setToolNames(toolNames); copy.setToolContext(toolContext);
        copy.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
        copy.setTenantId(tenantId); copy.setAgentIdentity(agentIdentity); copy.setIntentHint(intentHint);
        copy.setClientApiKey(clientApiKey); copy.setExtraBody(extraBody);
        return (T) copy;
    }

    /** 合并运行时选项与默认选项：runtime 的非 null 标准字段覆盖 defaults。 */
    public static RouterChatOptions merge(@Nullable ToolCallingChatOptions runtime,
                                          @Nullable RouterChatOptions defaults) {
        RouterChatOptions merged = defaults != null ? defaults.copy() : new RouterChatOptions();
        if (runtime == null) return merged;
        if (runtime.getModel() != null) merged.setModel(runtime.getModel());
        if (runtime.getTemperature() != null) merged.setTemperature(runtime.getTemperature());
        if (runtime.getMaxTokens() != null) merged.setMaxTokens(runtime.getMaxTokens());
        if (runtime.getTopP() != null) merged.setTopP(runtime.getTopP());
        if (runtime.getTopK() != null) merged.setTopK(runtime.getTopK());
        if (runtime.getFrequencyPenalty() != null) merged.setFrequencyPenalty(runtime.getFrequencyPenalty());
        if (runtime.getPresencePenalty() != null) merged.setPresencePenalty(runtime.getPresencePenalty());
        if (runtime.getStopSequences() != null) merged.setStopSequences(new ArrayList<>(runtime.getStopSequences()));
        if (runtime.getInternalToolExecutionEnabled() != null) merged.setInternalToolExecutionEnabled(runtime.getInternalToolExecutionEnabled());
        List<ToolCallback> callbacks = new ArrayList<>(merged.getToolCallbacks()); callbacks.addAll(runtime.getToolCallbacks()); merged.setToolCallbacks(callbacks);
        Set<String> names = new HashSet<>(merged.getToolNames()); names.addAll(runtime.getToolNames()); merged.setToolNames(names);
        Map<String, Object> context = new LinkedHashMap<>(merged.getToolContext()); context.putAll(runtime.getToolContext()); merged.setToolContext(context);
        if (runtime instanceof RouterChatOptions r) {
            if (r.getTenantId() != null) merged.setTenantId(r.getTenantId());
            if (r.getAgentIdentity() != null) merged.setAgentIdentity(r.getAgentIdentity());
            if (r.getIntentHint() != null) merged.setIntentHint(r.getIntentHint());
            if (r.getClientApiKey() != null) merged.setClientApiKey(r.getClientApiKey());
            if (r.getExtraBody() != null) {
                Map<String, Object> extra = merged.getExtraBody() != null ? new LinkedHashMap<>(merged.getExtraBody()) : new LinkedHashMap<>();
                extra.putAll(r.getExtraBody()); merged.setExtraBody(extra);
            }
        }
        return merged;
    }

    public static Builder builder() { return new Builder(); }

    /** RouterChatOptions Builder。 */
    public static final class Builder {
        private final RouterChatOptions options = new RouterChatOptions();
        private Builder() {}
        public Builder model(String v) { options.setModel(v); return this; }
        public Builder temperature(Double v) { options.setTemperature(v); return this; }
        public Builder maxTokens(Integer v) { options.setMaxTokens(v); return this; }
        public Builder topP(Double v) { options.setTopP(v); return this; }
        public Builder topK(Integer v) { options.setTopK(v); return this; }
        public Builder frequencyPenalty(Double v) { options.setFrequencyPenalty(v); return this; }
        public Builder presencePenalty(Double v) { options.setPresencePenalty(v); return this; }
        public Builder stopSequences(List<String> v) { options.setStopSequences(v); return this; }
        public Builder toolCallbacks(List<ToolCallback> v) { options.setToolCallbacks(v); return this; }
        public Builder toolNames(Set<String> v) { options.setToolNames(v); return this; }
        public Builder toolContext(Map<String, Object> v) { options.setToolContext(v); return this; }
        public Builder internalToolExecutionEnabled(Boolean v) { options.setInternalToolExecutionEnabled(v); return this; }
        public Builder tenantId(Long v) { options.setTenantId(v); return this; }
        public Builder agentIdentity(AgentIdentity v) { options.setAgentIdentity(v); return this; }
        public Builder intentHint(String v) { options.setIntentHint(v); return this; }
        public Builder clientApiKey(String v) { options.setClientApiKey(v); return this; }
        public Builder extraBody(Map<String, Object> v) { options.setExtraBody(v); return this; }
        public RouterChatOptions build() { return options; }
    }
}
