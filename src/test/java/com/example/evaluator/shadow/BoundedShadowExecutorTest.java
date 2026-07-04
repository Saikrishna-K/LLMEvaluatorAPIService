package com.example.evaluator.shadow;

import com.example.evaluator.config.ShadowProperties;
import com.example.evaluator.metrics.EvaluationMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedShadowExecutorTest {

    private BoundedShadowExecutor executor;
    private EvaluationMetrics metrics;

    // queue=2, core=1, max=1  → capacity is effectively queue+max = 3 tasks
    private static final ShadowProperties SMALL_PROPS =
            new ShadowProperties(2, 1, 1, "action-field");

    @BeforeEach
    void setUp() {
        metrics  = new EvaluationMetrics();
        executor = new BoundedShadowExecutor(SMALL_PROPS, metrics);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void submit_withinCapacity_accepted() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        boolean accepted = executor.submit(done::countDown);

        assertThat(accepted).isTrue();
        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(metrics.getShadowDropped()).isZero();
    }

    @Test
    void submit_overCapacity_taskIsDropped() throws InterruptedException {
        // Hold the single worker thread with a blocking latch
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        // Occupy the worker thread
        executor.submit(() -> {
            workerStarted.countDown();
            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        workerStarted.await(2, TimeUnit.SECONDS);

        // Fill the queue (capacity=2)
        executor.submit(() -> {});
        executor.submit(() -> {});

        // This one should be shed
        boolean accepted = executor.submit(() -> {});

        assertThat(accepted).isFalse();
        assertThat(metrics.getShadowDropped()).isEqualTo(1);

        blocker.countDown(); // release worker
    }

    @Test
    void droppedCounter_incrementsOnEachShedTask() throws InterruptedException {
        CountDownLatch blocker = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        executor.submit(() -> {
            workerStarted.countDown();
            try { blocker.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
        workerStarted.await(2, TimeUnit.SECONDS);

        // Fill queue
        executor.submit(() -> {});
        executor.submit(() -> {});

        // Shed 3 tasks
        executor.submit(() -> {});
        executor.submit(() -> {});
        executor.submit(() -> {});

        assertThat(metrics.getShadowDropped()).isEqualTo(3);
        blocker.countDown();
    }

    @Test
    void submit_tasksExecute_completeSuccessfully() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        executor.submit(() -> { counter.incrementAndGet(); latch.countDown(); });
        executor.submit(() -> { counter.incrementAndGet(); latch.countDown(); });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(2);
    }
}
