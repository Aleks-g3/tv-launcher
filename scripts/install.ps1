<#
.SYNOPSIS
Instaluje i uruchamia Start TV na telewizorze z Android TV / Google TV przez ADB po Wi-Fi.

.EXAMPLE
.\scripts\install.ps1 -TvIp 192.168.1.50

.EXAMPLE
.\scripts\install.ps1 -TvIp 192.168.1.50 -Build
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0, HelpMessage = 'Adres IP telewizora, np. 192.168.1.50')]
    [string]$TvIp,

    [ValidateRange(1, 65535)]
    [int]$Port = 5555,

    [string]$ApkPath,

    [switch]$Build
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$applicationId = 'pl.aleks.starttv'
$launcherActivity = '.MainActivity'

function Resolve-AdbPath {
    $fromPath = Get-Command -Name adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($fromPath) {
        return $fromPath.Source
    }

    $sdkRoots = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ }
    foreach ($sdkRoot in $sdkRoots) {
        $candidate = Join-Path $sdkRoot 'platform-tools\adb.exe'
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw 'Nie znaleziono adb. Dodaj platform-tools do PATH albo ustaw zmienną ANDROID_SDK_ROOT.'
}

function Get-DeviceState([string]$AdbPath, [string]$Serial) {
    $match = & $AdbPath devices |
        Select-String -Pattern ('^' + [regex]::Escape($Serial) + '\s+(\S+)$') |
        Select-Object -First 1
    if (-not $match) {
        return $null
    }
    return $match.Matches[0].Groups[1].Value
}

if ($Build) {
    Write-Host 'Budowanie APK (assembleDebug)...'
    & (Join-Path $projectRoot 'gradlew.bat') assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw 'Build nie powiódł się.'
    }
}

$apk = if ($ApkPath) { $ApkPath } else { Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk' }
$apk = [System.IO.Path]::GetFullPath($apk)
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Brak pliku APK: $apk`nZbuduj go: .\gradlew.bat assembleDebug"
}

$adb = Resolve-AdbPath
$serial = '{0}:{1}' -f $TvIp, $Port

Write-Host "ADB: $adb"
Write-Host "Łączenie z $serial ..."
& $adb connect $serial | Out-Host

$state = $null
for ($attempt = 1; $attempt -le 10; $attempt++) {
    $state = Get-DeviceState -AdbPath $adb -Serial $serial
    if ($state -eq 'device') {
        break
    }
    if ($state -eq 'unauthorized') {
        throw 'Telewizor oczekuje zgody: zaakceptuj okno „Zezwalaj na debugowanie USB” na ekranie TV i uruchom skrypt ponownie.'
    }
    Start-Sleep -Seconds 2
}
if ($state -ne 'device') {
    throw "Nie udało się połączyć z $serial (stan: $state). Sprawdź adres IP, sieć Wi-Fi i włączone debugowanie USB."
}

Write-Host "Instalowanie $([System.IO.Path]::GetFileName($apk)) ..."
$installOutput = & $adb -s $serial install -r $apk 2>&1
$installOutput | ForEach-Object { Write-Host $_ }
if (-not ($installOutput -match 'Success')) {
    throw 'Instalacja nie powiodła się.'
}

Write-Host 'Uruchamianie Start TV ...'
$launchOutput = & $adb -s $serial shell am start -n "$applicationId/$launcherActivity" 2>&1
$launchOutput | ForEach-Object { Write-Host $_ }
if ($launchOutput -match 'Error') {
    throw 'Nie udało się uruchomić aplikacji.'
}

Write-Host "Gotowe — Start TV działa na $serial."
