package com.example.evaluator.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 2 evaluator: extracts the top-level "action" string from both JSON responses
 * and performs an exact, case-sensitive comparison.
 *
 * A missing or null "action" key is treated as unparseable for that side.
 */
public class ActionFieldEvaluator implements ResponseEvaluator {

    private final ObjectMapper objectMapper;

    public ActionFieldEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(String primaryContent, String candidateContent) {
        String primaryAction = extractAction(primaryContent);
        String candidateAction = extractAction(candidateContent);

        if (primaryAction == null && candidateAction == null) return EvaluationResult.BOTH_UNPARSEABLE;
        if (primaryAction == null) return EvaluationResult.PRIMARY_UNPARSEABLE;
        if (candidateAction == null) return EvaluationResult.CANDIDATE_UNPARSEABLE;

        return primaryAction.equals(candidateAction) ? EvaluationResult.MATCH : EvaluationResult.MISMATCH;
    }

    private String extractAction(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            String cleaned = JsonEqualityEvaluator.stripMarkdownFences(content.trim());
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode action = root.get("action");
            if (action == null || action.isNull() || !action.isTextual()) return null;
            return action.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
