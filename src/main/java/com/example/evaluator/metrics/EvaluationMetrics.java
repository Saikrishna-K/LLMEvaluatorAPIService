package com.example.evaluator.metrics;

import com.example.evaluator.dto.MetricsSummary;
import com.example.evaluator.evaluation.EvaluationResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe, in-memory counters for all observable events.
 * Uses AtomicLong for lock-free reads and writes from concurrent threads.
 *
 * Note: counters reset on service restart (acceptable for v1).
 * A Prometheus/Redis backend can replace this in a future phase.
 */
@Component
public class EvaluationMetrics {

    private final AtomicLong requestsTotal   = new AtomicLong(0);
    private final AtomicLong shadowExecuted  = new AtomicLong(0);
    private final AtomicLong shadowDropped   = new AtomicLong(0);
    private final AtomicLong shadowErrors    = new AtomicLong(0);
    private final AtomicLong shadowTimeouts  = new AtomicLong(0);
    private final AtomicLong exactMatchCount = new AtomicLong(0);

    public void incrementRequestsTotal()  { requestsTotal.incrementAndGet(); }
    public void incrementShadowExecuted() { shadowExecuted.incrementAndGet(); }
    public void incrementShadowDropped()  { shadowDropped.incrementAndGet(); }
    public void incrementShadowErrors()   { shadowErrors.incrementAndGet(); }
    public void incrementShadowTimeouts() { shadowTimeouts.incrementAndGet(); }
    public void incrementExactMatch()     { exactMatchCount.incrementAndGet(); }

    /** Records a completed evaluation result into the relevant counter. */
    public void recordEvaluationResult(EvaluationResult result) {
        if (result == EvaluationResult.MATCH) {
            exactMatchCount.incrementAndGet();
        }
    }

    /** Builds a point-in-time snapshot. All reads are individually atomic. */
    public MetricsSummary getSummary() {
        long executed = shadowExecuted.get();
        long matches  = exactMatchCount.get();
        double matchRate = (executed > 0) ? (double) matches / executed * 100.0 : 0.0;
        double rounded   = Math.round(matchRate * 100.0) / 100.0;

        return new MetricsSummary(
                requestsTotal.get(),
                executed,
                shadowDropped.get(),
                shadowErrors.get(),
                shadowTimeouts.get(),
                matches,
                rounded,
                Instant.now().toString());
    }

    // Visible for testing
    public long getRequestsTotal()   { return requestsTotal.get(); }
    public long getShadowExecuted()  { return shadowExecuted.get(); }
    public long getShadowDropped()   { return shadowDropped.get(); }
    public long getShadowErrors()    { return shadowErrors.get(); }
    public long getShadowTimeouts()  { return shadowTimeouts.get(); }
    public long getExactMatchCount() { return exactMatchCount.get(); }
}
