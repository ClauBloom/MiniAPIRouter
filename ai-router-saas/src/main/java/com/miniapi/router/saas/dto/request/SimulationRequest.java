package com.miniapi.router.saas.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimulationRequest(
        String model,
        String intent,
        @JsonProperty("complexity") Integer complexity,
        @JsonProperty("agent_type") String agentType) {
}
