package com.example.evaluator.api;

import com.example.evaluator.client.PrimaryLlmClient;
import com.example.evaluator.dto.ChatRequest;
import com.example.evaluator.dto.LlmChatResponse;
import com.example.evaluator.metrics.EvaluationMetrics;
import com.example.evaluator.shadow.ShadowEvaluationService;
import com.example.evaluator.shadow.ShadowTask;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * POST /v1/chat
 *
 * Hot path (sync):
 *   1. Validate request.
 *   2. Increment requests_total.
 *   3. Call primary LLM — blocks until response or timeout.
 *   4. Submit shadow task to bounded executor (non-blocking, fire-and-forget).
 *   5. Return primary response immediately with X-Request-Id header.
 *
 * Shadow failures (drop/error/timeout) NEVER affect the HTTP status code.
 */
@RestController
@RequestMapping("/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final PrimaryLlmClient primaryClient;
    private final ShadowEvaluationService shadowService;
    private final EvaluationMetrics metrics;

    public ChatController(
            PrimaryLlmClient primaryClient,
            ShadowEvaluationService shadowService,
            EvaluationMetrics metrics) {
        this.primaryClient = primaryClient;
        this.shadowService = shadowService;
        this.metrics = metrics;
    }

    @PostMapping("/chat")
    public ResponseEntity<LlmChatResponse> chat(@RequestBody @Valid ChatRequest request) {
        String correlationId = UUID.randomUUID().toString();
        metrics.incrementRequestsTotal();
        log.info("testing CI/CD");
        log.info("[{}] Received chat request, model={}", correlationId, request.model());

        LlmChatResponse primaryResponse = primaryClient.chat(request);

        shadowService.submitForEvaluation(new ShadowTask(correlationId, request, primaryResponse));

        return ResponseEntity.ok()
                .header("X-Request-Id", correlationId)
                .body(primaryResponse);
    }
}
