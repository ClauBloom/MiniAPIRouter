package com.miniapi.router.saas.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuotaAdjustmentRequest(
        @JsonProperty("quota_limit") Long quotaLimit,
        String reason) {
}
