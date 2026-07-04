package com.example.evaluator.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionFieldEvaluatorTest {

    private ActionFieldEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ActionFieldEvaluator(new ObjectMapper());
    }

    @Test
    void sameAction_returnsMatch() {
        String primary   = "{\"action\":\"greet\",\"text\":\"Hello\"}";
        String candidate = "{\"action\":\"greet\",\"text\":\"Hi there\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MATCH);
    }

    @Test
    void differentAction_returnsMismatch() {
        String primary   = "{\"action\":\"greet\"}";
        String candidate = "{\"action\":\"farewell\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MISMATCH);
    }

    @Test
    void actionIsCaseSensitive() {
        String primary   = "{\"action\":\"Greet\"}";
        String candidate = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MISMATCH);
    }

    @Test
    void missingActionInPrimary_returnsPrimaryUnparseable() {
        String primary   = "{\"text\":\"Hello\"}";
        String candidate = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(primary, candidate))
                .isEqualTo(EvaluationResult.PRIMARY_UNPARSEABLE);
    }

    @Test
    void missingActionInCandidate_returnsCandidateUnparseable() {
        String primary   = "{\"action\":\"greet\"}";
        String candidate = "{\"text\":\"Hello\"}";
        assertThat(evaluator.evaluate(primary, candidate))
                .isEqualTo(EvaluationResult.CANDIDATE_UNPARSEABLE);
    }

    @Test
    void nullActionValue_returnsUnparseable() {
        String primary   = "{\"action\":null}";
        String candidate = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(primary, candidate))
                .isEqualTo(EvaluationResult.PRIMARY_UNPARSEABLE);
    }

    @Test
    void nonStringAction_returnsUnparseable() {
        String primary   = "{\"action\":42}";
        String candidate = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(primary, candidate))
                .isEqualTo(EvaluationResult.PRIMARY_UNPARSEABLE);
    }

    @Test
    void bothUnparseable_returnsBothUnparseable() {
        assertThat(evaluator.evaluate("not json", "also not json"))
                .isEqualTo(EvaluationResult.BOTH_UNPARSEABLE);
    }

    @Test
    void nullInputs_returnsBothUnparseable() {
        assertThat(evaluator.evaluate(null, null))
                .isEqualTo(EvaluationResult.BOTH_UNPARSEABLE);
    }

    @Test
    void markdownFencedJson_extractsActionCorrectly() {
        String primary   = "```json\n{\"action\":\"greet\"}\n```";
        String candidate = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MATCH);
    }
}
