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
        $instances = Invoke-RestMethod "http://$env:NACOS_SERVER_ADDR/nacos/v3/client/ns/instance/list?serviceName=$service&groupName=BYTE_JUDGE" -TimeoutSec 10
        if ($instances.code -ne 0 -or !($instances.data | Where-Object { $_.healthy -and $_.port -eq (8080 + $index) })) { throw "$service is not registered healthy." }
        Write-Host "PASS health + discovery: $service"
    }
    foreach ($owner in @('user', 'problem', 'submission', 'contest')) {
        $password = [Environment]::GetEnvironmentVariable("MYSQL_$($owner.ToUpper())_PASSWORD")
        $sql = "SELECT DATABASE(); SHOW GRANTS FOR CURRENT_USER();"
        $grants = docker compose --env-file .env.local exec -T -e "MYSQL_PWD=$password" mysql mysql "-ubj_$owner" "bj_$owner" -N -e $sql
        if ($LASTEXITCODE -ne 0 -or ($grants -join ' ') -notmatch "bj_$owner") { throw "$owner database connection failed." }
        foreach ($other in @('user', 'problem', 'submission', 'contest') | Where-Object { $_ -ne $owner }) {
            docker compose --env-file .env.local exec -T -e "MYSQL_PWD=$password" mysql mysql "-ubj_$owner" "bj_$other" -e 'SELECT 1' 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { throw "$owner unexpectedly has access to bj_$other" }
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
            @('user', '[service=user]'))) {
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
    $requestId = @($csrfResponse.Headers['X-Request-ID'])[0]
    if (!$requestId) { throw 'Gateway response is missing X-Request-ID.' }
    foreach ($service in @('gateway', 'user')) {
        $applicationLog = Join-Path $env:BYTE_JUDGE_LOG_DIR "$service/application.log"
        $deadline = (Get-Date).AddSeconds(20)
        do {
            $evidence = if (Test-Path $applicationLog) { Select-String -Path $applicationLog -SimpleMatch "requestId=$requestId" } else { @() }
            if ($evidence.Count -eq 0) { Start-Sleep -Seconds 1 }
        } while ($evidence.Count -eq 0 -and (Get-Date) -lt $deadline)
        if ($evidence.Count -eq 0) { throw "Missing request correlation evidence in $applicationLog for $requestId" }
        Write-Host "PASS request correlation: $service requestId=$requestId"
    }
    Write-Host 'PASS skeleton smoke test. Business, shared session, reliable messaging and sandbox remain unverified.'
} finally { Pop-Location }
