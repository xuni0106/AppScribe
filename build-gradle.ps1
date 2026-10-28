#requires -Version 5.1
<#
  Runs the checked-in Gradle Wrapper with a usable JDK, then verifies the APK.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\build-gradle.ps1
    powershell -ExecutionPolicy Bypass -File .\build-gradle.ps1 -SkipVerify
#>
[CmdletBinding()]
param(
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

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

$Jdk = Find-Jdk
if (-not $Jdk) {
    Write-Host "No JDK found. Set JAVA_HOME or install Android Studio." -ForegroundColor Red
    exit 1
}
$env:JAVA_HOME = $Jdk
$env:PATH = (Join-Path $Jdk "bin") + ";" + $env:PATH

$gradlew = Join-Path $Root "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    Write-Host "gradlew.bat is missing." -ForegroundColor Red
    exit 1
}

Write-Host "Building with Gradle Wrapper..." -ForegroundColor Cyan
Write-Host "JDK: $Jdk"
& $gradlew deliverRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Gradle build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apk = Join-Path $Root "artifacts\ShiJi-1.0.3-release.apk"
if (-not (Test-Path $apk)) {
    Write-Host "Expected APK was not produced: $apk" -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "APK: $apk" -ForegroundColor Green
Get-FileHash -Algorithm SHA256 $apk | Format-List

if (-not $SkipVerify) {
    & (Join-Path $Root "verify.ps1") -Apk $apk
    if ($LASTEXITCODE -ne 0) {
        Write-Host "APK verification failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}
exit 0
