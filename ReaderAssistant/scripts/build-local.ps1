param([string]$JdkPath, [string]$SdkPath, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $JdkPath) { $JdkPath = Join-Path $env:USERPROFILE '.jdks/ms-17.0.20.1' }
if (-not $SdkPath) { $SdkPath = Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/java.exe'))) { throw '请通过 -JdkPath 指定 JDK 17' }
if (-not (Test-Path -LiteralPath $SdkPath)) { throw '请通过 -SdkPath 指定 Android SDK' }
$env:JAVA_HOME = $JdkPath
# 构建工具状态与调试签名保存在本工程的忽略目录，不改动个人 .android 或其他项目签名。
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.android-local'
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
('sdk.dir=' + $SdkPath.Replace('\', '/')) | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding utf8
Push-Location $projectRoot
try {
    $gradleArgs = @('--no-daemon', "-Dorg.gradle.java.home=$JdkPath", ':app:assembleDebug', ':app:testDebugUnitTest', ':app:assembleDebugAndroidTest', ':app:lintDebug')
    if ($Offline) { $gradleArgs += '--offline' }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "构建失败：$LASTEXITCODE" }
} finally { Pop-Location }
