<#
  husk-companion.ps1 - PC companion for the Husk app (screen mirroring via scrcpy). PART OF Husk.

  Termux-INDEPENDENT: scrcpy reaches the phone through the app's own adb bridge (AdbForward) on
  Tailscale-IP:15557 -> the device's adbd. No WSL, no ssh, no socat, no Termux adb server.

  Runs on a freshly installed Windows 11: bootstraps scrcpy via winget (built into Win11), pairs
  the PC hands-free via the app's /pair endpoint (Android 12 Wireless Debugging = TLS, one-time
  pairing per PC), connects, and creates two desktop shortcuts (phone screen + DeX/TV).

  Requirement: Tailscale installed + logged into the same tailnet as the phone.
  Get the phone's Tailscale IP from the Husk app on the phone.

  Usage:
    One-time setup (on a new PC):   powershell -ExecutionPolicy Bypass -File husk-companion.ps1
    Shortcuts call it as:           ... -File <local copy> -Display 0   (or -Display 2 = DeX/TV)
#>
param(
  [int]$Display = -1,                       # -1 = install mode; 0/2 = launcher (shortcut)
  [string]$TsIp = '',                       # phone's Tailscale IP (prompted if empty)
  [int]$RelayPort = 15557,                  # AdbForward bridge port (outside adb's 5555-5585 emulator scan)
  [int]$AppPort = 8090
)
$ErrorActionPreference = 'Stop'
$cfg = Join-Path $env:LOCALAPPDATA 'Husk\config.txt'   # remembers the Tailscale IP between runs

function Get-TsIp {
  if ($TsIp) { return $TsIp }
  if (Test-Path $cfg) { return (Get-Content $cfg -Raw).Trim() }
  return (Read-Host "Phone's Tailscale IP (see the Husk app)").Trim()
}

function Find-Scrcpy {
  $c = Get-Command scrcpy -ErrorAction SilentlyContinue
  if ($c) { return $c.Source }
  $g = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Genymobile.scrcpy*" -Recurse -Filter scrcpy.exe -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($g) { return $g.FullName }
  return $null
}
# Always use the adb next to scrcpy (matches scrcpy-server version), else PATH adb.
function Get-Adb($scrcpyPath) {
  if ($scrcpyPath) { $a = Join-Path (Split-Path $scrcpyPath) 'adb.exe'; if (Test-Path $a) { return $a } }
  $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { return $c.Source }
  return 'adb'
}
function Connect-Device($adb, $dev) {
  & $adb connect $dev | Out-Null
  Start-Sleep -Milliseconds 800
  return (((& $adb devices) -join "`n") -match [regex]::Escape($dev) + "\s+device")
}

$ip = Get-TsIp
if (-not $ip) { Write-Host "No Tailscale IP given."; Read-Host 'Press Enter'; exit 1 }
$dev = "${ip}:${RelayPort}"

# ---------------- launcher mode (called by shortcuts) ----------------
if ($Display -ge 0) {
  $scrcpy = Find-Scrcpy
  if (-not $scrcpy) { Write-Host "scrcpy missing - run setup (husk-companion.ps1 with no arguments)."; Read-Host 'Press Enter'; exit 1 }
  $adb = Get-Adb $scrcpy
  if (-not (Connect-Device $adb $dev)) {
    Write-Host "Could not reach the phone ($dev)."
    Write-Host "Check: Tailscale on? App running (http://${ip}:${AppPort}/healthz)? PC paired (run setup again)?"
    Read-Host 'Press Enter'; exit 1
  }
  $title = if ($Display -eq 2) { 'Husk-TV' } else { 'Husk' }
  & $scrcpy -s $dev --display-id=$Display --window-title=$title --no-audio
  if ($LASTEXITCODE -ne 0) { Write-Host "scrcpy closed. Is DeX/TV on (display 2)?"; Read-Host 'Press Enter' }
  exit 0
}

# ---------------- install mode (one-time on a new PC) ----------------
Write-Host "== Husk companion setup =="

try { $h = (curl.exe -s --max-time 8 "http://${ip}:${AppPort}/healthz") } catch { $h = '' }
if ($h -ne 'ok') { Write-Host "ERROR: cannot reach the app at http://${ip}:${AppPort} (Tailscale logged in? phone awake? right IP?)."; Read-Host 'Press Enter'; exit 1 }
Write-Host "  app reached (healthz=ok)"

$scrcpy = Find-Scrcpy
if (-not $scrcpy) {
  Write-Host "  installing scrcpy via winget ..."
  winget install --id Genymobile.scrcpy -e --silent --accept-package-agreements --accept-source-agreements | Out-Null
  $scrcpy = Find-Scrcpy
}
if (-not $scrcpy) { Write-Host "ERROR: scrcpy install failed. Install manually: winget install Genymobile.scrcpy"; Read-Host 'Press Enter'; exit 1 }
$adb = Get-Adb $scrcpy
Write-Host "  scrcpy: $scrcpy"

# One-time Wireless-Debugging pairing (Android 12 WD = TLS). Skip if already connected.
if (-not (Connect-Device $adb $dev)) {
  Write-Host "  pairing PC with the phone's Wireless Debugging (fetching code from the app) ..."
  $j = curl.exe -s --max-time 70 "http://${ip}:${AppPort}/pair" | ConvertFrom-Json
  if (-not $j.addr -or -not $j.code) { Write-Host "ERROR: /pair returned no code (is accessibility enabled on the phone?)."; Read-Host 'Press Enter'; exit 1 }
  Write-Host "  $((& $adb pair $j.addr $j.code) -join ' ')"
  if (-not (Connect-Device $adb $dev)) { Write-Host "ERROR: connect after pairing failed ($dev)."; Read-Host 'Press Enter'; exit 1 }
}
Write-Host "  connected: $dev"

# Persist IP + copy self to a local path (no Mark-of-the-Web/SAC block from a synced folder) + shortcuts
$localDir = Join-Path $env:LOCALAPPDATA 'Husk'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
Set-Content -Path $cfg -Value $ip -Encoding ascii
$localScript = Join-Path $localDir 'husk-companion.ps1'
Copy-Item -LiteralPath $PSCommandPath -Destination $localScript -Force
$ps = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$desktop = [Environment]::GetFolderPath('Desktop')
$shell = New-Object -ComObject WScript.Shell
foreach ($g in @(
  @{ Navn = 'Husk (scrcpy).lnk';    Disp = 0; Icon = 'shell32.dll,18' },
  @{ Navn = 'Husk TV (scrcpy).lnk'; Disp = 2; Icon = 'shell32.dll,15' }
)) {
  $lnk = $shell.CreateShortcut((Join-Path $desktop $g.Navn))
  $lnk.TargetPath = $ps
  $lnk.Arguments  = "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$localScript`" -Display $($g.Disp)"
  $lnk.IconLocation = $g.Icon
  $lnk.WorkingDirectory = $localDir
  $lnk.Save()
  Write-Host "  shortcut: $($g.Navn)"
}
Write-Host "DONE. Double-click 'Husk (scrcpy)' on the desktop."
Read-Host 'Press Enter'
