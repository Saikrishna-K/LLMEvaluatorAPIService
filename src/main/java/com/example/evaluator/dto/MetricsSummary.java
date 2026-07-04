package com.example.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload returned by GET /metrics.
 * exact_match_rate_percent = exact_match_count / shadow_executed * 100
 * (drops and errors are excluded from the denominator).
 */
public record MetricsSummary(
        @JsonProperty("requests_total") long requestsTotal,
        @JsonProperty("shadow_executed") long shadowExecuted,
        @JsonProperty("shadow_dropped") long shadowDropped,
        @JsonProperty("shadow_errors") long shadowErrors,
        @JsonProperty("shadow_timeouts") long shadowTimeouts,
        @JsonProperty("exact_match_count") long exactMatchCount,
        @JsonProperty("exact_match_rate_percent") double exactMatchRatePercent,
        String timestamp) {}
