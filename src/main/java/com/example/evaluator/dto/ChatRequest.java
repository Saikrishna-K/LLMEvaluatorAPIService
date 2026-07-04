package com.example.evaluator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload accepted by POST /v1/chat.
 * model is optional — falls back to the configured primary/candidate default model.
 */
public record ChatRequest(
        String model,
        @NotEmpty @Valid List<LlmMessage> messages,
        Double temperature) {}
