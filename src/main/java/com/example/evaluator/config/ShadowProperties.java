package com.example.evaluator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounded-concurrency and evaluation settings for the shadow executor.
 *
 * evaluationMode: "action-field" (default) or "json-equality"
 *   - action-field: extracts the "action" key from both responses and exact-matches it (Phase 2)
 *   - json-equality: asserts full JSON structural equality (Phase 1)
 */
@ConfigurationProperties(prefix = "shadow")
public record ShadowProperties(
        int queueCapacity,
        int poolCoreSize,
        int poolMaxSize,
        String evaluationMode) {}
