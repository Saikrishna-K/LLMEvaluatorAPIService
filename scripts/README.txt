Sample invoke scripts for the live LLM Evaluator API.
Not part of the Maven build — safe to add here.

Committed samples:
  invoke-chat.ps1    PowerShell — health, POST /v1/chat, GET /metrics
  invoke-chat.sh     Bash/curl version
  chat-browser.html  Open in browser (double-click) for POST /v1/chat

Usage (PowerShell):
  cd C:\test\LLMEvaluatorAPIService
  .\scripts\invoke-chat.ps1

Usage (browser):
  Open scripts\chat-browser.html in Chrome/Edge

Local overrides (NOT committed — see .gitignore):
  Copy any script to scripts\local\ and edit freely (tokens, URLs).

Default live URL:
  https://llm-evaluator-api-t9qud.ondigitalocean.app
