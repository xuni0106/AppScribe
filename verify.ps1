#requires -Version 5.1
<#
  Verifies a built APK with aapt2, apkanalyzer, zipalign and apksigner, then
  writes reproducible text evidence into verify/.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\verify.ps1
    powershell -ExecutionPolicy Bypass -File .\verify.ps1 -Apk .\artifacts\ShiJi-1.0.3-native.apk
#>
[CmdletBinding()]
param(
    [string]$Apk = "artifacts\ShiJi-1.0.3-release.apk",
    [string]$OutDir = "verify"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
if (-not [System.IO.Path]::IsPathRooted($Apk)) { $Apk = Join-Path $Root $Apk }
if (-not (Test-Path $Apk)) { Write-Host "APK not found: $Apk" -ForegroundColor Red; exit 1 }
if (-not [System.IO.Path]::IsPathRooted($OutDir)) { $OutDir = Join-Path $Root $OutDir }
New-Item -ItemType Directory -Force $OutDir | Out-Null

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        return (Resolve-Path $env:JAVA_HOME).Path
    }
    foreach ($candidate in @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-17"
    )) {
        if (Test-Path (Join-Path $candidate "bin\java.exe")) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

function Find-Sdk {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"), "C:\Android\Sdk")) {
        if ($candidate -and (Test-Path (Join-Path $candidate "platforms"))) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

function Capture([string]$file, [string[]]$arguments, [string]$name) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $file @arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $previous
    $clean = @($output | Where-Object {
        $line = "$_"
        $line -notmatch '^WARNING:' -and
        $line -ne 'System.Management.Automation.RemoteException'
    })
    $clean | Set-Content -Path (Join-Path $OutDir $name) -Encoding UTF8
    if ($code -ne 0) {
        Write-Host "Verification command failed: $file" -ForegroundColor Red
        $clean | ForEach-Object { Write-Host $_ }
        exit 1
    }
    return $clean
}

$Jdk = Find-Jdk
$Sdk = Find-Sdk
if (-not $Jdk) { Write-Host "JDK not found." -ForegroundColor Red; exit 1 }
if (-not $Sdk) { Write-Host "Android SDK not found." -ForegroundColor Red; exit 1 }
$env:JAVA_HOME = $Jdk
$env:PATH = (Join-Path $Jdk "bin") + ";" + $env:PATH

$BuildTools = Get-ChildItem (Join-Path $Sdk "build-tools") -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$','') } -Descending |
    Select-Object -First 1
if (-not $BuildTools) { Write-Host "No build-tools found." -ForegroundColor Red; exit 1 }
$bt = $BuildTools.FullName
$aapt2 = Join-Path $bt "aapt2.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$zipalign = Join-Path $bt "zipalign.exe"
$apkanalyzer = Join-Path $Sdk "cmdline-tools\latest\bin\apkanalyzer.bat"

Write-Host "Verifying $Apk" -ForegroundColor Cyan
$verifyOutput = Capture $apksigner @("verify", "--verbose", "--print-certs", $Apk) `
    "apksigner-verify.txt"
if (-not ($verifyOutput -match 'Verified using v2 scheme.*: true') -or
    -not ($verifyOutput -match 'Verified using v3 scheme.*: true')) {
    Write-Host "FAIL: APK is not signed with v2 and v3 schemes." -ForegroundColor Red
    exit 1
}
$verifyOutput | Where-Object { $_ -match '^Verified using|^Signer|^Number of signers|DN:' } |
    ForEach-Object { Write-Host $_ }

$badging = Capture $aapt2 @("dump", "badging", $Apk) "aapt2-badging.txt"
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
$sdkLines = $badging | Where-Object { $_ -match '^(sdkVersion|minSdkVersion|targetSdkVersion):' }
$permLines = $badging | Where-Object { $_ -match '^uses-permission' }
$sdkLines | ForEach-Object { Write-Host $_ }
$permLines | ForEach-Object { Write-Host $_ }
if ($badging -match 'android.permission.INTERNET') {
    Write-Host "FAIL: INTERNET permission is present." -ForegroundColor Red
    exit 1
}

if (Test-Path $apkanalyzer) {
    Capture $apkanalyzer @("manifest", "print", $Apk) "manifest-print.txt" | Out-Null
    Capture $apkanalyzer @("apk", "summary", $Apk) "apk-summary.txt" | Out-Null
    Capture $apkanalyzer @("manifest", "permissions", $Apk) "apk-permissions.txt" | Out-Null
}

Capture $zipalign @("-c", "-v", "4", $Apk) "zipalign-check.txt" | Out-Null

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $entries = $zip.Entries | ForEach-Object { $_.FullName } | Sort-Object
    $entries | Set-Content -Path (Join-Path $OutDir "apk-entries.txt") -Encoding UTF8
} finally { $zip.Dispose() }
$hasDex = $entries -contains "classes.dex"
$hasResources = $entries -contains "resources.arsc"
if (-not $hasDex) { Write-Host "FAIL: classes.dex missing." -ForegroundColor Red; exit 1 }
if (-not $hasResources) { Write-Host "FAIL: resources.arsc missing." -ForegroundColor Red; exit 1 }

$hash = Get-FileHash -Algorithm SHA256 $Apk
Write-Host ""
Write-Host $packageLine
Write-Host "classes.dex: present"
Write-Host "resources.arsc: present"
Write-Host "SHA256: $($hash.Hash)"
Write-Host "Size: $([math]::Round((Get-Item $Apk).Length / 1KB, 1)) KB"
Write-Host "Reports: $OutDir" -ForegroundColor Green
