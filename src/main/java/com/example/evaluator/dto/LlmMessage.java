package com.example.evaluator.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A single chat message shared by both request and response payloads.
 * Mirrors the OpenAI / DO Inference API message schema.
 */
public record LlmMessage(
        @NotBlank String role,
        @NotBlank String content) {}
