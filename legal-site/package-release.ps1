[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$siteRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $siteRoot)).Path
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
    [IO.Path]::GetFullPath($OutputDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
}

function Test-IsSameOrChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Candidate,
        [Parameter(Mandatory = $true)][string]$Parent
    )

    if ($Candidate.Equals($Parent, [StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $prefix = $Parent.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    return $Candidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Get-NormalizedRelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $prefix = $Root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $Path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside the package directory: $Path"
    }
    return $Path.Substring($prefix.Length).Replace('\', '/')
}

if ((Test-IsSameOrChildPath -Candidate $outputPath -Parent $siteRoot) -or
    $outputPath.Equals($repositoryRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must not be the source site or repository root.'
}

& (Join-Path $siteRoot 'verify.ps1') -ForRelease

$sourceReparsePoints = @(
    Get-ChildItem -LiteralPath $siteRoot -Recurse -Force |
        Where-Object { ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 }
)
if ($sourceReparsePoints.Count -gt 0) {
    throw 'The legal site contains a symbolic link or reparse point and cannot be packaged.'
}

$releaseFiles = @(
    '.nojekyll',
    'index.html',
    '404.html',
    'robots.txt',
    'CNAME',
    'app-ads.txt'
)
$releaseDirectories = @('assets', 'mata')

foreach ($relativePath in $releaseFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $siteRoot $relativePath) -PathType Leaf)) {
        throw "Required release file is missing: $relativePath"
    }
}
foreach ($relativePath in $releaseDirectories) {
    if (-not (Test-Path -LiteralPath (Join-Path $siteRoot $relativePath) -PathType Container)) {
        throw "Required release directory is missing: $relativePath"
    }
}

if (Test-Path -LiteralPath $outputPath) {
    if (-not (Test-Path -LiteralPath $outputPath -PathType Container)) {
        throw "OutputDirectory is not a directory: $outputPath"
    }
    if (Get-ChildItem -LiteralPath $outputPath -Force | Select-Object -First 1) {
        throw "OutputDirectory must be empty: $outputPath"
    }
} else {
    New-Item -ItemType Directory -Path $outputPath | Out-Null
}

foreach ($relativePath in $releaseFiles) {
    Copy-Item -LiteralPath (Join-Path $siteRoot $relativePath) -Destination $outputPath
}
foreach ($relativePath in $releaseDirectories) {
    Copy-Item -LiteralPath (Join-Path $siteRoot $relativePath) -Destination $outputPath -Recurse
}

$packagedFiles = @(Get-ChildItem -LiteralPath $outputPath -File -Recurse -Force)
$unexpectedFiles = @(
    $packagedFiles | Where-Object {
        $relativePath = Get-NormalizedRelativePath -Root $outputPath -Path $_.FullName
        $isInAllowedDirectory = $false
        foreach ($directory in $releaseDirectories) {
            if ($relativePath.StartsWith("$directory/", [StringComparison]::Ordinal)) {
                $isInAllowedDirectory = $true
                break
            }
        }
        $relativePath -notin $releaseFiles -and -not $isInAllowedDirectory
    }
)
if ($unexpectedFiles.Count -gt 0) {
    throw 'The publication package contains a file outside the release allowlist.'
}

$hashLines = @(
    $packagedFiles |
        Sort-Object FullName |
        ForEach-Object {
            $relativePath = Get-NormalizedRelativePath -Root $outputPath -Path $_.FullName
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $relativePath"
        }
)
[IO.File]::WriteAllLines(
    (Join-Path $outputPath 'SHA256SUMS'),
    [string[]]$hashLines,
    [Text.UTF8Encoding]::new($false)
)

Write-Host "Legal site release package created: $outputPath"
Write-Host "Published files: $($packagedFiles.Count); SHA256SUMS added."
