package com.example.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body sent to the DO Serverless Inference API.
 * Uses @JsonInclude(NON_NULL) so null temperature / max_tokens are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmChatRequest(
        String model,
        List<LlmMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature) {}
