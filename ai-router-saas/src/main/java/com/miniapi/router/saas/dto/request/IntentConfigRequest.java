package com.miniapi.router.saas.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record IntentConfigRequest(
        String label,
        String name,
        String description,
        @JsonProperty("target_models") List<String> targetModels,
        @JsonProperty("model_weights") Map<String, Integer> modelWeights,
        @JsonProperty("sort_order") Integer sortOrder,
        Boolean enabled,
        @JsonProperty("is_default") Boolean isDefault,
        Boolean customized) {
}
