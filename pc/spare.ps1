# SPDX-License-Identifier: GPL-3.0-or-later
# Husk-flaade: fjernstyrings-harness for ENHVER Husk-enhed over Tailscale (8090).
# Bygget efter live-verifikation 2026-07-12 af at BEGGE spares (Sony 702SO/A9, Samsung A10e/A11)
# kan fjernstyres fuldt ud som en normal bruger - stik imod den tidligere "umuligt"-konklusion.
#
# KERNE-INDSIGT: en idle spare SOVER skaermen. Med slukket skaerm returnerer a11y kun navbar'en,
# gestus svarer "ERR cancelled" og home/back er no-ops (praecis det symptom der blev fejltolket som
# "a11y-motoren er doed"). Derfor VAEKKER dette script skaermen foer enhver handling. Sekvensen
# vaek -> se (/screen.jpg) -> handl (tap/swipe/launch) giver fuld menneske-aekvivalent kontrol.
#
# Brug:
#   .\spare.ps1 a11 shot                         # vaek + gem skaermbillede + aabn det
#   .\spare.ps1 a11 launch android.settings.SETTINGS
#   .\spare.ps1 a11 tap 360 960                  # koordinat-tap (laes koordinater fra 'shot')
#   .\spare.ps1 a11 swipe 360 1000 360 500 300   # scroll
#   .\spare.ps1 a9  home | back | recents
#   .\spare.ps1 a11 find Battery                 # a11y-node (bedst paa A11; flaky paa A9)
#   .\spare.ps1 a11 text "hej verden"            # skriv i fokuseret felt
#   .\spare.ps1 a11 update                       # trigger self-update (se Play Protect-note nedenfor)
#   .\spare.ps1 a11 control                      # aabn browser-viewer (live skaerm + klik)
#   .\spare.ps1 a11 health                        # /flags + /info-resume
#   .\spare.ps1 a11 rpc "dump 0"                  # raa 8127-RPC
#
# Aliaser (Tailscale-IP): a9/sony/702so=100.100.101.101, a11/samsung/a10e=100.100.101.102,
#                          note10/rig=100.100.103.102 (KRAEVER token -> $env:HUSK_TOKEN el. -Token).

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)] [string] $Target,
    [Parameter(Mandatory = $true, Position = 1)] [string] $Verb,
    [Parameter(ValueFromRemainingArguments = $true)] [string[]] $Rest,
    [string] $Token = $env:HUSK_TOKEN,
    [string] $OutDir = (Join-Path $env:TEMP 'husk-shots'),
    [switch] $NoOpen
)

$ErrorActionPreference = 'Stop'

$Aliases = @{
    'a9' = '100.100.101.101'; 'sony' = '100.100.101.101'; '702so' = '100.100.101.101'
    'a11' = '100.100.101.102'; 'samsung' = '100.100.101.102'; 'a10e' = '100.100.101.102'
    'note10' = '100.100.103.102'; 'rig' = '100.100.103.102'
}

$ip = if ($Aliases.ContainsKey($Target.ToLower())) { $Aliases[$Target.ToLower()] } else { $Target }
$base = "http://${ip}:8090"

function Q([string]$path) {
    # append token som query-param hvis sat (spares er tokenloese -> tom)
    if ($Token) {
        $sep = if ($path.Contains('?')) { '&' } else { '?' }
        return "$base$path$sep`token=$Token"
    }
    return "$base$path"
}

function Enc([string]$s) { [uri]::EscapeDataString($s) }

# GET der returnerer body som tekst (curl-agtig, kort timeout)
function Get-Text([string]$path) {
    try { return (Invoke-WebRequest -Uri (Q $path) -TimeoutSec 12 -UseBasicParsing).Content.Trim() }
    catch { return "ERR $($_.Exception.Message)" }
}

function Wake { [void](Get-Text '/wake') }

switch ($Verb.ToLower()) {

    'health' {
        Write-Host "== $Target ($ip) ==" -ForegroundColor Cyan
        Write-Host "healthz: $(Get-Text '/healthz')"
        Write-Host "flags:   $(Get-Text '/flags')"
        Write-Host "info:    $(Get-Text '/info')"
    }

    'wake' { Write-Host (Get-Text '/wake') }

    'shot' {
        Wake; Start-Sleep -Milliseconds 700
        if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $file = if ($Rest.Count -ge 1) { $Rest[0] } else { Join-Path $OutDir "$Target-$stamp.jpg" }
        try {
            Invoke-WebRequest -Uri (Q '/screen.jpg') -TimeoutSec 12 -OutFile $file -UseBasicParsing
            $sz = (Get-Item $file).Length
            Write-Host "skaermbillede: $file ($sz B)"
            if ($sz -lt 2000) { Write-Host "  ADVARSEL: lille fil -> skaermen sov maaske; koer 'shot' igen." -ForegroundColor Yellow }
            elseif (-not $NoOpen) { Invoke-Item $file }
        } catch { Write-Host "ERR $($_.Exception.Message)" -ForegroundColor Red }
    }

    'launch' {
        if ($Rest.Count -lt 1) { throw "brug: launch <action> [data] [pkg]  (fx launch android.settings.SETTINGS)" }
        Wake
        $action = Enc $Rest[0]
        $q = "/launch?d=0&action=$action"
        if ($Rest.Count -ge 2 -and $Rest[1]) { $q += "&data=$(Enc $Rest[1])" }
        if ($Rest.Count -ge 3 -and $Rest[2]) { $q += "&pkg=$(Enc $Rest[2])" }
        Write-Host (Get-Text $q)
    }

    'tap' {
        if ($Rest.Count -lt 2) { throw "brug: tap <x> <y>" }
        Wake
        Write-Host (Get-Text "/tap?x=$($Rest[0])&y=$($Rest[1])&d=0")
    }

    'swipe' {
        if ($Rest.Count -lt 4) { throw "brug: swipe <x1> <y1> <x2> <y2> [ms]" }
        Wake
        $ms = if ($Rest.Count -ge 5) { $Rest[4] } else { 250 }
        Write-Host (Get-Text "/swipe?x1=$($Rest[0])&y1=$($Rest[1])&x2=$($Rest[2])&y2=$($Rest[3])&d=0&ms=$ms")
    }

    { $_ -in 'home', 'back', 'recents', 'notifications' } {
        Wake
        Write-Host (Get-Text "/key?k=$($Verb.ToLower())")
    }

    'text' {
        if ($Rest.Count -lt 1) { throw "brug: text <streng>" }
        Write-Host (Get-Text "/text?t=$(Enc ($Rest -join ' '))")
    }

    'find'    { if ($Rest.Count -lt 1) { throw "brug: find <regex>" };   Write-Host (Get-Text "/find?d=0&match=$(Enc ($Rest -join ' '))") }
    'dump'    { Write-Host (Get-Text '/dump?d=0') }
    'exists'  { if ($Rest.Count -lt 1) { throw "brug: exists <regex>" }; Write-Host (Get-Text "/exists?d=0&match=$(Enc ($Rest -join ' '))") }

    'rpc' {
        if ($Rest.Count -lt 1) { throw "brug: rpc `"<8127-kommando>`"  (fx rpc `"dump 0`")" }
        Write-Host (Get-Text "/rpc?cmd=$(Enc ($Rest -join ' '))")
    }

    'update' {
        Wake
        Write-Host (Get-Text '/update?force=1')
        Write-Host ""
        Write-Host "Self-update trigget. Foelg /flags -> lastUpdate. NAAR OS'ets install-dialog kommer:" -ForegroundColor Cyan
        Write-Host "  * Google Play Protect 'App scan recommended' (fersk sideload): koer 'shot',"
        Write-Host "    tap 'More details', 'shot' igen, tap saa 'Install without scanning'."
        Write-Host "  * Standard-dialog 'Do you want to install...': tap 'Install'."
        Write-Host "  (a11y-'find' er upaalidelig paa disse system-dialoger -> laes koordinater fra 'shot'.)"
        Write-Host "  Installen commit'er -> 8090 falder kort -> J4 rejser den igen (self-heal, ingen reboot)."
    }

    'control' {
        $url = Q '/control'
        Write-Host "aabner $url"
        Start-Process $url
    }

    default {
        Write-Host "ukendt verb: $Verb" -ForegroundColor Red
        Write-Host "verbs: health wake shot launch tap swipe home back recents notifications text find dump exists rpc update control"
    }
}
