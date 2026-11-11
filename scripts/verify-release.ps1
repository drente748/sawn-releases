<#
.SYNOPSIS
    Verifies a built Sawn release APK before it is published or distributed.

.DESCRIPTION
    Three things must hold for a release APK to be safe to hand out:

    1. It is signed with the release key that already signed the installed
       copies. A different key means users cannot update over it.
    2. Its versionName/versionCode are the ones the release is tagged with.
    3. The public update manifest URL is baked into BuildConfig. Without it the
       "check for updates" button is permanently disabled in that APK — and it
       cannot be fixed later, because the URL is compiled in, not fetched.

.PARAMETER Apk
    Path to the APK. Defaults to app/build/outputs/apk/release/app-release.apk.

.PARAMETER ExpectedVersion
    Optional. When given, the APK's versionName must match it exactly.

.PARAMETER ManifestUrl
    The manifest URL the APK is expected to carry. Defaults to the published one.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1
    powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ExpectedVersion 2.2.0
#>
[CmdletBinding()]
param(
    [string]$Apk = "",
    [string]$ExpectedVersion = "",
    [string]$ManifestUrl = "https://drente748.github.io/warden/updates/manifest.json"
)

$ErrorActionPreference = "Stop"

$expectedCert = "3e2ffe2f03ef0c9506a863fb32b63e5e8ed2a44ed99e819c0306da8bae2d1d86"

function Write-Step { param([string]$Text) Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Text) Write-Host "    OK  $Text" -ForegroundColor Green }
function Write-Fail { param([string]$Text) Write-Host "    XX  $Text" -ForegroundColor Red }

$root = Split-Path -Parent $PSScriptRoot
if (-not $Apk) {
    # Prefer the Gradle output; fall back to the published artefact in release/.
    $gradleApk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $gradleApk) {
        $Apk = $gradleApk
    } else {
        $published = Get-ChildItem (Join-Path $root "release") -Filter "Sawn-*.apk" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($published) { $Apk = $published.FullName }
    }
}
if (-not $Apk -or -not (Test-Path $Apk)) { throw "APK not found. Build it, or pass -Apk explicitly." }
Write-Step "Verifying $([IO.Path]::GetFileName($Apk))"
Write-Host "    at  $Apk" -ForegroundColor DarkGray

# ---------------------------------------------------------------- toolchain
$sdk = $null
if ($env:ANDROID_HOME) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) {
    $properties = Join-Path $root "local.properties"
    if (Test-Path $properties) {
        $line = Get-Content $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            # Java escapes the drive colon: "C\:/Users/...". Unescape it, then
            # normalise the separators to the Windows form.
            $sdk = ($line -replace '^sdk\.dir=', '').Trim()
            $sdk = $sdk -replace '\\:', ':'
            $sdk = $sdk -replace '/', '\'
        }
    }
}
if (-not $sdk) { throw "Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." }

$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw "No build-tools found under $sdk." }
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$aapt = Join-Path $buildTools.FullName "aapt.exe"
Write-Ok "build-tools $($buildTools.Name)"

$failures = 0

# ---------------------------------------------------------------- signature
Write-Step "Signing certificate"
$certOutput = (& $apksigner verify --print-certs $Apk 2>&1) -join "`n"
$match = [regex]::Match($certOutput, "Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)")
if (-not $match.Success) {
    Write-Fail "Could not read the signing certificate from the APK."
    $failures++
} else {
    $actual = $match.Groups[1].Value.ToLower()
    if ($actual -eq $expectedCert) {
        Write-Ok "certificate matches the release key"
    } else {
        Write-Fail "certificate mismatch"
        Write-Host "        expected: $expectedCert"
        Write-Host "        actual:   $actual"
        $failures++
    }
}

# ---------------------------------------------------------------- version
Write-Step "Version"
$badging = (& $aapt dump badging $Apk 2>&1) -join "`n"
$versionCode = [regex]::Match($badging, "versionCode='(\d+)'").Groups[1].Value
$versionName = [regex]::Match($badging, "versionName='([^']+)'").Groups[1].Value
if (-not $versionCode) {
    Write-Fail "Could not read versionCode/versionName from the APK."
    $failures++
} else {
    Write-Ok "versionName=$versionName versionCode=$versionCode"
    if ($ExpectedVersion -and $versionName -ne $ExpectedVersion) {
        Write-Fail "versionName is $versionName but the release expects $ExpectedVersion"
        $failures++
    }
}

# ---------------------------------------------------------------- manifest url
Write-Step "Update manifest URL"

Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

# The dex entries inside an APK are deflated, so the text is invisible in the raw
# file - it has to be decompressed before it can be searched.
function Get-DexText {
    param([string]$Path)
    $texts = @()
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notmatch '^classes\d*\.dex$') { continue }
            $stream = $entry.Open()
            try {
                $memory = New-Object IO.MemoryStream
                $stream.CopyTo($memory)
                $texts += [Text.Encoding]::ASCII.GetString($memory.ToArray())
            } finally {
                $stream.Dispose()
            }
        }
    } finally {
        $zip.Dispose()
    }
    return $texts
}

$dexText = (Get-DexText $Apk) -join "`n"
$found = $dexText.Contains($ManifestUrl)

if ($found) {
    Write-Ok "manifest URL is baked into the APK"
} else {
    Write-Fail "manifest URL is NOT in the APK — update checks stay disabled in this build."
    Write-Host "        expected: $ManifestUrl"
    # Say which hosting the build actually points at, if it points at one. A stale
    # URL from an earlier owner is the failure mode that silently disables every
    # update check, so it deserves to be named rather than merely reported missing.
    $stale = [regex]::Match($dexText, "https://[a-z0-9.\-]+\.github\.io/warden/updates/manifest\.json")
    if ($stale.Success) {
        Write-Host "        this build points at: $($stale.Value)"
        Write-Host "        it was built before the hosting moved; rebuild it."
    }
    Write-Host "        rebuild with: `$env:SAWN_UPDATE_MANIFEST_URL = `"$ManifestUrl`""
    $failures++
}

Write-Host ""
if ($failures -eq 0) {
    Write-Host "Release APK verified." -ForegroundColor Green
    exit 0
}
Write-Host "$failures check(s) failed." -ForegroundColor Red
exit 1
