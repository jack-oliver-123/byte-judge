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

# This helper is for the repository's isolated, loopback-only Compose deployment. Server
# identity is highly privileged: remote deployments must use their own TLS/RBAC management
# path, rather than send this identity to an arbitrary address.
$address = if ($ServerAddr -match '^https?://') { $ServerAddr } else { "http://$ServerAddr" }
$server = [Uri]$address
if (!$server.IsLoopback -or $server.UserInfo -or $server.AbsolutePath -ne '/' -or $server.Query -or $server.Fragment) {
    throw 'The local import helper requires a loopback Nacos address without credentials, path or query.'
}
if (!$env:NACOS_AUTH_IDENTITY_KEY -or !$env:NACOS_AUTH_IDENTITY_VALUE) {
    throw 'Load the controlled local Nacos administrative identity before importing configuration.'
}
$headers = @{}
$headers[$env:NACOS_AUTH_IDENTITY_KEY] = $env:NACOS_AUTH_IDENTITY_VALUE
$base = $server.GetLeftPart([UriPartial]::Authority)

# Publish only the three reviewed templates, not arbitrary YAML files in this directory.
foreach ($dataId in @('byte-judge-common.yml', 'byte-judge-gateway.yml', 'byte-judge-user-model.yml')) {
    $file = Join-Path $ConfigDir $dataId
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing Nacos template: $dataId" }
    $content = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
    $body = @{
        dataId      = $dataId
        groupName   = $Group
        namespaceId = $NamespaceId
        content     = $content
        type        = 'yaml'
    }
    try {
        $published = Invoke-RestMethod -Method Post -Uri "$base/nacos/v3/admin/cs/config" `
            -Headers $headers -Body $body -ContentType 'application/x-www-form-urlencoded; charset=utf-8' -TimeoutSec 15
        if ($published.code -ne 0) { throw 'Rejected by Nacos.' }
    } catch {
        # Server and parser diagnostics may echo submitted content or credentials.
        throw "Nacos publish failed for $dataId. Check the controlled administrative identity and server health."
    }

    $query = 'dataId=' + [Uri]::EscapeDataString($dataId) + '&groupName=' + [Uri]::EscapeDataString($Group) +
        '&namespaceId=' + [Uri]::EscapeDataString($NamespaceId)
    $matched = $false
    for ($attempt = 1; $attempt -le 5 -and !$matched; $attempt++) {
        try {
            $readBack = Invoke-RestMethod -Method Get -Uri "$base/nacos/v3/client/cs/config?$query" `
                -Headers $headers -TimeoutSec 15
            $matched = $readBack.code -eq 0 -and $readBack.data -and
                [string]::Equals([string]$readBack.data.content, $content, [StringComparison]::Ordinal)
        } catch {
            $matched = $false
        }
        if (!$matched -and $attempt -lt 5) { Start-Sleep -Milliseconds 200 }
    }
    if (!$matched) { throw "Nacos read-back failed for $dataId. Check availability and read permission; no content was logged." }
    $byteCount = [Text.Encoding]::UTF8.GetByteCount($content)
    Write-Host "IMPORTED $dataId group=$Group namespace=$NamespaceId bytes=$byteCount"
}
Write-Host 'Nacos configuration import and read-back passed. Restart gateway and user service to apply changes.'
