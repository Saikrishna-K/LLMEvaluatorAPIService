package com.example.evaluator.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Phase 1 evaluator: asserts that both responses parse as valid JSON
 * and are structurally equal (deep Jackson node equality).
 *
 * Strips common markdown code fences (``` json ... ```) before parsing.
 */
public class JsonEqualityEvaluator implements ResponseEvaluator {

    private final ObjectMapper objectMapper;

    public JsonEqualityEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EvaluationResult evaluate(String primaryContent, String candidateContent) {
        JsonNode primaryNode = tryParse(primaryContent);
        JsonNode candidateNode = tryParse(candidateContent);

        if (primaryNode == null && candidateNode == null) return EvaluationResult.BOTH_UNPARSEABLE;
        if (primaryNode == null) return EvaluationResult.PRIMARY_UNPARSEABLE;
        if (candidateNode == null) return EvaluationResult.CANDIDATE_UNPARSEABLE;

        return primaryNode.equals(candidateNode) ? EvaluationResult.MATCH : EvaluationResult.MISMATCH;
    }

    private JsonNode tryParse(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            return objectMapper.readTree(stripMarkdownFences(content.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    static String stripMarkdownFences(String text) {
        if (text == null) return null;
        String stripped = text.trim();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline != -1) {
                stripped = stripped.substring(firstNewline + 1);
            }
            if (stripped.endsWith("```")) {
                stripped = stripped.substring(0, stripped.length() - 3).trim();
            }
        }
        return stripped;
    }
}
