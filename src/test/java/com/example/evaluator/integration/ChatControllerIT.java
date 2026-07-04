package com.example.evaluator.integration;

import com.example.evaluator.metrics.EvaluationMetrics;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatControllerIT {

    static WireMockServer wireMock;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    EvaluationMetrics metrics;

    // ---------------------------------------------------------------------------
    // Shared LLM response stubs
    // ---------------------------------------------------------------------------

    private static final String PRIMARY_MATCH_RESPONSE = """
            {
              "id": "chatcmpl-primary-1",
              "object": "chat.completion",
              "created": 1720000000,
              "model": "llama3.3-70b-instruct",
              "choices": [{
                "index": 0,
                "message": { "role": "assistant", "content": "{\\"action\\":\\"greet\\",\\"text\\":\\"Hello\\"}" },
                "finish_reason": "stop"
              }],
              "usage": { "prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30 }
            }
            """;

    private static final String CANDIDATE_MATCH_RESPONSE = """
            {
              "id": "chatcmpl-candidate-1",
              "object": "chat.completion",
              "created": 1720000000,
              "model": "llama3.1-8b-instruct",
              "choices": [{
                "index": 0,
                "message": { "role": "assistant", "content": "{\\"action\\":\\"greet\\",\\"text\\":\\"Hi there\\"}" },
                "finish_reason": "stop"
              }],
              "usage": { "prompt_tokens": 10, "completion_tokens": 15, "total_tokens": 25 }
            }
            """;

    private static final String CANDIDATE_MISMATCH_RESPONSE = """
            {
              "id": "chatcmpl-candidate-2",
              "object": "chat.completion",
              "created": 1720000000,
              "model": "llama3.1-8b-instruct",
              "choices": [{
                "index": 0,
                "message": { "role": "assistant", "content": "{\\"action\\":\\"farewell\\",\\"text\\":\\"Goodbye\\"}" },
                "finish_reason": "stop"
              }],
              "usage": { "prompt_tokens": 10, "completion_tokens": 12, "total_tokens": 22 }
            }
            """;

    private static final String CHAT_REQUEST = """
            {
              "messages": [{ "role": "user", "content": "Say hello" }]
            }
            """;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Both primary and candidate point at the same WireMock instance
        registry.add("llm.primary.base-url",   wireMock::baseUrl);
        registry.add("llm.candidate.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    void happyPath_actionMatch_returns200AndIncrementsMatchCount() {
        long matchesBefore  = metrics.getExactMatchCount();
        long executedBefore = metrics.getShadowExecuted();
        long errorsBefore   = metrics.getShadowErrors();

        // Scenario ensures primary call (1st) gets PRIMARY_MATCH_RESPONSE,
        // and candidate call (2nd) gets CANDIDATE_MATCH_RESPONSE.
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("happy-path")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_MATCH_RESPONSE))
                .willSetStateTo("primary-done"));

        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("happy-path")
                .whenScenarioStateIs("primary-done")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CANDIDATE_MATCH_RESPONSE)));

        ResponseEntity<Map> response = postChat(CHAT_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders()).containsKey("X-Request-Id");
        assertThat(response.getBody()).containsKey("choices");

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(metrics.getShadowExecuted()).isEqualTo(executedBefore + 1));

        assertThat(metrics.getExactMatchCount()).isEqualTo(matchesBefore + 1);
        assertThat(metrics.getShadowErrors()).isEqualTo(errorsBefore);
    }

    @Test
    void actionMismatch_returns200ButMismatchCounted() {
        long matchesBefore  = metrics.getExactMatchCount();
        long executedBefore = metrics.getShadowExecuted();
        long errorsBefore   = metrics.getShadowErrors();

        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("mismatch")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_MATCH_RESPONSE))
                .willSetStateTo("primary-done"));

        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("mismatch")
                .whenScenarioStateIs("primary-done")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CANDIDATE_MISMATCH_RESPONSE)));

        ResponseEntity<Map> response = postChat(CHAT_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(metrics.getShadowExecuted()).isEqualTo(executedBefore + 1));

        // Match count must NOT have increased — this is a mismatch
        assertThat(metrics.getExactMatchCount()).isEqualTo(matchesBefore);
        assertThat(metrics.getShadowErrors()).isEqualTo(errorsBefore);
    }

    @Test
    void primaryDown_returns502() {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        ResponseEntity<Map> response = postChat(CHAT_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void invalidRequest_missingMessages_returns400() {
        ResponseEntity<Map> response = postChat("{\"model\":\"llama3.3-70b-instruct\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void metricsEndpoint_returnsAllFields() {
        long shadowExBefore = metrics.getShadowExecuted();

        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("metrics")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_MATCH_RESPONSE))
                .willSetStateTo("primary-done"));
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("metrics")
                .whenScenarioStateIs("primary-done")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CANDIDATE_MATCH_RESPONSE)));

        postChat(CHAT_REQUEST);

        // Wait for BOTH the request counter and the shadow to settle before reading /metrics
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(metrics.getRequestsTotal()).isGreaterThan(0);
            assertThat(metrics.getShadowExecuted()).isEqualTo(shadowExBefore + 1);
        });

        ResponseEntity<Map> metricsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/metrics", Map.class);

        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsResponse.getBody()).containsKeys(
                "requests_total",
                "shadow_executed",
                "shadow_dropped",
                "shadow_errors",
                "shadow_timeouts",
                "exact_match_count",
                "exact_match_rate_percent",
                "timestamp"
        );
    }

    @Test
    void candidateError_shadowErrorCounted_primaryResponseUnaffected() {
        long errorsBefore  = metrics.getShadowErrors();
        long matchesBefore = metrics.getExactMatchCount();

        // Primary succeeds, candidate returns 500
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("candidate-error")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(PRIMARY_MATCH_RESPONSE))
                .willSetStateTo("primary-done"));

        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .inScenario("candidate-error")
                .whenScenarioStateIs("primary-done")
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":\"model error\"}")));

        ResponseEntity<Map> response = postChat(CHAT_REQUEST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(metrics.getShadowErrors()).isEqualTo(errorsBefore + 1));

        assertThat(metrics.getExactMatchCount()).isEqualTo(matchesBefore);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void stubLlm(String responseBody) {
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postChat(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(
                "http://localhost:" + port + "/v1/chat", entity, Map.class);
    }
}
