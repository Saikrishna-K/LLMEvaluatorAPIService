package com.example.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LlmChoice(
        Integer index,
        LlmMessage message,
        @JsonProperty("finish_reason") String finishReason) {}
