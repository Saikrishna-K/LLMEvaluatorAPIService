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

> **Note:** `/v1/chat` requires **POST** with a JSON body. Opening the URL in a browser (GET) returns `500 INTERNAL_ERROR`.

---

## Live E2E Demo (Production)

Use this section to walk an interviewer through the deployed system end-to-end.

### Production base URL

```
https://llm-evaluator-api-t9qud.ondigitalocean.app
```

| Endpoint | Method | Browser URL? | Purpose |
|----------|--------|--------------|---------|
| `/actuator/health` | GET | Yes | Liveness — DO App Platform health check |
| `/metrics` | GET | Yes | Real-time counters + exact match rate |
| `/v1/chat` | **POST** | **No** — use script or Postman | Sync primary LLM + async shadow eval |

### E2E flow (what happens on one chat request)

```
1. Client  ──POST /v1/chat──►  API (sync path)
2. API     ──sync call──────►  Primary LLM  (DO Serverless Inference, llama3.3-70b-instruct)
3. API     ◄──response──────   Primary LLM
4. API     ──200 + body────►  Client          ← immediate return (user never waits for candidate)
5. API     ──fire-and-forget► BoundedShadowExecutor
6. Worker  ──async call─────►  Candidate LLM  (llama3.1-8b-instruct)
7. Worker  ──evaluate───────►  ActionFieldEvaluator (exact match on JSON "action" field)
8. Worker  ──increment──────►  EvaluationMetrics (requests_total, shadow_executed, exact_match_count, …)
```

### Step 1 — Health check

**Request:** open in browser or:

```powershell
Invoke-RestMethod "https://llm-evaluator-api-t9qud.ondigitalocean.app/actuator/health"
```

**Response `200 OK`:**

```json
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" }
  }
}
```

### Step 2 — Chat (primary path)

**Request:**

```powershell
$base = "https://llm-evaluator-api-t9qud.ondigitalocean.app"
$body = '{"messages":[{"role":"user","content":"Respond with JSON only: {\"action\":\"greet\",\"text\":\"Hello\"}"}]}'

Invoke-RestMethod -Uri "$base/v1/chat" -Method POST -ContentType "application/json" -Body $body
```

Or run the committed script:

```powershell
.\scripts\invoke-chat.ps1
```

Or open `scripts/chat-browser.html` in a browser and click **Send Chat**.

**Response `200 OK` (example from live deployment):**

```json
{
  "id": "chatcmpl-4f65c2cd-9c7e-4ada-a4c1-daf51777bcfe",
  "object": "chat.completion",
  "created": 1783161442,
  "model": "llama3.3-70b-instruct",
  "choices": [{
    "index": 0,
    "message": {
      "role": "assistant",
      "content": "{\"action\":\"respond\",\"text\":\"Hello, how are you?\"}"
    },
    "finish_reason": "stop"
  }],
  "usage": {
    "prompt_tokens": 50,
    "completion_tokens": 15,
    "total_tokens": 65
  }
}
```

**Response header:**

| Header | Example | Use |
|--------|---------|-----|
| `X-Request-Id` | `0bb3c357-7908-4c2c-8b83-0dd66b573676` | Correlate with shadow evaluation logs |

The client receives the **primary** response only. Shadow evaluation runs in the background.

### Step 3 — Metrics (after ~5–8 seconds)

**Request:**

```powershell
Invoke-RestMethod "https://llm-evaluator-api-t9qud.ondigitalocean.app/metrics"
```

**Response `200 OK` (example after one chat call):**

```json
{
  "timestamp": "2026-07-04T10:37:31.727248990Z",
  "requests_total": 1,
  "shadow_executed": 1,
  "shadow_dropped": 0,
  "shadow_errors": 0,
  "shadow_timeouts": 0,
  "exact_match_count": 1,
  "exact_match_rate_percent": 100.0
}
```

| Metric | Meaning |
|--------|---------|
| `requests_total` | All `POST /v1/chat` calls received |
| `shadow_executed` | Background evaluations that started |
| `shadow_dropped` | Shed under load (queue full) — never affects HTTP status |
| `shadow_errors` | Candidate LLM or parse failures |
| `shadow_timeouts` | Candidate call exceeded timeout |
| `exact_match_count` | Primary vs candidate `action` field matched |
| `exact_match_rate_percent` | `exact_match_count / shadow_executed × 100` |

### Error responses (live)

| Status | When | Example body |
|--------|------|----------------|
| `400` | Missing/invalid `messages` | `{"error":"VALIDATION_ERROR","message":"messages: must not be empty",...}` |
| `502` | Primary LLM error (e.g. bad API key) | `{"error":"BAD_GATEWAY","message":"Primary LLM returned an error",...}` |
| `504` | Primary LLM timeout | `{"error":"GATEWAY_TIMEOUT","message":"Primary LLM did not respond in time",...}` |
| `500` | GET on `/v1/chat` (browser URL bar) | `{"error":"INTERNAL_ERROR","message":"An unexpected error occurred",...}` |

Shadow failures never change the chat HTTP status — they appear only in `/metrics`.

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

## Test Suite

**Total: 40 automated tests** — 34 unit (Surefire) + 6 integration (Failsafe).  
All LLM calls in tests use **WireMock** stubs — no real API keys or network in CI.

```bash
mvn verify          # all tests
mvn test            # unit only
mvn failsafe:integration-test failsafe:verify   # integration only
```

### Unit tests — `ActionFieldEvaluatorTest` (10 tests)

Phase 2 evaluator: extracts `"action"` from JSON and exact-matches.

| Test | Scenario | Expected |
|------|----------|----------|
| `sameAction_returnsMatch` | Same `action`, different other fields | `MATCH` |
| `differentAction_returnsMismatch` | Different `action` values | `MISMATCH` |
| `actionIsCaseSensitive` | `"Greet"` vs `"greet"` | `MISMATCH` |
| `missingActionInPrimary_returnsPrimaryUnparseable` | No `action` in primary | `PRIMARY_UNPARSEABLE` |
| `missingActionInCandidate_returnsCandidateUnparseable` | No `action` in candidate | `CANDIDATE_UNPARSEABLE` |
| `nullActionValue_returnsUnparseable` | `"action": null` | `PRIMARY_UNPARSEABLE` |
| `nonStringAction_returnsUnparseable` | `"action": 42` | `PRIMARY_UNPARSEABLE` |
| `bothUnparseable_returnsBothUnparseable` | Both invalid JSON | `BOTH_UNPARSEABLE` |
| `nullInputs_returnsBothUnparseable` | Null content strings | `BOTH_UNPARSEABLE` |
| `markdownFencedJson_extractsActionCorrectly` | JSON wrapped in ` ```json ` fences | `MATCH` |

### Unit tests — `JsonEqualityEvaluatorTest` (12 tests)

Phase 1 evaluator: deep JSON structural equality.

| Test | Scenario | Expected |
|------|----------|----------|
| `identicalJsonObjects_returnsMatch` | Identical JSON objects | `MATCH` |
| `differentFieldValues_returnsMismatch` | Different field values | `MISMATCH` |
| `extraFieldInCandidate_returnsMismatch` | Extra field in candidate | `MISMATCH` |
| `bothUnparseable_returnsBothUnparseable` | Both invalid JSON | `BOTH_UNPARSEABLE` |
| `primaryUnparseable_returnsPrimaryUnparseable` | Primary not JSON | `PRIMARY_UNPARSEABLE` |
| `candidateUnparseable_returnsCandidateUnparseable` | Candidate not JSON | `CANDIDATE_UNPARSEABLE` |
| `nullPrimary_returnsPrimaryUnparseable` | Null primary | `PRIMARY_UNPARSEABLE` |
| `blankBoth_returnsBothUnparseable` | Empty/blank strings | `BOTH_UNPARSEABLE` |
| `markdownFencedJson_strippedAndMatches` | Markdown code fences stripped | `MATCH` |
| `jsonArrays_matchWhenEqual` | Equal JSON arrays | `MATCH` |
| `stripMarkdownFences_removesCodeBlock` | Fence stripping utility | Correct string |
| `stripMarkdownFences_noFences_returnsUnchanged` | Plain JSON unchanged | Correct string |

### Unit tests — `EvaluationMetricsTest` (8 tests)

| Test | Scenario |
|------|----------|
| `initialState_allCountersAreZero` | Fresh metrics all zero |
| `matchRate_zeroExecuted_returnsZeroPercent` | No division-by-zero |
| `matchRate_allMatch_returns100Percent` | 1/1 = 100% |
| `matchRate_halfMatch_returns50Percent` | 1/2 = 50% |
| `recordEvaluationResult_mismatch_doesNotIncrementMatchCount` | Mismatch ≠ match |
| `recordEvaluationResult_unparseablePrimary_doesNotIncrementMatchCount` | Unparseable ≠ match |
| `allCounters_incrementCorrectly` | All AtomicLong counters work |
| `getSummary_includesNonNullTimestamp` | Snapshot has ISO timestamp |

### Unit tests — `BoundedShadowExecutorTest` (4 tests)

Load-shedding and bounded queue behavior.

| Test | Scenario |
|------|----------|
| `submit_withinCapacity_accepted` | Task accepted when queue has room |
| `submit_overCapacity_taskIsDropped` | Full queue → task shed, `shadow_dropped++` |
| `droppedCounter_incrementsOnEachShedTask` | Multiple sheds increment counter |
| `submit_tasksExecute_completeSuccessfully` | Accepted tasks run to completion |

### Integration tests — `ChatControllerIT` (6 tests)

Full HTTP flow via `@SpringBootTest` + WireMock stubbing both LLM endpoints.

| Test | Scenario | Asserts |
|------|----------|---------|
| `happyPath_actionMatch_returns200AndIncrementsMatchCount` | Primary + candidate same `action` | `200`, `X-Request-Id`, `exact_match_count++` |
| `actionMismatch_returns200ButMismatchCounted` | Different `action` values | `200`, match count unchanged |
| `primaryDown_returns502` | Primary LLM returns 503 | `502 BAD_GATEWAY` |
| `invalidRequest_missingMessages_returns400` | Empty/missing `messages` | `400 VALIDATION_ERROR` |
| `metricsEndpoint_returnsAllFields` | GET `/metrics` after chat | All 8 metric fields present |
| `candidateError_shadowErrorCounted_primaryResponseUnaffected` | Candidate 500, primary OK | Chat `200`, `shadow_errors++` |

---

## CI / CD — GitHub Actions

The pipeline (`.github/workflows/ci.yml`) has 3 jobs:

```
push to main
    │
    ▼
[test] ──(pass)──► [build-and-push] ──► [deploy]
    │                (Docker image          (doctl apps create-deployment)
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
| `DO_REGISTRY_NAME` | `llmevaluator` |
| `DO_APP_ID` | `22663ce9-2cba-4779-aefc-75969f1b5745` |

Runtime secrets (`DO_INFERENCE_API_KEY`, `CANDIDATE_LLM_API_KEY`) are set **only in the DO App Platform console** — they never touch GitHub.

---

## Deployment to DigitalOcean

### Live URLs

| Endpoint | URL |
|----------|-----|
| Chat | `POST https://llm-evaluator-api-t9qud.ondigitalocean.app/v1/chat` |
| Metrics | `GET https://llm-evaluator-api-t9qud.ondigitalocean.app/metrics` |
| Health | `GET https://llm-evaluator-api-t9qud.ondigitalocean.app/actuator/health` |

See **[Live E2E Demo (Production)](#live-e2e-demo-production)** for sample requests and responses.

### First deploy (manual, one-time)

App config (env vars, instance size, inference keys) is managed in the **DO App Platform console** after initial setup:

1. `doctl registry create llmevaluator`
2. `docker build` + `docker push registry.digitalocean.com/llmevaluator/llm-evaluator:latest`
3. Create app in DO Console → point to DOCR image `llm-evaluator:latest`
4. Set `DO_INFERENCE_API_KEY` and `CANDIDATE_LLM_API_KEY` (Model Access Keys)
5. Subsequent deploys: push to `main` (GitHub Actions) or `doctl apps create-deployment <APP_ID>`

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
