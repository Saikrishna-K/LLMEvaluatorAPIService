#!/usr/bin/env bash
# Invoke the live LLM Evaluator chat API and print metrics.
# Usage:
#   ./scripts/invoke-chat.sh
#   BASE_URL=https://your-app.ondigitalocean.app ./scripts/invoke-chat.sh

set -euo pipefail

BASE_URL="${BASE_URL:-https://llm-evaluator-api-t9qud.ondigitalocean.app}"
MESSAGE="${MESSAGE:-Respond with JSON only: {\"action\":\"greet\",\"text\":\"Hello\"}}"
WAIT="${METRICS_WAIT_SECONDS:-8}"

echo "=== HEALTH ==="
curl -s "${BASE_URL}/actuator/health" | jq .

echo ""
echo "=== POST /v1/chat ==="
curl -s -D - -X POST "${BASE_URL}/v1/chat" \
  -H "Content-Type: application/json" \
  -d "{\"messages\":[{\"role\":\"user\",\"content\":\"${MESSAGE}\"}"

echo ""
echo ""
echo "Waiting ${WAIT}s for shadow evaluation..."
sleep "${WAIT}"

echo ""
echo "=== GET /metrics ==="
curl -s "${BASE_URL}/metrics" | jq .
