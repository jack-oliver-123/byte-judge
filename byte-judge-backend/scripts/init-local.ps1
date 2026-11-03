$ErrorActionPreference = 'Stop'
$backend = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $backend '.env.local'
$content = if (Test-Path $envFile) { Get-Content $envFile -Raw } else { '' }
foreach ($line in Get-Content (Join-Path $backend '.env.example')) {
    $key = ($line -split '=', 2)[0]
    if ($line -match '^[A-Z][A-Z0-9_]*=' -and $content -notmatch "(?m)^$key=") {
        $content = $content.TrimEnd() + [Environment]::NewLine + $line + [Environment]::NewLine
    }
}
while ($content.Contains('GENERATED_BY_INIT')) {
    $bytes = New-Object byte[] 24
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $secret = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    $content = ([regex]'GENERATED_BY_INIT').Replace($content, $secret, 1)
}
[IO.File]::WriteAllText($envFile, $content.TrimStart())
. "$PSScriptRoot/local-env.ps1"
$initDir = Join-Path $backend '.local/mysql-init'
New-Item -ItemType Directory -Force $initDir | Out-Null
$sql = Get-Content (Join-Path $backend 'infra/mysql/init/01-databases.sql.template') -Raw
foreach ($owner in @('USER', 'PROBLEM', 'SUBMISSION', 'CONTEST')) {
    $value = [Environment]::GetEnvironmentVariable("MYSQL_${owner}_PASSWORD")
    if ($value -notmatch '^[a-f0-9]{48}$') { throw "Invalid generated password for $owner; restore .env.local or use an empty new local environment." }
    $sql = $sql.Replace('${MYSQL_' + $owner + '_PASSWORD}', $value)
}
[IO.File]::WriteAllText((Join-Path $initDir '01-databases.sql'), $sql)
Write-Host 'Local environment ready. Existing credentials preserved. SQL runs only on an empty MySQL volume.'
