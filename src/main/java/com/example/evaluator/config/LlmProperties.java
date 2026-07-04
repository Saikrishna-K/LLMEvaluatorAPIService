package com.example.evaluator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from application.yml under the "llm" prefix.
 * API keys are injected via environment variables — never hard-coded.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(ModelConfig primary, ModelConfig candidate) {

    public record ModelConfig(
            String baseUrl,
            String apiKey,
            String defaultModel,
            long timeoutMs) {}
}
