package com.example.evaluator.metrics;

import com.example.evaluator.dto.MetricsSummary;
import com.example.evaluator.evaluation.EvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetricsTest {

    private EvaluationMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new EvaluationMetrics();
    }

    @Test
    void initialState_allCountersAreZero() {
        MetricsSummary summary = metrics.getSummary();
        assertThat(summary.requestsTotal()).isZero();
        assertThat(summary.shadowExecuted()).isZero();
        assertThat(summary.shadowDropped()).isZero();
        assertThat(summary.shadowErrors()).isZero();
        assertThat(summary.shadowTimeouts()).isZero();
        assertThat(summary.exactMatchCount()).isZero();
        assertThat(summary.exactMatchRatePercent()).isZero();
    }

    @Test
    void matchRate_zeroExecuted_returnsZeroPercent() {
        assertThat(metrics.getSummary().exactMatchRatePercent()).isEqualTo(0.0);
    }

    @Test
    void matchRate_allMatch_returns100Percent() {
        metrics.incrementShadowExecuted();
        metrics.recordEvaluationResult(EvaluationResult.MATCH);

        assertThat(metrics.getSummary().exactMatchRatePercent()).isEqualTo(100.0);
    }

    @Test
    void matchRate_halfMatch_returns50Percent() {
        metrics.incrementShadowExecuted();
        metrics.recordEvaluationResult(EvaluationResult.MATCH);
        metrics.incrementShadowExecuted();
        metrics.recordEvaluationResult(EvaluationResult.MISMATCH);

        assertThat(metrics.getSummary().exactMatchRatePercent()).isEqualTo(50.0);
    }

    @Test
    void recordEvaluationResult_mismatch_doesNotIncrementMatchCount() {
        metrics.incrementShadowExecuted();
        metrics.recordEvaluationResult(EvaluationResult.MISMATCH);

        assertThat(metrics.getSummary().exactMatchCount()).isZero();
    }

    @Test
    void recordEvaluationResult_unparseablePrimary_doesNotIncrementMatchCount() {
        metrics.incrementShadowExecuted();
        metrics.recordEvaluationResult(EvaluationResult.PRIMARY_UNPARSEABLE);

        assertThat(metrics.getSummary().exactMatchCount()).isZero();
    }

    @Test
    void allCounters_incrementCorrectly() {
        metrics.incrementRequestsTotal();
        metrics.incrementRequestsTotal();
        metrics.incrementShadowExecuted();
        metrics.incrementShadowDropped();
        metrics.incrementShadowErrors();
        metrics.incrementShadowTimeouts();
        metrics.incrementExactMatch();

        MetricsSummary s = metrics.getSummary();
        assertThat(s.requestsTotal()).isEqualTo(2);
        assertThat(s.shadowExecuted()).isEqualTo(1);
        assertThat(s.shadowDropped()).isEqualTo(1);
        assertThat(s.shadowErrors()).isEqualTo(1);
        assertThat(s.shadowTimeouts()).isEqualTo(1);
        assertThat(s.exactMatchCount()).isEqualTo(1);
    }

    @Test
    void getSummary_includesNonNullTimestamp() {
        assertThat(metrics.getSummary().timestamp()).isNotNull();
    }
}
