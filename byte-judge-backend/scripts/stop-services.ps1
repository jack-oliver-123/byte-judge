$ErrorActionPreference = 'Stop'
$backend = Split-Path $PSScriptRoot -Parent
Get-ChildItem "$backend/.local/pids/*.pid" -ErrorAction SilentlyContinue | ForEach-Object {
    $processId = [int](Get-Content $_.FullName)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($process -and $process.Name -eq 'java.exe' -and $process.CommandLine.Contains($backend)) {
        Stop-Process -Id $processId
    }
    Remove-Item -LiteralPath $_.FullName
}
