package com.example.evaluator.shadow;

import com.example.evaluator.client.CandidateLlmClient;
import com.example.evaluator.dto.LlmChatResponse;
import com.example.evaluator.evaluation.EvaluationResult;
import com.example.evaluator.evaluation.ResponseEvaluator;
import com.example.evaluator.metrics.EvaluationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;

/**
 * Orchestrates a single shadow evaluation cycle:
 *   1. Calls the candidate LLM (blocking, on a shadow worker thread).
 *   2. Extracts content from both responses.
 *   3. Runs the active ResponseEvaluator.
 *   4. Updates metrics.
 *
 * Any exception here is swallowed — it must NEVER propagate back to the HTTP layer.
 */
@Service
public class ShadowEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ShadowEvaluationService.class);

    private final CandidateLlmClient candidateClient;
    private final ResponseEvaluator evaluator;
    private final EvaluationMetrics metrics;
    private final BoundedShadowExecutor shadowExecutor;

    public ShadowEvaluationService(
            CandidateLlmClient candidateClient,
            ResponseEvaluator evaluator,
            EvaluationMetrics metrics,
            BoundedShadowExecutor shadowExecutor) {
        this.candidateClient = candidateClient;
        this.evaluator = evaluator;
        this.metrics = metrics;
        this.shadowExecutor = shadowExecutor;
    }

    /**
     * Fire-and-forget: submits the shadow task to the bounded executor.
     * Returns immediately; the HTTP response is not delayed.
     */
    public void submitForEvaluation(ShadowTask task) {
        shadowExecutor.submit(() -> runEvaluation(task));
    }

    private void runEvaluation(ShadowTask task) {
        metrics.incrementShadowExecuted();
        String correlationId = task.correlationId();

        try {
            log.debug("[{}] Shadow candidate call starting", correlationId);
            LlmChatResponse candidateResponse = candidateClient.chat(task.request()).block();

            String primaryContent   = task.primaryResponse().firstChoiceContent();
            String candidateContent = candidateResponse != null ? candidateResponse.firstChoiceContent() : null;

            EvaluationResult result = evaluator.evaluate(primaryContent, candidateContent);
            metrics.recordEvaluationResult(result);

            log.info("[{}] Shadow evaluation result={} evaluator={}",
                    correlationId, result, evaluator.getClass().getSimpleName());

        } catch (Exception ex) {
            if (isTimeout(ex)) {
                metrics.incrementShadowTimeouts();
                log.warn("[{}] Shadow candidate call timed out", correlationId);
            } else {
                metrics.incrementShadowErrors();
                log.error("[{}] Shadow candidate call error: {}", correlationId, ex.getMessage());
            }
        }
    }

    private boolean isTimeout(Throwable ex) {
        if (ex instanceof TimeoutException) return true;
        Throwable cause = ex.getCause();
        return cause instanceof TimeoutException
                || (cause != null && cause.getClass().getName().contains("TimeoutException"));
    }
}
