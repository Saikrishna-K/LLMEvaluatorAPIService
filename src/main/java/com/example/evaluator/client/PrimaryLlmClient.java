package com.example.evaluator.client;

import com.example.evaluator.config.LlmProperties;
import com.example.evaluator.dto.ChatRequest;
import com.example.evaluator.dto.LlmChatRequest;
import com.example.evaluator.dto.LlmChatResponse;
import com.example.evaluator.exception.PrimaryLlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

/**
 * Calls the primary LLM synchronously on the HTTP request thread.
 * A failure here surfaces to the caller as a 502/504.
 */
@Component
public class PrimaryLlmClient {

    private static final Logger log = LoggerFactory.getLogger(PrimaryLlmClient.class);

    private final WebClient webClient;
    private final LlmProperties.ModelConfig config;

    public PrimaryLlmClient(
            @Qualifier("primaryWebClient") WebClient webClient,
            LlmProperties props) {
        this.webClient = webClient;
        this.config = props.primary();
    }

    public LlmChatResponse chat(ChatRequest request) {
        String model = (request.model() != null) ? request.model() : config.defaultModel();
        LlmChatRequest llmRequest = new LlmChatRequest(model, request.messages(), null, request.temperature());

        log.debug("Primary LLM request: model={}", model);
        try {
            return webClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(llmRequest)
                    .retrieve()
                    .bodyToMono(LlmChatResponse.class)
                    .block();
        } catch (Exception ex) {
            boolean isTimeout = isTimeoutCause(ex);
            log.error("Primary LLM call failed (timeout={}): {}", isTimeout, ex.getMessage());
            throw new PrimaryLlmException("Primary LLM call failed: " + ex.getMessage(), ex, isTimeout);
        }
    }

    private boolean isTimeoutCause(Throwable ex) {
        if (ex instanceof TimeoutException) return true;
        Throwable cause = ex.getCause();
        return cause instanceof TimeoutException;
    }
}
