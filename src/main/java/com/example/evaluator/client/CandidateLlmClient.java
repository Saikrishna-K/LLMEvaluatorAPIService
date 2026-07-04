package com.example.evaluator.client;

import com.example.evaluator.config.LlmProperties;
import com.example.evaluator.dto.ChatRequest;
import com.example.evaluator.dto.LlmChatRequest;
import com.example.evaluator.dto.LlmChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Calls the candidate LLM and returns a Mono (non-blocking until subscribed).
 * Blocking happens in the shadow worker thread, not on the HTTP request thread.
 */
@Component
public class CandidateLlmClient {

    private static final Logger log = LoggerFactory.getLogger(CandidateLlmClient.class);

    private final WebClient webClient;
    private final LlmProperties.ModelConfig config;

    public CandidateLlmClient(
            @Qualifier("candidateWebClient") WebClient webClient,
            LlmProperties props) {
        this.webClient = webClient;
        this.config = props.candidate();
    }

    /**
     * Returns a cold Mono. The caller (shadow thread) blocks via .block().
     * Timeout is already configured at the Reactor Netty transport layer in LlmClientConfig.
     */
    public Mono<LlmChatResponse> chat(ChatRequest request) {
        String model = (request.model() != null) ? request.model() : config.defaultModel();
        LlmChatRequest llmRequest = new LlmChatRequest(model, request.messages(), null, request.temperature());

        log.debug("Candidate LLM request: model={}", model);
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(llmRequest)
                .retrieve()
                .bodyToMono(LlmChatResponse.class);
    }
}
