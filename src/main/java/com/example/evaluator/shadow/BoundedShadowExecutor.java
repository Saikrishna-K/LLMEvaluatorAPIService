package com.example.evaluator.shadow;

import com.example.evaluator.config.ShadowProperties;
import com.example.evaluator.metrics.EvaluationMetrics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded thread-pool executor for shadow evaluation tasks.
 *
 * Load-shedding contract:
 *   - The internal queue has a hard capacity limit (shadow.queue-capacity).
 *   - When the queue is full AND all worker threads are busy, the submitting
 *     thread gets RejectedExecutionException (AbortPolicy).
 *   - We catch that exception, increment the shadow_dropped counter, and
 *     return false — the HTTP response is already on its way to the client.
 *   - We NEVER block the HTTP request thread waiting for queue space.
 *
 * Thread naming: "shadow-worker-N" for easier thread-dump diagnostics.
 */
@Component
public class BoundedShadowExecutor {

    private static final Logger log = LoggerFactory.getLogger(BoundedShadowExecutor.class);

    private final ThreadPoolExecutor executor;
    private final EvaluationMetrics metrics;

    public BoundedShadowExecutor(ShadowProperties props, EvaluationMetrics metrics) {
        this.metrics = metrics;
        this.executor = new ThreadPoolExecutor(
                props.poolCoreSize(),
                props.poolMaxSize(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.queueCapacity()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("shadow-worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
                // default RejectedExecutionHandler = AbortPolicy (throws RejectedExecutionException)
        );
        log.info("Shadow executor started: core={}, max={}, queueCap={}",
                props.poolCoreSize(), props.poolMaxSize(), props.queueCapacity());
    }

    /**
     * Submits a runnable without blocking. Returns true if accepted, false if shed.
     */
    public boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            metrics.incrementShadowDropped();
            log.warn("Shadow task shed — queue saturated (dropped={}, queueSize={})",
                    metrics.getShadowDropped(), executor.getQueue().size());
            return false;
        }
    }

    /** Visible for testing. */
    public int getQueueSize() { return executor.getQueue().size(); }
    public int getActiveCount() { return executor.getActiveCount(); }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down shadow executor");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
