package com.example.evaluator.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEqualityEvaluatorTest {

    private JsonEqualityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new JsonEqualityEvaluator(new ObjectMapper());
    }

    @Test
    void identicalJsonObjects_returnsMatch() {
        String json = "{\"action\":\"greet\",\"text\":\"Hello\"}";
        assertThat(evaluator.evaluate(json, json)).isEqualTo(EvaluationResult.MATCH);
    }

    @Test
    void differentFieldValues_returnsMismatch() {
        String primary   = "{\"action\":\"greet\"}";
        String candidate = "{\"action\":\"farewell\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MISMATCH);
    }

    @Test
    void extraFieldInCandidate_returnsMismatch() {
        String primary   = "{\"action\":\"greet\"}";
        String candidate = "{\"action\":\"greet\",\"extra\":\"field\"}";
        assertThat(evaluator.evaluate(primary, candidate)).isEqualTo(EvaluationResult.MISMATCH);
    }

    @Test
    void bothUnparseable_returnsBothUnparseable() {
        assertThat(evaluator.evaluate("not json", "also not json"))
                .isEqualTo(EvaluationResult.BOTH_UNPARSEABLE);
    }

    @Test
    void primaryUnparseable_returnsPrimaryUnparseable() {
        assertThat(evaluator.evaluate("not json", "{\"action\":\"greet\"}"))
                .isEqualTo(EvaluationResult.PRIMARY_UNPARSEABLE);
    }

    @Test
    void candidateUnparseable_returnsCandidateUnparseable() {
        assertThat(evaluator.evaluate("{\"action\":\"greet\"}", "not json"))
                .isEqualTo(EvaluationResult.CANDIDATE_UNPARSEABLE);
    }

    @Test
    void nullPrimary_returnsPrimaryUnparseable() {
        assertThat(evaluator.evaluate(null, "{\"action\":\"greet\"}"))
                .isEqualTo(EvaluationResult.PRIMARY_UNPARSEABLE);
    }

    @Test
    void blankBoth_returnsBothUnparseable() {
        assertThat(evaluator.evaluate("", "   "))
                .isEqualTo(EvaluationResult.BOTH_UNPARSEABLE);
    }

    @Test
    void markdownFencedJson_strippedAndMatches() {
        String fenced = "```json\n{\"action\":\"greet\"}\n```";
        String plain  = "{\"action\":\"greet\"}";
        assertThat(evaluator.evaluate(fenced, plain)).isEqualTo(EvaluationResult.MATCH);
    }

    @Test
    void jsonArrays_matchWhenEqual() {
        String json = "[1,2,3]";
        assertThat(evaluator.evaluate(json, json)).isEqualTo(EvaluationResult.MATCH);
    }

    @Test
    void stripMarkdownFences_removesCodeBlock() {
        String input    = "```json\n{\"k\":\"v\"}\n```";
        String expected = "{\"k\":\"v\"}";
        assertThat(JsonEqualityEvaluator.stripMarkdownFences(input)).isEqualTo(expected);
    }

    @Test
    void stripMarkdownFences_noFences_returnsUnchanged() {
        String input = "{\"k\":\"v\"}";
        assertThat(JsonEqualityEvaluator.stripMarkdownFences(input)).isEqualTo(input);
    }
}
