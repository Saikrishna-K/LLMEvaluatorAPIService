# LLM Evaluator API Service

A Java 21 / Spring Boot service that proxies a **primary LLM** (DigitalOcean Serverless Inference) and runs a **shadow evaluation** against a **candidate LLM** in the background — without ever blocking the HTTP response.

---

## Architecture

```
Client
  │
  ▼ POST /v1/chat
┌─────────────────────────────────────────────┐
│               API Layer (Spring Boot)        │
│                                             │
│  ChatController                             │
│    ├── PrimaryLlmClient (SYNC, blocks here) │──► DO Serverless Inference
│    │       returns immediately to client    │    https://inference.do-ai.run
│    │                                        │
│    └── BoundedShadowExecutor               │
│          .submit(task)  ← non-blocking      │
│          (if queue full → DROP + metric)    │
└─────────────────────────────────────────────┘
              │ (fire-and-forget)
              ▼
┌─────────────────────────────────────────────┐
│     Decoupled Background Shadow Pool        │
│  (ThreadPoolExecutor, ArrayBlockingQueue)   │
│                                             │
│  ShadowWorker                               │
│    ├── CandidateLlmClient (async → block)  │──► Candidate LLM endpoint
│    ├── ResponseEvaluator                   │
│    │     ActionFieldEvaluator              │
│    │     (or JsonEqualityEvaluator)        │
│    └── EvaluationMetrics (AtomicLong)      │
└─────────────────────────────────────────────┘

GET /metrics ──► EvaluationMetrics.getSummary()
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java (JDK) | 21 |
| Maven | 3.9+ |
| Docker | 24+ |
| DigitalOcean account | — |
| `doctl` CLI | latest |

---

## Local Development

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_ORG/LLMEvaluatorAPIService.git
cd LLMEvaluatorAPIService
```

### 2. Set environment variables

Create a `.env` file (never commit it):

```bash
export DO_INFERENCE_API_KEY=dop_v1_xxxxxxxxxxxxxxxx
export CANDIDATE_LLM_API_KEY=dop_v1_xxxxxxxxxxxxxxxx   # can be same key
export CANDIDATE_LLM_BASE_URL=https://inference.do-ai.run
export CANDIDATE_LLM_MODEL=llama3.1-8b-instruct
```

### 3. Run locally

```bash
source .env
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.

### 4. Run with Docker

```bash
docker build -t llm-evaluator .
docker run -p 8080:8080 \
  -e DO_INFERENCE_API_KEY=$DO_INFERENCE_API_KEY \
  -e CANDIDATE_LLM_API_KEY=$CANDIDATE_LLM_API_KEY \
  -e CANDIDATE_LLM_BASE_URL=$CANDIDATE_LLM_BASE_URL \
  llm-evaluator
```

---

## API Reference

### POST /v1/chat

Calls the primary LLM synchronously and returns the response immediately.  
Triggers shadow evaluation against the candidate LLM in the background.

**Request**

```json
{
  "model": "llama3.3-70b-instruct",
  "messages": [{ "role": "user", "content": "What should I do next?" }],
  "temperature": 0.0
}
```

`model` is optional — defaults to `PRIMARY_LLM_MODEL` if omitted.

**Response `200 OK`**

```json
{
  "id": "chatcmpl-...",
  "model": "llama3.3-70b-instruct",
  "choices": [{
    "index": 0,
    "message": { "role": "assistant", "content": "{\"action\":\"greet\",\"text\":\"Hello!\"}" },
    "finish_reason": "stop"
  }],
  "usage": { "prompt_tokens": 12, "completion_tokens": 18, "total_tokens": 30 }
}
```

**Headers returned**

| Header | Description |
|--------|-------------|
| `X-Request-Id` | UUID for correlating shadow logs |

**Error responses**

| Status | Condition |
|--------|-----------|
| 400 | Missing or invalid `messages` |
| 502 | Primary LLM returned a non-2xx response |
| 504 | Primary LLM did not respond within timeout |

Shadow failures (`shadow_errors`, `shadow_timeouts`, `shadow_dropped`) **never** change the HTTP status code.

---

### GET /metrics

Real-time summary of all observable events since last restart.

**Response `200 OK`**

```json
{
  "requests_total": 1523,
  "shadow_executed": 1410,
  "shadow_dropped": 89,
  "shadow_errors": 12,
  "shadow_timeouts": 5,
  "exact_match_count": 1187,
  "exact_match_rate_percent": 84.18,
  "timestamp": "2026-07-04T09:12:00Z"
}
```

`exact_match_rate_percent = exact_match_count / shadow_executed × 100`  
(denominator excludes drops; errors count as non-match)

---

### GET /actuator/health

Used by DO App Platform health checks.

```json
{ "status": "UP" }
```

---

## Configuration Reference

All values configurable via environment variables (12-factor):

| Variable | Default | Description |
|----------|---------|-------------|
| `DO_INFERENCE_API_KEY` | _(required)_ | DO API key for primary LLM |
| `DO_INFERENCE_BASE_URL` | `https://inference.do-ai.run` | Primary LLM base URL |
| `PRIMARY_LLM_MODEL` | `llama3.3-70b-instruct` | Default model for primary |
| `PRIMARY_LLM_TIMEOUT_MS` | `30000` | Primary call timeout (ms) |
| `CANDIDATE_LLM_API_KEY` | _(required)_ | API key for candidate LLM |
| `CANDIDATE_LLM_BASE_URL` | `https://inference.do-ai.run` | Candidate LLM base URL |
| `CANDIDATE_LLM_MODEL` | `llama3.1-8b-instruct` | Default model for candidate |
| `CANDIDATE_LLM_TIMEOUT_MS` | `60000` | Candidate call timeout (ms) |
| `SHADOW_QUEUE_CAPACITY` | `500` | Max pending shadow tasks before shedding |
| `SHADOW_POOL_CORE_SIZE` | `4` | Shadow worker thread floor |
| `SHADOW_POOL_MAX_SIZE` | `16` | Shadow worker thread ceiling |
| `SHADOW_EVAL_MODE` | `action-field` | `action-field` or `json-equality` |
| `PORT` | `8080` | HTTP server port |

---

## Evaluation Phases

### Phase 1 — JSON Equality (`json-equality`)

Both LLM response content strings must:
1. Parse as valid JSON (markdown code fences are stripped automatically).
2. Be structurally equal (deep Jackson node comparison).

Set `SHADOW_EVAL_MODE=json-equality`.

### Phase 2 — Action Field (`action-field`, default)

1. Parse both responses as JSON.
2. Extract the top-level `"action"` string from each.
3. Assert exact, case-sensitive equality.

```json
{ "action": "greet", "text": "Hello" }
```

If `action` is missing, null, or not a string, that side is classified as `UNPARSEABLE` (tracked in errors, not match count).

Switch phases with a single env var change — no code change, no redeploy of new code required.

---

## Shadow Queue — Load Shedding

```
Bounded Queue (SHADOW_QUEUE_CAPACITY = 500)
    │
    │  HTTP request thread calls submit() — non-blocking
    ▼
 [task, task, task, ..., task]   ← 500 max
    │
    ▼ ThreadPoolExecutor (core=4, max=16)
 [worker][worker][worker][worker]

When queue is FULL and all 16 workers are busy:
  → RejectedExecutionException caught
  → shadow_dropped++ (metric only)
  → HTTP response already sent — client unaffected
```

**The HTTP request thread never blocks waiting for queue space.**  
Under a traffic burst that saturates the shadow pool, dropped tasks are silently counted and observable via `GET /metrics`.

Tune `SHADOW_QUEUE_CAPACITY`, `SHADOW_POOL_CORE_SIZE`, `SHADOW_POOL_MAX_SIZE` based on your observed `shadow_dropped` rate.

---

## Running Tests

```bash
# All unit tests + integration tests (Failsafe)
mvn verify

# Unit tests only
mvn test

# Integration tests only
mvn failsafe:integration-test failsafe:verify
```

Integration tests use **WireMock** to stub both LLM endpoints — no real API keys or network access needed in CI.

**Test coverage:**

| Test class | What it covers |
|------------|----------------|
| `JsonEqualityEvaluatorTest` | Match, mismatch, both/one unparseable, markdown fences |
| `ActionFieldEvaluatorTest` | Same/different action, missing action, null, case sensitivity |
| `EvaluationMetricsTest` | All counters, match rate edge cases (zero denominator), timestamp |
| `BoundedShadowExecutorTest` | Acceptance, queue saturation, multiple drops, task execution |
| `ChatControllerIT` | Happy path, mismatch, primary down, invalid request, candidate error, metrics endpoint |

---

## CI / CD — GitHub Actions

The pipeline (`.github/workflows/ci.yml`) has 3 jobs:

```
push to main
    │
    ▼
[test] ──(pass)──► [build-and-push] ──► [deploy]
    │                (Docker image          (doctl apps update)
    │               pushed to DOCR)
    ▼
[fail] → pipeline stops, nothing deployed
```

On **pull requests**: only the `test` job runs.

### GitHub Secrets required

Set these in **Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|-------|
| `DIGITALOCEAN_ACCESS_TOKEN` | Your DO personal access token |
| `DO_REGISTRY_NAME` | Your DO Container Registry name (e.g. `my-registry`) |
| `DO_APP_ID` | App Platform App ID (from `doctl apps list`) |

Runtime secrets (`DO_INFERENCE_API_KEY`, `CANDIDATE_LLM_API_KEY`) are set **only in the DO App Platform console** — they never touch GitHub.

---

## Deployment to DigitalOcean

### Step-by-step first deploy

```bash
# 1. Install doctl
brew install doctl                    # macOS
# or download from https://docs.digitalocean.com/reference/doctl/

# 2. Authenticate
doctl auth init --access-token YOUR_DO_TOKEN

# 3. Create a container registry (one-time)
doctl registry create my-registry

# 4. Build and push image manually (first time)
doctl registry login
docker build -t registry.digitalocean.com/my-registry/llm-evaluator:latest .
docker push registry.digitalocean.com/my-registry/llm-evaluator:latest

# 5. Edit .do/app.yaml — replace ${DO_REGISTRY_NAME} with your registry name

# 6. Create the app
doctl apps create --spec .do/app.yaml

# 7. Set the runtime secrets (one-time, via DO console or doctl)
#    Go to: Apps → llm-evaluator-api → Settings → Environment Variables
#    Set: DO_INFERENCE_API_KEY, CANDIDATE_LLM_API_KEY

# 8. Get your App ID for GitHub Actions
doctl apps list
```

After the first deploy, every push to `main` triggers the full pipeline automatically.

### Live URLs

| Endpoint | URL pattern |
|----------|-------------|
| Chat | `https://llm-evaluator-api-xxxxx.ondigitalocean.app/v1/chat` |
| Metrics | `https://llm-evaluator-api-xxxxx.ondigitalocean.app/metrics` |
| Health | `https://llm-evaluator-api-xxxxx.ondigitalocean.app/actuator/health` |

---

## Resource Sizing

| Instance | vCPU | RAM | Cost/mo | Verdict |
|----------|------|-----|---------|---------|
| `basic-xxs` | 1 | 512 MB | $5 | Too small — JVM uses ~300 MB baseline |
| `basic-xs` | 1 | 1 GB | $10 | Minimum viable — tight under burst |
| **`basic-s`** | **1** | **2 GB** | **$18** | **Recommended** — shadow pool fits comfortably |
| `basic-m` | 2 | 4 GB | $40 | Scale up above ~60 req/min sustained |

**JVM configured at:** `-Xms256m -Xmx1g` (see `Dockerfile`).

Memory budget on `basic-s`:
- JVM heap: 1 GB
- Metaspace + JVM native: ~150 MB
- Shadow threads (16 × 1 MB): ~16 MB
- Queue (500 × 50 KB): ~25 MB
- Reactor Netty pools: ~30 MB
- OS headroom: ~150 MB
- **Total: ~1.4 GB** (comfortable in 2 GB)

---

## Project Structure

```
src/main/java/com/example/evaluator/
├── EvaluatorApplication.java
├── api/
│   ├── ChatController.java          POST /v1/chat
│   ├── MetricsController.java       GET /metrics
│   └── GlobalExceptionHandler.java
├── client/
│   ├── PrimaryLlmClient.java        Sync LLM call
│   └── CandidateLlmClient.java      Async (Mono) LLM call
├── config/
│   ├── LlmProperties.java
│   ├── ShadowProperties.java
│   ├── LlmClientConfig.java         WebClient beans
│   └── EvaluatorConfig.java         Active evaluator selection
├── dto/                             All request/response records
├── evaluation/
│   ├── ResponseEvaluator.java       Interface
│   ├── JsonEqualityEvaluator.java   Phase 1
│   └── ActionFieldEvaluator.java    Phase 2 (default)
├── exception/
│   └── PrimaryLlmException.java
├── metrics/
│   └── EvaluationMetrics.java       AtomicLong counters
└── shadow/
    ├── BoundedShadowExecutor.java   Load-shedding executor
    ├── ShadowEvaluationService.java Fire-and-forget orchestration
    └── ShadowTask.java              Immutable context record
```
