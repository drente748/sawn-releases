<#
.SYNOPSIS
    Prepares the GitHub side of Sawn's self-hosted updates: Actions secrets and
    the GitHub Pages site that serves updates/manifest.json.

.DESCRIPTION
    Run this ONCE per repository, as an account that has admin rights on it.
    It reads the release keystore that already lives on this machine and pushes
    the four signing secrets plus the certificate fingerprint into GitHub
    Actions, then turns on GitHub Pages for the gh-pages branch.

    Nothing is printed: the keystore is base64-encoded in memory and piped
    straight into `gh secret set`, so it never lands in the shell history, the
    console, or a temp file.

    It deliberately does NOT create or push a git tag and does NOT publish
    anything. Publishing is a separate, explicit step — see docs/update-hosting.md.

.PARAMETER Repo
    owner/repo to configure. Default: the upstream repository of this checkout.

.PARAMETER Version
    Expected versionName of the release, e.g. 2.2.0. Only used for reporting.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/setup-update-hosting.ps1
#>
[CmdletBinding()]
param(
    [string]$Repo = "",
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"

function Write-Step { param([string]$Text) Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok   { param([string]$Text) Write-Host "    OK  $Text" -ForegroundColor Green }
function Write-Warn { param([string]$Text) Write-Host "    !!  $Text" -ForegroundColor Yellow }
function Write-Fail { param([string]$Text) Write-Host "    XX  $Text" -ForegroundColor Red }

# ---------------------------------------------------------------- prerequisites
Write-Step "Checking prerequisites"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) not found. Install it from https://cli.github.com and run `gh auth login`."
}

$login = (& gh api user --jq ".login" 2>$null)
if (-not $login) {
    throw "gh is not authenticated. Run `gh auth login` as the repository owner, then re-run this script."
}
Write-Ok "authenticated as $login"

if (-not $Repo) {
    $remote = (& git remote get-url origin 2>$null)
    if ($remote -notmatch "github\.com[:/](?<owner>[^/]+)/(?<repo>[^/]+?)(\.git)?$") {
        throw "Could not derive owner/repo from the git remote. Pass -Repo explicitly."
    }
    $Repo = "$($Matches.owner)/$($Matches.repo)"
}
Write-Ok "repository: $Repo"

$permissions = (& gh api "repos/$Repo" --jq ".permissions.admin" 2>$null)
if ($permissions -ne "true") {
    throw ("Account '$login' does not have admin rights on '$Repo'. " +
           "Either log in as the owner (`gh auth login`) or grant admin access, then re-run.")
}
Write-Ok "admin rights confirmed"

# ---------------------------------------------------------------- local secrets
Write-Step "Reading signing material from local.properties"

$root = Split-Path -Parent $PSScriptRoot
$localProperties = Join-Path $root "local.properties"
if (-not (Test-Path $localProperties)) {
    throw "local.properties not found at $localProperties. It must define SAWN_STORE_FILE, SAWN_STORE_PASSWORD, SAWN_KEY_ALIAS and SAWN_KEY_PASSWORD."
}

$props = @{}
Get-Content $localProperties | ForEach-Object {
    if ($_ -match '^\s*([^=]+?)\s*=\s*(.*)$') { $props[$matches[1]] = $matches[2] }
}
foreach ($key in @("SAWN_STORE_FILE", "SAWN_STORE_PASSWORD", "SAWN_KEY_ALIAS", "SAWN_KEY_PASSWORD")) {
    if (-not $props[$key]) { throw "local.properties is missing $key." }
}

$storePath = $props["SAWN_STORE_FILE"]
if (-not (Test-Path $storePath)) { $storePath = Join-Path $root $storePath }
if (-not (Test-Path $storePath)) { throw "Keystore not found: $storePath" }
Write-Ok "keystore: $storePath"

$keytool = $null
if ($env:JAVA_HOME) { $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"; if (Test-Path $candidate) { $keytool = $candidate } }
if (-not $keytool) {
    $candidate = Get-Command keytool -ErrorAction SilentlyContinue
    if ($candidate) { $keytool = $candidate.Source }
}
if (-not $keytool) { throw "keytool not found. Set JAVA_HOME to a JDK (17 or newer). The certificate fingerprint cannot be computed without it." }

$keytoolOutput = & $keytool -list -v -keystore $storePath -alias $props["SAWN_KEY_ALIAS"] -storepass $props["SAWN_STORE_PASSWORD"] 2>&1
$match = [regex]::Match(($keytoolOutput -join "`n"), "SHA256:\s*([0-9A-Fa-f:]{95})")
if (-not $match.Success) { throw "Could not read the SHA-256 fingerprint of the signing certificate from the keystore." }
$certSha = ($match.Groups[1].Value -replace ":", "").ToLower()
Write-Ok "signing certificate SHA-256: $certSha"

$expected = "3e2ffe2f03ef0c9506a863fb32b63e5e8ed2a44ed99e819c0306da8bae2d1d86"
if ($certSha -ne $expected) {
    Write-Warn "This keystore is NOT the one recorded in docs/update-hosting.md."
    Write-Warn "  documented: $expected"
    Write-Warn "  this one:   $certSha"
    Write-Warn "Updating with a different key breaks every already-installed copy."
    throw "Refusing to continue: the keystore does not match the documented release key."
}

# ---------------------------------------------------------------- secrets
Write-Step "Publishing Actions secrets"

$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($storePath))
if ([string]::IsNullOrWhiteSpace($keystoreBase64)) { throw "Keystore encoded to an empty string." }

# Piped through stdin on purpose: `gh secret set --body` would expose the value
# in the process command line.
$keystoreBase64 | & gh secret set SAWN_KEYSTORE_BASE64 --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to set SAWN_KEYSTORE_BASE64." }
Write-Ok "SAWN_KEYSTORE_BASE64"

$props["SAWN_STORE_PASSWORD"] | & gh secret set SAWN_STORE_PASSWORD --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to set SAWN_STORE_PASSWORD." }
Write-Ok "SAWN_STORE_PASSWORD"

$props["SAWN_KEY_ALIAS"] | & gh secret set SAWN_KEY_ALIAS --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to set SAWN_KEY_ALIAS." }
Write-Ok "SAWN_KEY_ALIAS"

$props["SAWN_KEY_PASSWORD"] | & gh secret set SAWN_KEY_PASSWORD --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to set SAWN_KEY_PASSWORD." }
Write-Ok "SAWN_KEY_PASSWORD"

$certSha | & gh secret set SAWN_SIGNING_CERT_SHA256 --repo $Repo
if ($LASTEXITCODE -ne 0) { throw "Failed to set SAWN_SIGNING_CERT_SHA256." }
Write-Ok "SAWN_SIGNING_CERT_SHA256"

# ---------------------------------------------------------------- pages
Write-Step "Ensuring the gh-pages branch exists"

$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new()

# A missing branch answers 404, and gh reports that on stderr. With
# $ErrorActionPreference = 'Stop' that becomes a terminating error, so the
# probe runs with the preference relaxed and reports only its exit code.
function Test-GhCall {
    param([string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $null = & gh @Arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    return ($code -eq 0)
}

# gh --input - reads stdin, and a PowerShell pipeline into a native command
# does not arrive as clean JSON (BOM/line-ending handling differs). Writing
# the body to a file and passing --input <file> is what actually works.
function Send-GhJson {
    param([string]$Method, [string]$Path, [object]$Body)
    $file = Join-Path $env:TEMP ("sawn-gh-" + [Guid]::NewGuid().ToString("N") + ".json")
    try {
        [IO.File]::WriteAllText($file, ($Body | ConvertTo-Json -Depth 6 -Compress), [Text.UTF8Encoding]::new($false))
        & gh api --method $Method $Path --input $file | Out-Null
        return ($LASTEXITCODE -eq 0)
    } finally {
        Remove-Item $file -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-GhCall @("api", "repos/$Repo/git/ref/heads/gh-pages", "--silent"))) {
    $defaultBranch = (& gh api "repos/$Repo" --jq ".default_branch").Trim()
    $headSha = (& gh api "repos/$Repo/git/ref/heads/$defaultBranch" --jq ".object.sha").Trim()
    if (-not (Send-GhJson -Method POST -Path "repos/$Repo/git/refs" -Body @{ ref = "refs/heads/gh-pages"; sha = $headSha })) {
        throw "Could not create the gh-pages branch."
    }
    Write-Ok "created gh-pages from $defaultBranch"
} else {
    Write-Ok "gh-pages already exists"
}

Write-Step "Seeding the update manifest placeholder"

if (-not (Test-GhCall @("api", "repos/$Repo/contents/updates/manifest.json?ref=gh-pages", "--silent"))) {
    # versionCode 1 keeps the placeholder valid: the app reads it as "no newer
    # version" rather than as a broken manifest. versionCode 0 would make the
    # checker report an error instead of "you are up to date".
    $placeholder = @{
        versionCode = 1
        versionName = "0.0.1"
        apkUrl     = "https://example.com/placeholder.apk"
        sha256     = ("0" * 64)
        changelog  = "placeholder"
        mandatory  = $false
    } | ConvertTo-Json
    $content = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($placeholder))
    $contentBody = @{
        message = "chore: initialise update manifest"
        content = $content
        branch  = "gh-pages"
    }
    if (-not (Send-GhJson -Method PUT -Path "repos/$Repo/contents/updates/manifest.json" -Body $contentBody)) {
        throw "Could not seed updates/manifest.json on gh-pages."
    }
    Write-Ok "seeded updates/manifest.json"
} else {
    Write-Ok "updates/manifest.json already exists (left untouched)"
}

Write-Step "Enabling GitHub Pages (gh-pages branch)"

$pagesBody = @{ "source" = @{ "branch" = "gh-pages"; "path" = "/" } }
$pagesJson = $pagesBody | ConvertTo-Json -Depth 5

if (Test-GhCall @("api", "repos/$Repo/pages", "--silent")) {
    if (-not (Send-GhJson -Method PUT -Path "repos/$Repo/pages" -Body $pagesBody)) {
        throw "Failed to update the GitHub Pages configuration."
    }
    Write-Ok "GitHub Pages updated to publish from gh-pages / (root)"
} else {
    if (-not (Send-GhJson -Method POST -Path "repos/$Repo/pages" -Body $pagesBody)) {
        throw ("Could not enable GitHub Pages. Enable it manually: " +
               "Settings -> Pages -> Deploy from a branch -> gh-pages / (root).")
    }
    Write-Ok "GitHub Pages enabled to publish from gh-pages / (root)"
}

# ---------------------------------------------------------------- report
$owner = ($Repo -split "/")[0]
$name = ($Repo -split "/")[1]
$manifestUrl = "https://$($owner.ToLower()).github.io/$($name.ToLower())/updates/manifest.json"

Write-Host ""
Write-Host "Hosting is configured." -ForegroundColor Green
Write-Host ""
Write-Host "  Public manifest URL : $manifestUrl"
Write-Host "  Release assets       : https://github.com/$Repo/releases"
Write-Host "  Update check is off  : the installed APK must be rebuilt with"
Write-Host "                         SAWN_UPDATE_MANIFEST_URL set to the URL above."
if ($Version) {
    Write-Host ""
    Write-Host "Next release tag: v$Version" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "Nothing has been published yet. To publish, commit the release code,"
Write-Host "push it, then tag and push the tag — see docs/update-hosting.md."
