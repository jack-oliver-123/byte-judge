param([string]$JdkPath = 'C:\Develop\JDK\jdk21')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/use-java21.ps1" -JdkPath $JdkPath
. "$PSScriptRoot/local-env.ps1"
# The gateway, user and problem services import required configuration from Nacos and refuse to start
# without it. Publishing is idempotent, so this also repairs a missing Data ID.
& "$PSScriptRoot/import-nacos-config.ps1"
$pidDir = Join-Path $BackendRoot '.local/pids'
New-Item -ItemType Directory -Force $pidDir | Out-Null
$names = @('gateway', 'user', 'problem', 'submission', 'contest', 'judge')
foreach ($name in $names) {
    $port = 8080 + [array]::IndexOf($names, $name)
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Port $port already in use. Stop existing services first." }
    $managementPort = $port + 1000
    if (Get-NetTCPConnection -LocalPort $managementPort -State Listen -ErrorAction SilentlyContinue) { throw "Port $managementPort already in use." }
}
foreach ($name in $names) {
    $artifact = if ($name -eq 'gateway') { 'byte-judge-gateway' } else { "byte-judge-models/byte-judge-$name-model" }
    $module = Split-Path $artifact -Leaf
    $jar = Join-Path $BackendRoot "$artifact/target/$module-0.0.1-SNAPSHOT.jar"
    if (!(Test-Path $jar)) { throw "Build first: $jar" }
}
foreach ($name in $names) {
    $artifact = if ($name -eq 'gateway') { 'byte-judge-gateway' } else { "byte-judge-models/byte-judge-$name-model" }
    $module = Split-Path $artifact -Leaf
    $jar = Join-Path $BackendRoot "$artifact/target/$module-0.0.1-SNAPSHOT.jar"
    $logDir = Join-Path $env:BYTE_JUDGE_LOG_DIR $name
    New-Item -ItemType Directory -Force $logDir | Out-Null
    $process = Start-Process -FilePath "$env:JAVA_HOME/bin/java.exe" -ArgumentList @('-Xmx256m', "`"-DJM.LOG.PATH=$logDir`"", '-jar', "`"$jar`"") -WorkingDirectory $BackendRoot -WindowStyle Hidden -RedirectStandardOutput "$logDir/console.out.log" -RedirectStandardError "$logDir/console.err.log" -PassThru
    Set-Content "$pidDir/$name.pid" $process.Id
}
Write-Host 'Six services starting; run scripts/smoke-test.ps1 to wait and verify.'
