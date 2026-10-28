#requires -Version 5.1
<#
  Builds, aligns and signs the 时迹 release APK using only Android SDK tools.
  No Gradle, AndroidX, Maven or network access is required after the SDK/JDK
  are present. All paths are rooted at this script's directory.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\build.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$AppName = "ShiJi"
$VersionName = "1.0.3"
$VersionCode = 4
$MinSdk = 26
$TargetSdk = 35
$CompileSdk = 35
$PackageId = "com.shiji.tracker"
$Keystore = Join-Path $Root "keystore\shiji-release.p12"
$KeystorePass = "ShiJi-Local-2026"
$KeyAlias = "shiji"
$BuildDir = Join-Path $Root "build-native"
$ArtifactDir = Join-Path $Root "artifacts"
$VerifyDir = Join-Path $Root "verify\native"
$FinalApk = Join-Path $ArtifactDir "$AppName-$VersionName-native.apk"

function Fail([string]$message) {
    Write-Host "ERROR: $message" -ForegroundColor Red
    exit 1
}

function Find-Jdk {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $candidate) { return (Resolve-Path $env:JAVA_HOME).Path }
    }
    $candidates = @(
        "C:\Program Files\Android\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jre",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-21",
        "C:\Program Files\Eclipse Adoptium\jdk-17",
        (Join-Path $env:USERPROFILE ".jdks")
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate "bin\java.exe"))) {
            return (Resolve-Path $candidate).Path
        }
    }
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) {
        return (Split-Path -Parent (Split-Path -Parent $java.Source))
    }
    return $null
}

function Find-Sdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        "C:\Android\Sdk", "E:\Android\Sdk")
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate "platform-tools"))) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

function Invoke-Tool([string]$file, [string[]]$arguments, [string]$label) {
    Write-Host "  $label" -ForegroundColor DarkGray
    & $file @arguments
    if ($LASTEXITCODE -ne 0) {
        Fail "$label failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Capture([string]$file, [string[]]$arguments, [string]$outputFile,
                        [string]$label) {
    Write-Host "  $label" -ForegroundColor DarkGray
    $prior = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $file @arguments 2>&1
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prior
    $output | Set-Content -Path $outputFile -Encoding UTF8
    if ($code -ne 0) {
        Fail "$label failed with exit code $code"
    }
    return $output
}

$Jdk = Find-Jdk
if (-not $Jdk) {
    Fail "No JDK found. Install JDK 17+ or Android Studio, or set JAVA_HOME."
}
$env:JAVA_HOME = $Jdk
$env:PATH = (Join-Path $Jdk "bin") + ";" + $env:PATH

$Sdk = Find-Sdk
if (-not $Sdk) {
    Fail "Android SDK not found. Set ANDROID_HOME or install it under the default LocalAppData path."
}

$BuildToolsRoot = Join-Path $Sdk "build-tools"
if (-not (Test-Path $BuildToolsRoot)) {
    Fail "Android build-tools are missing from $Sdk"
}
$buildToolsCandidates = Get-ChildItem $BuildToolsRoot -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } |
    Sort-Object { [version]($_.Name -replace '[^0-9.].*$','') } -Descending
if (-not $buildToolsCandidates -or $buildToolsCandidates.Count -eq 0) {
    Fail "No usable Android build-tools installation found."
}
$BuildTools = $buildToolsCandidates[0].FullName

$PlatformRoot = Join-Path $Sdk "platforms"
$platformCandidates = Get-ChildItem $PlatformRoot -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } |
    Sort-Object { [int](($_.Name -split '-')[-1] -replace '\.0$','') } -Descending
$platform = $platformCandidates | Where-Object { $_.Name -eq "android-$CompileSdk" } |
    Select-Object -First 1
if (-not $platform) {
    $platform = $platformCandidates | Select-Object -First 1
}
if (-not $platform) {
    Fail "No Android platform android.jar found under $Sdk\platforms."
}
$AndroidJar = Join-Path $platform.FullName "android.jar"

$aapt2 = Join-Path $BuildTools "aapt2.exe"
$d8 = Join-Path $BuildTools "d8.bat"
$zipalign = Join-Path $BuildTools "zipalign.exe"
$apksigner = Join-Path $BuildTools "apksigner.bat"
$keytool = Join-Path $Jdk "bin\keytool.exe"
$apkanalyzer = Join-Path $Sdk "cmdline-tools\latest\bin\apkanalyzer.bat"

foreach ($tool in @($aapt2, $d8, $zipalign, $apksigner, $keytool)) {
    if (-not (Test-Path $tool)) {
        Fail "Required build tool is missing: $tool"
    }
}

Write-Host ""
Write-Host "ShiJi release build" -ForegroundColor Cyan
Write-Host "  JDK        : $Jdk"
Write-Host "  SDK        : $Sdk"
Write-Host "  build-tools: $(Split-Path -Leaf $BuildTools)"
Write-Host "  platform   : $(Split-Path -Leaf $platform.FullName)"
Write-Host "  target     : API $TargetSdk (min API $MinSdk)"
Write-Host ""

if (-not (Test-Path $Keystore)) {
    Write-Host "Generating local release keystore..." -ForegroundColor Yellow
    $keytoolArgs = @(
        "-genkeypair", "-v",
        "-keystore", $Keystore,
        "-storetype", "PKCS12",
        "-storepass", $KeystorePass,
        "-keypass", $KeystorePass,
        "-alias", $KeyAlias,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=ShiJi Release, OU=Personal, O=ShiJi, L=Local, ST=Local, C=CN"
    )
    New-Item -ItemType Directory -Force (Split-Path -Parent $Keystore) | Out-Null
    Invoke-Tool $keytool $keytoolArgs "generate keystore"
}

if (Test-Path $BuildDir) {
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force $BuildDir | Out-Null
New-Item -ItemType Directory -Force $ArtifactDir | Out-Null
New-Item -ItemType Directory -Force $VerifyDir | Out-Null

$resDir = Join-Path $Root "app\src\main\res"
$sourceManifest = Join-Path $Root "app\src\main\AndroidManifest.xml"
$manifest = Join-Path $BuildDir "AndroidManifest.xml"
$resZip = Join-Path $BuildDir "resources.zip"
$genDir = Join-Path $BuildDir "generated"
$classesDir = Join-Path $BuildDir "classes"
$dexDir = Join-Path $BuildDir "dex"
$unsignedApk = Join-Path $BuildDir "app-unsigned.apk"
$alignedApk = Join-Path $BuildDir "app-aligned.apk"
New-Item -ItemType Directory -Force $genDir, $classesDir, $dexDir | Out-Null

Write-Host "[1/6] Compiling resources" -ForegroundColor Cyan
Invoke-Tool $aapt2 @("compile", "--dir", $resDir, "-o", $resZip) "aapt2 compile"

Write-Host "[2/6] Linking manifest and resources" -ForegroundColor Cyan
# AGP uses namespace for the package; the standalone aapt2 pipeline needs the
# package attribute in a generated copy of the shared manifest.
$manifestText = [System.IO.File]::ReadAllText($sourceManifest)
if ($manifestText -notmatch 'package="') {
    $manifestText = $manifestText -replace '<manifest ', ('<manifest package="' + $PackageId + '" ')
}
[System.IO.File]::WriteAllText($manifest, $manifestText,
    (New-Object System.Text.UTF8Encoding($false)))
$aapt2LinkArgs = @(
    "link",
    "-o", $unsignedApk,
    "-I", $AndroidJar,
    "--manifest", $manifest,
    "--java", $genDir,
    "--auto-add-overlay",
    "--min-sdk-version", "$MinSdk",
    "--target-sdk-version", "$TargetSdk",
    "--version-code", "$VersionCode",
    "--version-name", $VersionName,
    $resZip
)
Invoke-Tool $aapt2 $aapt2LinkArgs "aapt2 link"

Write-Host "[3/6] Compiling Java sources" -ForegroundColor Cyan
$javaSources = @(Get-ChildItem (Join-Path $Root "app\src\main\java") -Recurse -Filter "*.java" |
    ForEach-Object { $_.FullName })
$generatedR = Get-ChildItem $genDir -Recurse -Filter "R.java" | Select-Object -First 1
if (-not $generatedR) { Fail "aapt2 did not generate R.java" }
$javaSources += $generatedR.FullName
$javacArgs = @(
    "-encoding", "UTF-8",
    "-source", "8",
    "-target", "8",
    "-Xlint:-options",
    "-classpath", $AndroidJar,
    "-d", $classesDir
) + $javaSources
Invoke-Tool (Join-Path $Jdk "bin\javac.exe") $javacArgs "javac"

Write-Host "[4/6] Dexing and packaging" -ForegroundColor Cyan
$classesJar = Join-Path $BuildDir "classes.jar"
Invoke-Tool (Join-Path $Jdk "bin\jar.exe") @("cf", $classesJar,
    "-C", $classesDir, ".") "jar"
Invoke-Tool $d8 @("--release", "--min-api", "$MinSdk", "--lib", $AndroidJar,
    "--output", $dexDir, $classesJar) "d8"
$dexFiles = @(Get-ChildItem $dexDir -Filter "classes*.dex" | Sort-Object Name)
if ($dexFiles.Count -eq 0) { Fail "D8 did not produce a classes.dex file." }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsignedApk,
    [System.IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($dex in $dexFiles) {
        if ($zip.GetEntry($dex.Name)) {
            $zip.GetEntry($dex.Name).Delete()
        }
        $entry = $zip.CreateEntry($dex.Name,
            [System.IO.Compression.CompressionLevel]::Optimal)
        $input = [System.IO.File]::OpenRead($dex.FullName)
        $output = $entry.Open()
        try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
    }
} finally {
    $zip.Dispose()
}

Write-Host "[5/6] Aligning and signing" -ForegroundColor Cyan
Invoke-Tool $zipalign @("-f", "-p", "4", $unsignedApk, $alignedApk) "zipalign"
$signArgs = @(
    "sign",
    "--ks", $Keystore,
    "--ks-key-alias", $KeyAlias,
    "--ks-pass", "pass:$KeystorePass",
    "--key-pass", "pass:$KeystorePass",
    "--min-sdk-version", "$MinSdk",
    "--v1-signing-enabled", "false",
    "--v2-signing-enabled", "true",
    "--v3-signing-enabled", "true",
    "--v4-signing-enabled", "false",
    "--out", $FinalApk,
    $alignedApk
)
if (Test-Path $FinalApk) { Remove-Item -Force $FinalApk }
Invoke-Tool $apksigner $signArgs "apksigner sign"
Get-ChildItem $ArtifactDir -Filter "*.idsig" -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "[6/6] Verifying artifact" -ForegroundColor Cyan
$verifyOutput = Invoke-Capture $apksigner @("verify", "--verbose", "--print-certs",
    $FinalApk) (Join-Path $VerifyDir "apksigner-verify.txt") "apksigner verify"
$verifyOutput | ForEach-Object { Write-Host $_ }

$badgingLines = Invoke-Capture $aapt2 @("dump", "badging", $FinalApk) `
    (Join-Path $VerifyDir "aapt2-badging.txt") "aapt2 dump badging"

$zipRead = [System.IO.Compression.ZipFile]::OpenRead($FinalApk)
try {
    $entryList = $zipRead.Entries | ForEach-Object { $_.FullName } | Sort-Object
    $entryList | Set-Content -Path (Join-Path $VerifyDir "apk-entries.txt") -Encoding UTF8
    $hasDex = $entryList -contains "classes.dex"
} finally { $zipRead.Dispose() }
if (-not $hasDex) { Fail "classes.dex is missing from the signed APK." }

if (Test-Path $apkanalyzer) {
    try {
        Invoke-Capture $apkanalyzer @("manifest", "print", $FinalApk) `
            (Join-Path $VerifyDir "manifest-print.txt") "apkanalyzer manifest" | Out-Null
        Invoke-Capture $apkanalyzer @("apk", "summary", $FinalApk) `
            (Join-Path $VerifyDir "apk-summary.txt") "apkanalyzer summary" | Out-Null
    } catch {
        Write-Host "  apkanalyzer cross-check skipped: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

$hash = Get-FileHash -Algorithm SHA256 $FinalApk
$hashLine = ($hash.Hash.ToLower()) + "  " + (Split-Path -Leaf $FinalApk)
$hashLine | Set-Content -Path (Join-Path $ArtifactDir "$AppName-$VersionName-native.apk.sha256") -Encoding ASCII

Write-Host ""
Write-Host "APK: $FinalApk" -ForegroundColor Green
Write-Host "SHA256: $($hash.Hash)" -ForegroundColor Green
Write-Host "Size: $([math]::Round((Get-Item $FinalApk).Length / 1MB, 2)) MB"
$packageLine = $badgingLines | Where-Object { $_ -like "package:*" } | Select-Object -First 1
$sdkLine = $badgingLines | Where-Object {
    $_ -match '^(sdkVersion|minSdkVersion|targetSdkVersion):'
}
Write-Host $packageLine
$sdkLine | ForEach-Object { Write-Host $_ }
Write-Host "Verification files: $VerifyDir" -ForegroundColor Green
Write-Host ""
exit 0
