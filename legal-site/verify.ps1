[CmdletBinding()]
param(
    [switch]$ForRelease
)

$ErrorActionPreference = 'Stop'
$siteRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $siteRoot
$errors = [System.Collections.Generic.List[string]]::new()

function Add-VerificationError {
    param([string]$Message)
    $errors.Add($Message)
}

$requiredFiles = @(
    '.nojekyll',
    'index.html',
    '404.html',
    'assets/styles.css',
    'README.md',
    'serve.mjs',
    'robots.txt',
    'CNAME.template',
    'app-ads.txt.template',
    'mata/privacy/index.html',
    'mata/terms/index.html',
    'mata/commercial-transactions/index.html',
    'mata/external-transmission/index.html'
)

foreach ($relativePath in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $siteRoot $relativePath) -PathType Leaf)) {
        Add-VerificationError "Required file is missing: $relativePath"
    }
}

$htmlFiles = Get-ChildItem -LiteralPath $siteRoot -Filter '*.html' -File -Recurse
foreach ($file in $htmlFiles) {
    $relativePath = $file.FullName.Substring($siteRoot.Length).TrimStart('\').Replace('\', '/')
    $content = [IO.File]::ReadAllText($file.FullName)

    foreach ($requiredPattern in @(
        '<html lang="ja">',
        '<meta name="viewport"',
        '<meta name="description"',
        '<title>',
        '<main id="main-content"'
    )) {
        if (-not $content.Contains($requiredPattern)) {
            Add-VerificationError "$relativePath is missing a required element: $requiredPattern"
        }
    }

    if ($content -match '<script(?:\s|>)') {
        Add-VerificationError "$relativePath contains JavaScript."
    }

    if ($content -match '(?i)google-analytics|googletagmanager|doubleclick|facebook\.net') {
        Add-VerificationError "$relativePath contains an analytics or advertising tag."
    }

    $hrefMatches = [regex]::Matches($content, 'href="([^"]+)"')
    foreach ($match in $hrefMatches) {
        $href = $match.Groups[1].Value
        if ($href.StartsWith('#') -or $href.StartsWith('mailto:') -or $href.StartsWith('https://') -or $href.StartsWith('http://')) {
            continue
        }

        if (-not $href.StartsWith('/')) {
            Add-VerificationError "$relativePath contains a non-root-relative internal link: $href"
            continue
        }

        $pathOnly = ($href -split '[?#]', 2)[0].TrimStart('/')
        if ([string]::IsNullOrEmpty($pathOnly)) {
            $target = Join-Path $siteRoot 'index.html'
        } elseif ([IO.Path]::HasExtension($pathOnly)) {
            $target = Join-Path $siteRoot $pathOnly
        } else {
            $target = Join-Path (Join-Path $siteRoot $pathOnly) 'index.html'
        }

        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
            Add-VerificationError "$relativePath contains a broken internal link: $href"
        }
    }
}

$requiredContent = @{
    'mata/privacy/index.html' = @('Google Mobile Ads', 'Holidays JP', 'com.mochisofts@gmail.com')
    'mata/terms/index.html' = @('remove_ads', 'Google Play', 'com.mochisofts@gmail.com')
    'mata/commercial-transactions/index.html' = @('remove_ads', '500', 'Google Play')
    'mata/external-transmission/index.html' = @('Google Mobile Ads', 'User Messaging Platform', 'Google Play Billing', 'Holidays JP', 'GitHub Pages')
}

$sourceDocuments = @{
    'mata/privacy/index.html' = '.agents/non-functional-specs/legal-specs/privacy-policy.md'
    'mata/terms/index.html' = '.agents/non-functional-specs/legal-specs/terms-of-use.md'
    'mata/commercial-transactions/index.html' = '.agents/non-functional-specs/legal-specs/commercial-transactions.md'
    'mata/external-transmission/index.html' = '.agents/non-functional-specs/legal-specs/external-transmission.md'
}

foreach ($entry in $sourceDocuments.GetEnumerator()) {
    $htmlPath = Join-Path $siteRoot $entry.Key
    $sourcePath = Join-Path $repositoryRoot $entry.Value
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        Add-VerificationError "Canonical source is missing: $($entry.Value)"
        continue
    }
    if (-not (Test-Path -LiteralPath $htmlPath -PathType Leaf)) {
        continue
    }

    $sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
    $htmlContent = [IO.File]::ReadAllText($htmlPath)
    if (-not $htmlContent.Contains("source-sha256: $sourceHash")) {
        Add-VerificationError "$($entry.Key) is not synchronized with $($entry.Value)"
    }
}

foreach ($entry in $requiredContent.GetEnumerator()) {
    $path = Join-Path $siteRoot $entry.Key
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        continue
    }
    $content = [IO.File]::ReadAllText($path)
    foreach ($phrase in $entry.Value) {
        if (-not $content.Contains($phrase)) {
            Add-VerificationError "$($entry.Key) is missing required source content: $phrase"
        }
    }
}

if ($ForRelease) {
    foreach ($file in $htmlFiles) {
        $relativePath = $file.FullName.Substring($siteRoot.Length).TrimStart('\').Replace('\', '/')
        $content = [IO.File]::ReadAllText($file.FullName)
        foreach ($blockedText in @('[', 'draft-notice')) {
            if ($content.Contains($blockedText)) {
                Add-VerificationError "Release-blocking draft content remains: $relativePath / $blockedText"
            }
        }
        if ($relativePath -ne '404.html' -and $content.Contains('noindex')) {
            Add-VerificationError "Release-blocking draft content remains: $relativePath / noindex"
        }
    }

    foreach ($releaseFile in @('CNAME', 'app-ads.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $siteRoot $releaseFile) -PathType Leaf)) {
            Add-VerificationError "Required release file is missing: $releaseFile"
        }
    }

    $robotsPath = Join-Path $siteRoot 'robots.txt'
    if ((Test-Path -LiteralPath $robotsPath) -and ([IO.File]::ReadAllText($robotsPath) -match '(?im)^Disallow:\s*/\s*$')) {
        Add-VerificationError 'robots.txt blocks the entire site.'
    }
}

if ($errors.Count -gt 0) {
    Write-Error ("Legal site verification failed.`n- " + ($errors -join "`n- "))
    exit 1
}

$mode = if ($ForRelease) { 'release' } else { 'draft' }
Write-Host "Legal site $mode verification passed. HTML files: $($htmlFiles.Count)"
