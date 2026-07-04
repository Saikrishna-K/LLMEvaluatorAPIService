package com.example.evaluator.shadow;

import com.example.evaluator.dto.ChatRequest;
import com.example.evaluator.dto.LlmChatResponse;

/**
 * Immutable snapshot of the data needed to run a shadow evaluation.
 * Captured on the HTTP request thread; consumed by a shadow worker thread.
 */
public record ShadowTask(
        String correlationId,
        ChatRequest request,
        LlmChatResponse primaryResponse) {}
