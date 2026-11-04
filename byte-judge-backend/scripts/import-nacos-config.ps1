param(
    [string]$ServerAddr = $env:NACOS_SERVER_ADDR,
    [string]$NamespaceId = $env:NACOS_CONFIG_NAMESPACE,
    [string]$Group = $env:NACOS_CONFIG_GROUP,
    [string]$ConfigDir = (Join-Path (Split-Path $PSScriptRoot -Parent) 'infra/nacos/config')
)
$ErrorActionPreference = 'Stop'
if (!$ServerAddr) { $ServerAddr = '127.0.0.1:8848' }
if (!$Group) { $Group = 'BYTE_JUDGE' }
if (!$NamespaceId) { $NamespaceId = 'public' }

# Local and controlled environments authenticate this administrative write with the Nacos
# server identity, which is supplied by the environment and never written into shared config.
$headers = @{}
if ($env:NACOS_AUTH_IDENTITY_KEY -and $env:NACOS_AUTH_IDENTITY_VALUE) {
    $headers[$env:NACOS_AUTH_IDENTITY_KEY] = $env:NACOS_AUTH_IDENTITY_VALUE
}

$files = @(Get-ChildItem $ConfigDir -Filter '*.yml' | Sort-Object Name)
if ($files.Count -eq 0) { throw "No Nacos config templates found in $ConfigDir" }

foreach ($file in $files) {
    $dataId = $file.Name
    $content = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
    $body = @{
        dataId      = $dataId
        groupName   = $Group
        namespaceId = $NamespaceId
        content     = $content
        type        = 'yaml'
    }
    try {
        $published = Invoke-RestMethod -Method Post -Uri "http://$ServerAddr/nacos/v3/admin/cs/config" `
            -Headers $headers -Body $body -ContentType 'application/x-www-form-urlencoded' -TimeoutSec 15
    } catch {
        # The request body and identity headers are not echoed; only the data id and server.
        throw "Nacos publish failed for $dataId at $ServerAddr`: $($_.Exception.Message)"
    }
    if ($published.code -ne 0) {
        throw "Nacos publish rejected for $dataId`: code=$($published.code) message=$($published.message)"
    }

    # The client read path can briefly trail the write; retry a few times before failing.
    $matched = $false
    for ($attempt = 1; $attempt -le 5 -and !$matched; $attempt++) {
        $readBack = Invoke-RestMethod -Method Get `
            -Uri "http://$ServerAddr/nacos/v3/client/cs/config?dataId=$dataId&groupName=$Group&namespaceId=$NamespaceId" `
            -TimeoutSec 15
        $matched = $readBack.code -eq 0 -and $readBack.data -and
            [string]::Equals([string]$readBack.data.content, $content, [StringComparison]::Ordinal)
        if (!$matched) { Start-Sleep -Milliseconds 200 }
    }
    if (!$matched) { throw "Nacos read-back mismatch for $dataId; the imported configuration is not active." }
    Write-Host "IMPORTED $dataId group=$Group namespace=$NamespaceId bytes=$($content.Length)"
}
Write-Host 'Nacos configuration import and read-back passed.'
