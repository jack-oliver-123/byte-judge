param([string]$JdkPath = 'C:\Develop\JDK\jdk21')
$ErrorActionPreference = 'Stop'
$java = Join-Path $JdkPath 'bin/java.exe'
if (!(Test-Path $java)) { throw "JDK not found: $JdkPath" }
$version = & $java --version | Out-String
if ($LASTEXITCODE -ne 0 -or $version -notmatch '(?m)^(java|openjdk) 21\.') { throw 'Java 21 is required.' }
$env:JAVA_HOME = (Resolve-Path $JdkPath).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME=$env:JAVA_HOME (current process only)"
