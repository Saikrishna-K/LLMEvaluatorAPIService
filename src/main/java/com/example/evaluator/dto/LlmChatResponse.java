package com.example.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response shape returned by the DO Inference API (OpenAI-compatible).
 * @JsonIgnoreProperties(ignoreUnknown=true) prevents failures on unexpected fields
 * that different models may include.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatResponse(
        String id,
        String object,
        Long created,
        String model,
        List<LlmChoice> choices,
        LlmUsage usage) {

    /** Convenience: extracts the assistant content from the first choice. */
    public String firstChoiceContent() {
        if (choices == null || choices.isEmpty()) return null;
        LlmChoice first = choices.get(0);
        if (first == null || first.message() == null) return null;
        return first.message().content();
    }
}
