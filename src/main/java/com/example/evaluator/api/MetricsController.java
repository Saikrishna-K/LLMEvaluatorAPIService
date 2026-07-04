package com.example.evaluator.api;

import com.example.evaluator.dto.MetricsSummary;
import com.example.evaluator.metrics.EvaluationMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /metrics — real-time summary of all observable events.
 * Reads atomic counters; no locking; always returns a consistent point-in-time snapshot.
 */
@RestController
public class MetricsController {

    private final EvaluationMetrics metrics;

    public MetricsController(EvaluationMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public MetricsSummary getMetrics() {
        return metrics.getSummary();
    }
}
