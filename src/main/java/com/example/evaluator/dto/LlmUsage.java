package com.example.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LlmUsage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens) {}
