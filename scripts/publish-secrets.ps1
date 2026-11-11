# Re-publishes the Actions secrets without the trailing CRLF that PowerShell's
# pipeline adds. GNU base64 rejects a stray carriage return, so a secret stored
# with one fails to decode in the workflow with "base64: error: invalid input".
$ErrorActionPreference = "Stop"

function Write-Step { param([string]$Text) Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Text) Write-Host "    OK  $Text" -ForegroundColor Green }

$repo = "drente748/warden"
$root = Split-Path -Parent $PSScriptRoot

$props = @{}
Get-Content (Join-Path $root "local.properties") | ForEach-Object {
    if ($_ -match '^\s*([^=]+?)\s*=\s*(.*)$') { $props[$matches[1]] = $matches[2] }
}

function Set-SecretFromText {
    param([string]$Name, [string]$Value)
    $file = Join-Path $env:TEMP ("sawn-secret-" + $Name + ".txt")
    # No BOM, no trailing newline: the file is fed straight to stdin.
    [IO.File]::WriteAllText($file, $Value, [Text.UTF8Encoding]::new($false))
    try {
        cmd /c "gh secret set $Name --repo $repo < `"$file`"" 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "gh secret set failed for $Name" }
        Write-Ok $Name
    } finally {
        Remove-Item $file -Force -ErrorAction SilentlyContinue
    }
}

Write-Step "Re-publishing the Actions secrets (no trailing newline)"

$storePath = $props["SAWN_STORE_FILE"]
if (-not (Test-Path $storePath)) { $storePath = Join-Path $root $storePath }
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($storePath))
Set-SecretFromText -Name "SAWN_KEYSTORE_BASE64" -Value $keystoreBase64

Set-SecretFromText -Name "SAWN_STORE_PASSWORD" -Value $props["SAWN_STORE_PASSWORD"]
Set-SecretFromText -Name "SAWN_KEY_ALIAS" -Value $props["SAWN_KEY_ALIAS"]
Set-SecretFromText -Name "SAWN_KEY_PASSWORD" -Value $props["SAWN_KEY_PASSWORD"]
Set-SecretFromText -Name "SAWN_SIGNING_CERT_SHA256" -Value "3e2ffe2f03ef0c9506a863fb32b63e5e8ed2a44ed99e819c0306da8bae2d1d86"

Write-Host ""
Write-Host "Secrets re-published for $repo" -ForegroundColor Green
