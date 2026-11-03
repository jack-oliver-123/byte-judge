param([string]$JdkPath = 'C:\Develop\JDK\jdk21')
$ErrorActionPreference = 'Stop'
$java = Join-Path $JdkPath 'bin/java.exe'
if (!(Test-Path $java)) { throw "JDK not found: $JdkPath" }
$version = & $java -version 2>&1 | Out-String
if ($version -notmatch 'version "21\.') { throw 'Java 21 is required.' }
$env:JAVA_HOME = (Resolve-Path $JdkPath).Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Write-Host "JAVA_HOME=$env:JAVA_HOME (current process only)"
