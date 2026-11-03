$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/init-local.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    docker compose --env-file .env.local up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'Infrastructure startup failed. Inspect docker compose logs.' }
} finally { Pop-Location }
