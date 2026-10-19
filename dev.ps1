# Starts JobTrack for local development on Windows: PostgreSQL (Docker), the backend and the
# frontend, each backend/frontend in its own window. Run from the project folder:
#   powershell -ExecutionPolicy Bypass -File .\dev.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path .env)) { throw 'Missing .env - copy .env.example to .env and fill it in first.' }

Write-Host 'Starting PostgreSQL...'
docker compose up -d --wait

# The local AI is optional for the app itself, but "Add from link" and Gmail tracking need it.
try {
    Invoke-RestMethod http://localhost:11434/api/version -TimeoutSec 3 | Out-Null
    Write-Host 'Ollama is running.'
} catch {
    Write-Warning 'Ollama is not running: start the Ollama app to use "Add from link" and Gmail tracking.'
}

Write-Host 'Starting the backend (new window)...'
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$PSScriptRoot\backend'; .\mvnw.cmd spring-boot:run"

Write-Host 'Starting the frontend (new window)...'
if (-not (Test-Path frontend\node_modules)) { Push-Location frontend; npm install; Pop-Location }
Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$PSScriptRoot\frontend'; npm run dev"

Write-Host 'Open http://localhost:5173 once both windows say they are ready (the backend takes ~20 s).'
