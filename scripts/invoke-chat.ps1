# Invoke the live LLM Evaluator chat API and print metrics.
# Usage:
#   .\scripts\invoke-chat.ps1
#   .\scripts\invoke-chat.ps1 -BaseUrl "https://your-app.ondigitalocean.app" -Message "Say hello"

param(
    [string]$BaseUrl = "https://llm-evaluator-api-t9qud.ondigitalocean.app",
    [string]$Message = 'Respond with JSON only: {"action":"greet","text":"Hello"}',
    [int]$MetricsWaitSeconds = 8
)

$ErrorActionPreference = "Stop"

Write-Host "=== HEALTH ===" -ForegroundColor Cyan
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
Write-Host "Status: $($health.status)"

Write-Host "`n=== POST /v1/chat ===" -ForegroundColor Cyan
$body = @{
    messages = @(
        @{ role = "user"; content = $Message }
    )
} | ConvertTo-Json -Depth 5 -Compress

$response = Invoke-WebRequest `
    -Uri "$BaseUrl/v1/chat" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body `
    -UseBasicParsing

Write-Host "HTTP Status: $($response.StatusCode)"
Write-Host "X-Request-Id: $($response.Headers['X-Request-Id'])"
Write-Host "Response:"
$response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10

Write-Host "`nWaiting ${MetricsWaitSeconds}s for shadow evaluation..." -ForegroundColor Yellow
Start-Sleep -Seconds $MetricsWaitSeconds

Write-Host "`n=== GET /metrics ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "$BaseUrl/metrics" | ConvertTo-Json
