<#
  husk-companion.ps1 - PC companion for the Husk app (screen mirroring via scrcpy). PART OF Husk.

  Termux-INDEPENDENT: scrcpy reaches the phone through the app's own adb bridge (AdbForward) on
  Tailscale-IP:15557 -> the device's adbd. No WSL, no ssh, no socat, no Termux adb server.

  Runs on a freshly installed Windows 11: bootstraps scrcpy via winget (built into Win11), gets
  the phone's access token (Husk 1.4+: approve on the phone), pairs the PC hands-free via the
  app's /pair endpoint (Android 12 Wireless Debugging = TLS, one-time pairing per PC), connects,
  and creates two desktop shortcuts (phone screen + DeX/TV).

  Access token (Husk 1.4+): every app endpoint except /healthz, / and /token/request+status needs
  ?token=. Setup asks the phone for it (/token/request; you tap Approve on the phone). A phone
  WITHOUT a token is open to the whole tailnet, so setup offers to set one the same way. The
  token is stored with Windows' per-user encryption (DPAPI) in %LOCALAPPDATA%\Husk\token.dpapi.
  HTTP calls run in-process (Invoke-WebRequest), so the token never appears in a command line.

  Requirement: Tailscale installed + logged into the same tailnet as the phone.
  Get the phone's Tailscale IP from the Husk app on the phone.

  Usage:
    One-time setup (on a new PC):   powershell -ExecutionPolicy Bypass -File husk-companion.ps1
    Shortcuts call it as:           ... -File <local copy> -Display 0   (or -Display 2 = DeX/TV)
    Run setup again after the phone's token has changed, or to pair a phone with a new IP.
#>
param(
  [int]$Display = -1,                       # -1 = install mode; 0/2 = launcher (shortcut)
  [string]$TsIp = '',                       # phone's Tailscale IP (prompted if empty)
  [int]$RelayPort = 15557,                  # AdbForward bridge port (outside adb's 5555-5585 emulator scan)
  [int]$AppPort = 8090
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'    # Invoke-WebRequest's progress bar is slow on PS 5.1
[Net.WebRequest]::DefaultWebProxy = $null   # the phone is on the tailnet; a system proxy must not intercept
$localDir = Join-Path $env:LOCALAPPDATA 'Husk'
$cfg      = Join-Path $localDir 'config.txt'     # remembers the Tailscale IP between runs
$tokFile  = Join-Path $localDir 'token.dpapi'    # the phone's access token, DPAPI-protected
$launcher = $Display -ge 0

# The shortcuts run with -WindowStyle Hidden, so a console prompt there would hang invisibly.
# Launcher errors therefore go to a message box; setup errors go to the console.
function Fail([string]$msg) {
  if ($launcher) { (New-Object -ComObject WScript.Shell).Popup($msg, 0, 'Husk', 0x30) | Out-Null }
  else { Write-Host "ERROR: $msg"; Read-Host 'Press Enter' | Out-Null }
  exit 1
}

function Get-TsIp {
  if ($TsIp) { return $TsIp }
  if (Test-Path $cfg) { return ([string](Get-Content $cfg -Raw)).Trim() }
  if ($launcher) { return '' }
  return (Read-Host "Phone's Tailscale IP (see the Husk app)").Trim()
}

function Get-SavedToken {
  if (-not (Test-Path $tokFile)) { return '' }
  try {
    $sec = (Get-Content $tokFile -Raw).Trim() | ConvertTo-SecureString
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
  } catch { return '' }   # unreadable (other user / other PC) = no saved token
}
function Save-Token([string]$t) {
  New-Item -ItemType Directory -Force -Path $localDir | Out-Null
  if (-not $t) { Remove-Item -LiteralPath $tokFile -Force -ErrorAction SilentlyContinue; return }
  ConvertTo-SecureString $t -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -Path $tokFile -Encoding ascii
}

# One HTTP GET against the app. Returns Code (0 = no answer) and Body. A non-2xx answer is a
# result, not an exception: 401 means "token needed/wrong", and the callers act on that.
function Invoke-Husk([string]$path, [hashtable]$query = @{}, [int]$timeout = 15) {
  $q = @($query.GetEnumerator() | Where-Object { $_.Value } |
         ForEach-Object { $_.Key + '=' + [uri]::EscapeDataString([string]$_.Value) }) -join '&'
  $u = "http://${ip}:${AppPort}$path" + $(if ($q) { "?$q" } else { '' })
  try {
    # -DisableKeepAlive: the app closes an idle connection after 20 s, while .NET keeps it pooled
    # for 100 s. A pause at a prompt or a winget install would otherwise reuse a dead socket.
    $r = Invoke-WebRequest -UseBasicParsing -DisableKeepAlive -Uri $u -TimeoutSec $timeout
    return [pscustomobject]@{ Code = [int]$r.StatusCode; Body = [string]$r.Content }
  } catch {
    $resp = $_.Exception.Response
    if ($resp) {
      $body = [string]$_.ErrorDetails.Message
      return [pscustomobject]@{ Code = [int]$resp.StatusCode; Body = $body }
    }
    return [pscustomobject]@{ Code = 0; Body = $_.Exception.Message }
  }
}

# Ask the phone for its token (/token/request). The phone shows a notification and the user taps
# Approve. If the phone has NO token, approving sets a new random one and hands it out.
# -AllowOld: the phone has no token, so a pre-1.4 Husk (no /token/request) is not an error here -
# setup carries on without a token, as it did before 1.4.
function Request-Token([switch]$AllowOld) {
  $client = ($env:COMPUTERNAME -replace '[^A-Za-z0-9 ._-]', '')
  if (-not $client) { $client = 'Husk-PC' }
  if ($client.Length -gt 32) { $client = $client.Substring(0, 32) }
  $r = Invoke-Husk '/token/request' @{ client = $client }
  if ($r.Code -eq 404 -and $AllowOld) {
    Write-Host "  this Husk is older than 1.4 and cannot set a token remotely - continuing without one"
    return ''
  }
  if ($r.Code -eq 429) { Fail 'Another token request is waiting on the phone. Approve or deny it there, then run setup again.' }
  if ($r.Code -eq 503) { Fail 'Notifications for Husk are off on the phone. Turn them on, then run setup again.' }
  # A pre-1.4 Husk has no /token/request: 404 without a token, 401 with an old adb-set token.
  if ($r.Code -eq 404 -or $r.Code -eq 401) { Fail 'The phone runs a Husk older than 1.4. Update Husk (F-Droid), then run setup again.' }
  if ($r.Code -ne 200) { Fail "/token/request answered $($r.Code): $($r.Body)" }
  $j = $null; try { $j = $r.Body | ConvertFrom-Json } catch { }
  if (-not $j -or -not $j.id) { Fail "/token/request gave an unreadable answer: $($r.Body)" }
  $id = [string]$j.id
  # Stop polling BEFORE the phone's own expiry (expires_in, 120 s): an Approve tapped in the last
  # seconds could otherwise set a token on the phone while this side reports "expired".
  $ttl = 120; if ($j.expires_in) { $ttl = [int]$j.expires_in }
  Write-Host "  >> On the phone: tap Approve on the notification '$client ...' (waiting up to $ttl seconds)"
  $deadline = (Get-Date).AddSeconds($ttl - 5)
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $s = Invoke-Husk '/token/status' @{ id = $id }
    if ($s.Code -ne 200) { continue }
    $st = $null; try { $st = $s.Body | ConvertFrom-Json } catch { continue }
    switch ([string]$st.status) {
      'approved' { return [string]$st.token }
      'denied'   { Fail 'The request was denied on the phone.' }
      'expired'  { Fail 'The request expired. If you tapped Approve at the last moment, a token may have been set: run setup again.' }
    }
  }
  Fail 'No answer from the phone in time. If you tapped Approve at the last moment, a token may have been set: run setup again.'
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
if (-not $ip) { Fail 'No Tailscale IP known. Run setup (husk-companion.ps1 with no arguments).' }
$dev = "${ip}:${RelayPort}"

# ---------------- launcher mode (called by shortcuts) ----------------
if ($launcher) {
  $scrcpy = Find-Scrcpy
  if (-not $scrcpy) { Fail 'scrcpy missing - run setup (husk-companion.ps1 with no arguments).' }
  $adb = Get-Adb $scrcpy
  if (-not (Connect-Device $adb $dev)) {
    # Wireless Debugging is off after a phone reboot; /wd lets the app turn it back on.
    $wd = Invoke-Husk '/wd' @{ token = (Get-SavedToken) } 60
    if ($wd.Code -eq 401) { Fail 'The phone''s access token has changed. Run setup again (husk-companion.ps1 with no arguments).' }
    if ($wd.Code -eq 0)   { Fail "Could not reach the Husk app at ${ip}:${AppPort}. Is Tailscale on, and the phone awake?" }
    if ($wd.Code -ne 200 -or -not (Connect-Device $adb $dev)) {
      Fail "Could not reach the phone ($dev). /wd answered $($wd.Code). If this PC was never paired, run setup again."
    }
  }
  $title = if ($Display -eq 2) { 'Husk-TV' } else { 'Husk' }
  & $scrcpy -s $dev --display-id=$Display --window-title=$title --no-audio
  if ($LASTEXITCODE -ne 0) { Fail "scrcpy closed with exit $LASTEXITCODE. Is DeX/TV on (display 2)?" }
  exit 0
}

# ---------------- install mode (one-time on a new PC) ----------------
Write-Host "== Husk companion setup =="

$h = Invoke-Husk '/healthz' @{} 8
if ($h.Code -ne 200 -or $h.Body.Trim() -ne 'ok') { Fail "cannot reach the app at http://${ip}:${AppPort} (Tailscale logged in? phone awake? right IP?)." }
Write-Host "  app reached (healthz=ok)"

# Access token. Probe a token-gated read-only endpoint WITHOUT a token:
#   200 -> the phone has NO token and is open to the whole tailnet: offer to set one
#   401 -> the phone has a token: use the saved one if it is accepted, else ask the phone
# The probe carries no token on purpose: a phone without a token accepts ANY token, so a probe
# with a stale saved token would answer 200 and hide that the phone is open.
$token = ''
$p = Invoke-Husk '/flags'
if ($p.Code -eq 200) {
  Write-Host ""
  Write-Host "  The phone has NO access token: anyone on your Tailscale network can control it."
  Write-Host "  Setting one means other PCs and apps must use it too (Husk Webcam: 'Hent fra telefonen')."
  $a = Read-Host "  Set an access token now by approving on the phone? [Y/n]"
  if ($a -notmatch '^(n|no|nej)$') { $token = Request-Token -AllowOld }
} elseif ($p.Code -eq 401) {
  $saved = Get-SavedToken
  if ($saved -and (Invoke-Husk '/flags' @{ token = $saved }).Code -eq 200) { $token = $saved }
  else {
    Write-Host "  the phone has an access token - asking the phone for it"
    $token = Request-Token
  }
} else {
  Fail "/flags answered $($p.Code): $($p.Body)"
}
if ($token) {
  $v = Invoke-Husk '/flags' @{ token = $token }
  if ($v.Code -ne 200) { Fail "the token from the phone was not accepted (/flags answered $($v.Code))." }
  Write-Host "  access token OK (stored encrypted for this Windows user)"
}
Save-Token $token

$scrcpy = Find-Scrcpy
if (-not $scrcpy) {
  Write-Host "  installing scrcpy via winget ..."
  winget install --id Genymobile.scrcpy -e --silent --accept-package-agreements --accept-source-agreements | Out-Null
  $scrcpy = Find-Scrcpy
}
if (-not $scrcpy) { Fail 'scrcpy install failed. Install manually: winget install Genymobile.scrcpy' }
$adb = Get-Adb $scrcpy
Write-Host "  scrcpy: $scrcpy"

# One-time Wireless-Debugging pairing (Android 12 WD = TLS). Skip if already connected.
if (-not (Connect-Device $adb $dev)) {
  Write-Host "  pairing PC with the phone's Wireless Debugging (fetching code from the app) ..."
  $pr = Invoke-Husk '/pair' @{ token = $token } 75
  if ($pr.Code -eq 401) { Fail '/pair refused the access token. Run setup again.' }
  $j = $null; try { $j = $pr.Body | ConvertFrom-Json } catch { }
  if ($pr.Code -ne 200 -or -not $j.addr -or -not $j.code) {
    Fail "/pair returned no code ($($pr.Code)). Is Accessibility on for Husk, and Developer options unlocked (button in the app)?"
  }
  Write-Host "  $((& $adb pair $j.addr $j.code) -join ' ')"
  if (-not (Connect-Device $adb $dev)) { Fail "connect after pairing failed ($dev)." }
}
Write-Host "  connected: $dev"

# Persist IP + copy self to a local path (no Mark-of-the-Web/SAC block from a synced folder) + shortcuts
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
Set-Content -Path $cfg -Value $ip -Encoding ascii
$localScript = Join-Path $localDir 'husk-companion.ps1'
if ($PSCommandPath -ne $localScript) { Copy-Item -LiteralPath $PSCommandPath -Destination $localScript -Force }
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
