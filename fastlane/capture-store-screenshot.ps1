[CmdletBinding(DefaultParameterSetName = 'List')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Seed')]
    [switch]$Seed,

    [Parameter(Mandatory = $true, ParameterSetName = 'Prepare')]
    [switch]$Prepare,

    [Parameter(Mandatory = $true, ParameterSetName = 'Capture')]
    [ValidateNotNullOrEmpty()]
    [string]$CaptureKey,

    [Parameter(Mandatory = $true, ParameterSetName = 'Finish')]
    [switch]$Finish,

    [Parameter(ParameterSetName = 'Seed')]
    [Parameter(ParameterSetName = 'Prepare')]
    [Parameter(ParameterSetName = 'Capture')]
    [Parameter(ParameterSetName = 'Finish')]
    [string]$DeviceSerial,

    [Parameter(ParameterSetName = 'Capture')]
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$fastlaneRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $fastlaneRoot)).Path
$manifestPath = Join-Path $fastlaneRoot 'play-store-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$screenshotAssets = @(
    $manifest.assets | Where-Object { $_.kind -in @('phoneScreenshot', 'tabletScreenshot') }
)

function Resolve-AdbExecutable {
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidates.Add((Join-Path $sdkRoot 'platform-tools\adb.exe'))
            $candidates.Add((Join-Path $sdkRoot 'platform-tools/adb'))
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'))
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }
    throw 'adb was not found. Install Android SDK Platform-Tools or configure ANDROID_SDK_ROOT.'
}

function Resolve-ConnectedDevice {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [string]$RequestedSerial
    )

    $deviceOutput = @(& $AdbPath devices 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed:`n$($deviceOutput -join "`n")"
    }
    $connected = @(
        $deviceOutput |
            ForEach-Object { [string]$_ } |
            Where-Object { $_ -match '^([^\s]+)\s+device$' } |
            ForEach-Object { ([regex]::Match($_, '^([^\s]+)')).Groups[1].Value }
    )
    if (-not [string]::IsNullOrWhiteSpace($RequestedSerial)) {
        if ($RequestedSerial -notin $connected) {
            throw "The requested device is not connected and authorized: $RequestedSerial"
        }
        return $RequestedSerial
    }
    if ($connected.Count -eq 0) {
        throw 'No authorized Android device is connected.'
    }
    if ($connected.Count -gt 1) {
        throw "Multiple devices are connected. Specify -DeviceSerial. Devices: $($connected -join ', ')"
    }
    return $connected[0]
}

function Invoke-AdbCommand {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string[]]$CommandArguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # adb pull writes normal progress to stderr. Windows PowerShell wraps that output in a
        # NativeCommandError when the caller uses Stop, so the process exit code is authoritative.
        $ErrorActionPreference = 'Continue'
        $commandOutput = @(& $AdbPath -s $Serial @CommandArguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "adb command failed: $($CommandArguments -join ' ')`n$($commandOutput -join "`n")"
    }
    return $commandOutput
}

function Get-PngInfo {
    param([Parameter(Mandatory = $true)][string]$Path)

    $bytes = [IO.File]::ReadAllBytes($Path)
    $signature = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if ($bytes.Length -lt 33) {
        throw 'Captured file is too short to be a PNG.'
    }
    for ($index = 0; $index -lt $signature.Length; $index++) {
        if ($bytes[$index] -ne $signature[$index]) {
            throw 'Captured file does not have a PNG signature.'
        }
    }
    if ([Text.Encoding]::ASCII.GetString($bytes, 12, 4) -ne 'IHDR') {
        throw 'Captured PNG does not start with an IHDR chunk.'
    }
    $width = [uint32]$bytes[16] * 16777216 + [uint32]$bytes[17] * 65536 +
        [uint32]$bytes[18] * 256 + [uint32]$bytes[19]
    $height = [uint32]$bytes[20] * 16777216 + [uint32]$bytes[21] * 65536 +
        [uint32]$bytes[22] * 256 + [uint32]$bytes[23]
    [pscustomobject]@{
        Width = $width
        Height = $height
        BitDepth = [int]$bytes[24]
        ColorType = [int]$bytes[25]
        Bytes = $bytes.Length
    }
}

function Invoke-Seed {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial
    )

    $gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw 'gradlew.bat is missing.'
    }
    Push-Location $repositoryRoot
    try {
        & $gradleWrapper ':app:assembleDebug' ':app:assembleDebugAndroidTest' '--no-daemon'
        if ($LASTEXITCODE -ne 0) {
            throw 'Debug app or AndroidTest APK build failed.'
        }
    } finally {
        Pop-Location
    }

    $appApk = Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk'
    $testApk = Join-Path $repositoryRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    foreach ($apk in @($appApk, $testApk)) {
        if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
            throw "Expected APK is missing: $apk"
        }
        Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
            'install', '-r', '-t', $apk
        ) | Out-Null
    }

    $instrumentationOutput = Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
        'shell',
        'am',
        'instrument',
        '-w',
        '-r',
        '-e',
        'class',
        'com.mochisofts.mata.store.StoreScreenshotDataSeedTest',
        'com.mochisofts.mata.debug.test/androidx.test.runner.AndroidJUnitRunner'
    )
    $instrumentationText = $instrumentationOutput -join "`n"
    if ($instrumentationText -notmatch 'OK \(1 test\)') {
        throw "Screenshot data seeding did not pass:`n$instrumentationText"
    }

    Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
        'shell', 'am', 'force-stop', 'com.mochisofts.mata.debug'
    ) | Out-Null
    Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
        'shell',
        'am',
        'start',
        '-n',
        'com.mochisofts.mata.debug/com.mochisofts.mata.app.MainActivity'
    ) | Out-Null

    Write-Host 'Debug data replaced with the store screenshot fixture.'
    Write-Host 'Seeded: 3 categories, 6 TODOs, 2 notifications, and 3 history entries.'
}

function Invoke-Prepare {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial
    )

    $commands = [System.Collections.Generic.List[string[]]]::new()
    $commands.Add(@('shell', 'svc', 'power', 'stayon', 'true'))
    $commands.Add(@('shell', 'settings', 'put', 'global', 'sysui_demo_allowed', '1'))
    $commands.Add(@('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'enter'))
    $commands.Add(@('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'clock', '-e', 'hhmm', '1000'))
    $commands.Add(@('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'battery', '-e', 'level', '100', '-e', 'plugged', 'false'))
    $commands.Add(@('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'network', '-e', 'wifi', 'show', '-e', 'level', '4', '-e', 'mobile', 'hide'))
    $commands.Add(@('shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'notifications', '-e', 'visible', 'false'))
    foreach ($command in $commands) {
        Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments $command | Out-Null
    }
    Write-Host 'Screenshot session prepared: time=10:00, battery=100%, notifications hidden.'
    Write-Host 'The script does not change display size, density, orientation, theme, or app navigation.'
}

function Invoke-Finish {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial
    )

    Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
        'shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command', 'exit'
    ) | Out-Null
    Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
        'shell', 'svc', 'power', 'stayon', 'false'
    ) | Out-Null
    Write-Host 'Screenshot session finished and System UI demo mode exited.'
}

function Invoke-Capture {
    param(
        [Parameter(Mandatory = $true)][string]$AdbPath,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)]$Asset,
        [switch]$Overwrite
    )

    $outputPath = [IO.Path]::GetFullPath((Join-Path $fastlaneRoot $Asset.path))
    $outputPrefix = $fastlaneRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    if (-not $outputPath.StartsWith($outputPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Manifest screenshot path escapes fastlane: $($Asset.path)"
    }
    if ((Test-Path -LiteralPath $outputPath) -and -not $Overwrite) {
        throw "Screenshot already exists. Review it or specify -Force: $outputPath"
    }

    $outputDirectory = Split-Path -Parent $outputPath
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $temporaryPath = "$outputPath.capture"
    if (Test-Path -LiteralPath $temporaryPath) {
        Remove-Item -LiteralPath $temporaryPath -Force
    }
    $remotePath = "/sdcard/Download/mata-$($Asset.captureKey).png"
    try {
        Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
            'shell', 'screencap', '-p', $remotePath
        ) | Out-Null
        Invoke-AdbCommand -AdbPath $AdbPath -Serial $Serial -CommandArguments @(
            'pull', $remotePath, $temporaryPath
        ) | Out-Null

        $png = Get-PngInfo -Path $temporaryPath
        if ($png.Width -ne [int]$Asset.width -or $png.Height -ne [int]$Asset.height) {
            throw "Captured image is $($png.Width)x$($png.Height); expected $($Asset.width)x$($Asset.height)."
        }
        if ($png.BitDepth -ne 8 -or $png.ColorType -notin @(2, 6)) {
            throw "Captured PNG must use 8-bit RGB or RGBA channels; bitDepth=$($png.BitDepth), colorType=$($png.ColorType)."
        }

        Move-Item -LiteralPath $temporaryPath -Destination $outputPath -Force:$Overwrite
        Write-Host "Captured $($Asset.captureKey): $outputPath"
        Write-Host "PNG: $($png.Width)x$($png.Height), $($png.Bytes) bytes"
        Write-Host "Alt text: $($Asset.altText)"
    } finally {
        & $AdbPath -s $Serial shell rm -f $remotePath 2>&1 | Out-Null
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

if ($PSCmdlet.ParameterSetName -eq 'List') {
    $screenshotAssets |
        Select-Object @{ Name = 'CaptureKey'; Expression = { $_.captureKey } },
            @{ Name = 'Size'; Expression = { "$($_.width)x$($_.height)" } },
            @{ Name = 'AltText'; Expression = { $_.altText } } |
        Format-Table -AutoSize
    Write-Host 'Use -Seed only on a disposable Debug installation; it deletes existing Debug app data.'
    return
}

$selectedAsset = $null
if ($PSCmdlet.ParameterSetName -eq 'Capture') {
    $matchingAssets = @($screenshotAssets | Where-Object { $_.captureKey -eq $CaptureKey })
    if ($matchingAssets.Count -ne 1) {
        throw "Unknown CaptureKey: $CaptureKey. Run this script without arguments to list keys."
    }
    $selectedAsset = $matchingAssets[0]
}

$adbExecutable = Resolve-AdbExecutable
$serial = Resolve-ConnectedDevice -AdbPath $adbExecutable -RequestedSerial $DeviceSerial
Write-Host "Android device: $serial"

switch ($PSCmdlet.ParameterSetName) {
    'Seed' {
        Invoke-Seed -AdbPath $adbExecutable -Serial $serial
    }
    'Prepare' {
        Invoke-Prepare -AdbPath $adbExecutable -Serial $serial
    }
    'Capture' {
        Invoke-Capture -AdbPath $adbExecutable -Serial $serial -Asset $selectedAsset -Overwrite:$Force
    }
    'Finish' {
        Invoke-Finish -AdbPath $adbExecutable -Serial $serial
    }
}
