$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/local-env.ps1"
Push-Location $BackendRoot
try {
    $names = @('gateway', 'user', 'problem', 'submission', 'contest', 'judge')
    $deadline = (Get-Date).AddSeconds(180)
    foreach ($name in $names) {
        $index = [array]::IndexOf($names, $name)
        $ready = $false
        while ((Get-Date) -lt $deadline) {
            try {
                $health = Invoke-RestMethod "http://127.0.0.1:$(9080 + $index)/actuator/health" -TimeoutSec 3
                if ($health.status -eq 'UP') { $ready = $true; break }
            } catch {}
            Start-Sleep -Seconds 2
        }
        if (!$ready) { throw "$name health check failed." }
        $service = if ($name -eq 'gateway') { 'byte-judge-gateway' } else { "byte-judge-$name-model" }
        $group = 'BYTE_JUDGE'
        $namespace = 'public'
        if ($name -in @('gateway', 'user')) {
            if ($env:NACOS_DISCOVERY_GROUP) { $group = $env:NACOS_DISCOVERY_GROUP }
            if ($env:NACOS_DISCOVERY_NAMESPACE) { $namespace = $env:NACOS_DISCOVERY_NAMESPACE }
        }
        $discoveryQuery = 'serviceName=' + [Uri]::EscapeDataString($service) + '&groupName=' +
            [Uri]::EscapeDataString($group) + '&namespaceId=' + [Uri]::EscapeDataString($namespace)
        $instances = Invoke-RestMethod "http://$env:NACOS_SERVER_ADDR/nacos/v3/client/ns/instance/list?$discoveryQuery" -TimeoutSec 10
        if ($instances.code -ne 0 -or !($instances.data | Where-Object { $_.healthy -and $_.port -eq (8080 + $index) })) { throw "$service is not registered healthy." }
        Write-Host "PASS health + discovery: $service"
    }
    foreach ($owner in @('user', 'problem', 'submission', 'contest')) {
        $password = [Environment]::GetEnvironmentVariable("MYSQL_$($owner.ToUpper())_PASSWORD")
        # The problem database/account keeps its legacy literal name (see infra/mysql/init template).
        $dbName = if ($owner -eq 'problem') { 'bj_problem' } else { "bj_$owner" }
        $sql = "SELECT DATABASE(); SHOW GRANTS FOR CURRENT_USER();"
        $grants = docker compose --env-file .env.local exec -T -e "MYSQL_PWD=$password" mysql mysql "-u$dbName" "$dbName" -N -e $sql
        if ($LASTEXITCODE -ne 0 -or ($grants -join ' ') -notmatch [regex]::Escape($dbName)) { throw "$owner database connection failed." }
        foreach ($other in @('user', 'problem', 'submission', 'contest') | Where-Object { $_ -ne $owner }) {
            $otherDb = if ($other -eq 'problem') { 'bj_problem' } else { "bj_$other" }
            # Cross-db denial makes mysql write to stderr on purpose. Under Windows PowerShell 5.1,
            # any redirected or captured native stderr (2>$null / 2>&1) becomes a NativeCommandError
            # record, which the script's Stop preference would turn fatal. Run the check through
            # cmd.exe so stderr is merged at the process level and only the exit code is observed.
            $denied = cmd /c "docker compose --env-file .env.local exec -T -e MYSQL_PWD=$password mysql mysql -u$dbName $otherDb -e `"SELECT 1`" 2>&1"
            if ($LASTEXITCODE -eq 0) { throw "$owner unexpectedly has access to $otherDb" }
        }
        Write-Host "PASS database ownership: $owner"
    }
    $pong = docker compose --env-file .env.local exec -T -e "REDISCLI_AUTH=$env:REDIS_PASSWORD" redis redis-cli ping
    if ($LASTEXITCODE -ne 0 -or $pong -ne 'PONG') { throw 'Redis authentication failed.' }
    $pair = "$env:RABBITMQ_USER`:$env:RABBITMQ_PASSWORD"
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $null = Invoke-RestMethod 'http://127.0.0.1:15672/api/overview' -Headers @{Authorization="Basic $token"} -TimeoutSec 10
    foreach ($route in @(@('user','users'), @('problem','problems'), @('submission','submissions'), @('contest','contests'))) {
        $probe = '/api/v1/' + $route[1] + '/skeleton-probe-' + [guid]::NewGuid().ToString('N')
        try { Invoke-WebRequest "http://127.0.0.1:8080$probe" -UseBasicParsing -TimeoutSec 10 | Out-Null; throw 'Expected downstream 404.' }
        catch { if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) { throw } }
        $logDeadline = (Get-Date).AddSeconds(20)
        do {
            Start-Sleep -Seconds 1
            $logs = Get-ChildItem "$env:BYTE_JUDGE_LOG_DIR/$($route[0])/access/access*.log"
            $evidence = @(Select-String -Path $logs.FullName -SimpleMatch "$probe 404")
        } while ($evidence.Count -eq 0 -and (Get-Date) -lt $logDeadline)
        if ($evidence.Count -eq 0) { throw "Missing downstream access evidence: $probe" }
        Write-Host "PASS discovered route + downstream access log: $($route[0])"
    }
    foreach ($path in @('/internal/v1/probe', '/actuator/health', '/byte-judge-user-model/actuator/health', '/api/v1/judge/probe')) {
        try { Invoke-WebRequest "http://127.0.0.1:8080$path" -UseBasicParsing -TimeoutSec 10 | Out-Null; throw "Unexpected public route: $path" }
        catch { if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 404) { throw } }
    }
    Write-Host 'PASS public route allowlist.'

    # Required Nacos Config is visible in the client processes, not only in the server console.
    foreach ($check in @(
            @('gateway', 'request correlation configured ingress=true'),
            @('user', 'request correlation configured ingress=false'),
            @('gateway', '[service=gateway]'),
            @('user', '[service=user]'),
            @('gateway', 'configContract=auth-infra-v1'),
            @('user', 'configContract=auth-infra-v1'))) {
        $applicationLog = Join-Path $env:BYTE_JUDGE_LOG_DIR "$($check[0])/application.log"
        $deadline = (Get-Date).AddSeconds(30)
        do {
            $evidence = if (Test-Path $applicationLog) { Select-String -Path $applicationLog -SimpleMatch $check[1] } else { @() }
            if ($evidence.Count -eq 0) { Start-Sleep -Seconds 1 }
        } while ($evidence.Count -eq 0 -and (Get-Date) -lt $deadline)
        if ($evidence.Count -eq 0) { throw "Missing Nacos Config application evidence in $applicationLog : $($check[1])" }
        Write-Host "PASS Nacos Config applied in client: $($check[0]) $($check[1])"
    }

    # The same real HTTP call is visible in the gateway and user service application logs.
    $csrfResponse = Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/auth/csrf' -UseBasicParsing -TimeoutSec 10
    # The gateway and the downstream service both emit the correlation header (same value), so
    # Windows PowerShell 5.1 joins the duplicate response headers into 'id,id'. Take the first.
    $requestId = (@($csrfResponse.Headers['X-Request-ID'])[0] -split ',')[0].Trim()
    if (!$requestId) { throw 'Gateway response is missing X-Request-ID.' }
    foreach ($service in @('gateway', 'user')) {
        $applicationLog = Join-Path $env:BYTE_JUDGE_LOG_DIR "$service/application.log"
        $deadline = (Get-Date).AddSeconds(20)
        do {
            $evidence = if (Test-Path $applicationLog) {
                @(Select-String -Path $applicationLog -SimpleMatch "http requestId=$requestId" |
                    Where-Object { $_.Line.Contains("[service=$service]") -and $_.Line.Contains("[requestId=$requestId]") })
            } else { @() }
            if ($evidence.Count -eq 0) { Start-Sleep -Seconds 1 }
        } while ($evidence.Count -eq 0 -and (Get-Date) -lt $deadline)
        if ($evidence.Count -eq 0) { throw "Missing request correlation evidence in $applicationLog for $requestId" }
        Write-Host "PASS request correlation: $service requestId=$requestId"
    }
    Write-Host 'PASS skeleton smoke test. Business, shared session, reliable messaging and sandbox remain unverified.'
} finally { Pop-Location }
