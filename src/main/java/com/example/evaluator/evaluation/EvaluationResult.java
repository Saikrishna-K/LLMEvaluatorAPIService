package com.example.evaluator.evaluation;

/**
 * Outcome of a single primary-vs-candidate comparison.
 * MATCH is the only outcome that increments exact_match_count.
 */
public enum EvaluationResult {
    MATCH,
    MISMATCH,
    PRIMARY_UNPARSEABLE,
    CANDIDATE_UNPARSEABLE,
    BOTH_UNPARSEABLE
}
