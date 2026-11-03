$ErrorActionPreference = 'Stop'
$BackendRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $BackendRoot '.env.local'
if (!(Test-Path $envFile)) { throw 'Run scripts/init-local.ps1 first.' }
foreach ($line in Get-Content $envFile) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:BYTE_JUDGE_RUNTIME_DIR = Join-Path $BackendRoot '.local/runtime'
$env:BYTE_JUDGE_LOG_DIR = Join-Path $BackendRoot 'log'
