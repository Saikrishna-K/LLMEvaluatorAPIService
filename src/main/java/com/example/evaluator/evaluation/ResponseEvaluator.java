package com.example.evaluator.evaluation;

/**
 * Strategy interface for deterministic, heuristic evaluation of LLM response content strings.
 * Implementations must be stateless and thread-safe.
 */
public interface ResponseEvaluator {

    /**
     * @param primaryContent   assistant content string from the primary LLM
     * @param candidateContent assistant content string from the candidate LLM
     * @return deterministic comparison result
     */
    EvaluationResult evaluate(String primaryContent, String candidateContent);
}
