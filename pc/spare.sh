#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Husk-flaade: fjernstyrings-harness for ENHVER Husk-enhed over Tailscale (8090).
# Bash-port af spare.ps1 (Git Bash / WSL / Linux) - plain bash + curl, ingen jq/python.
# Bygget efter live-verifikation 2026-07-12 af at BEGGE spares (Sony 702SO/A9, Samsung A10e/A11)
# kan fjernstyres fuldt ud som en normal bruger - stik imod den tidligere "umuligt"-konklusion.
#
# KERNE-INDSIGT: en idle spare SOVER skaermen. Med slukket skaerm returnerer a11y kun navbar'en,
# gestus svarer "ERR cancelled" og home/back er no-ops (praecis det symptom der blev fejltolket som
# "a11y-motoren er doed"). Derfor VAEKKER dette script skaermen foer enhver handling. Sekvensen
# vaek -> se (/screen.jpg) -> handl (tap/swipe/launch) giver fuld menneske-aekvivalent kontrol.
#
# Brug:
#   ./spare.sh a11 shot                         # vaek + gem skaermbillede
#   ./spare.sh a11 launch android.settings.SETTINGS
#   ./spare.sh a11 tap 360 960                  # koordinat-tap (laes koordinater fra 'shot')
#   ./spare.sh a11 swipe 360 1000 360 500 300   # scroll
#   ./spare.sh a9  home | back | recents
#   ./spare.sh a11 find Battery                 # a11y-node (bedst paa A11; flaky paa A9)
#   ./spare.sh a11 text "hej verden"            # skriv i fokuseret felt
#   ./spare.sh a11 update                       # trigger self-update (se Play Protect-note nedenfor)
#   ./spare.sh a11 control                      # print browser-viewer-URL (live skaerm + klik)
#   ./spare.sh a11 health                        # /flags + /info-resume
#   ./spare.sh a11 rpc "dump 0"                  # raa 8127-RPC
#
# Aliaser (Tailscale-IP): a9/sony/702so=100.100.101.101, a11/samsung/a10e=100.100.101.102,
#                          note10/rig=100.100.103.102 (KRAEVER token -> HUSK_TOKEN).

set -uo pipefail

if [[ $# -lt 2 ]]; then
    echo "brug: ./spare.sh <target> <verb> [args...]" >&2
    exit 1
fi

TARGET="$1"
VERB="$2"
shift 2

TOKEN="${HUSK_TOKEN:-}"

# alias -> Tailscale-IP (case-insensitivt); ukendt target bruges verbatim som host/IP
case "${TARGET,,}" in
    a9|sony|702so)    ip='100.100.101.101' ;;
    a11|samsung|a10e) ip='100.100.101.102' ;;
    note10|rig)       ip='100.100.103.102' ;;
    *)                ip="$TARGET" ;;
esac
base="http://${ip}:8090"

q() {
    # append token som query-param hvis sat (spares er tokenloese -> tom)
    local path="$1" sep
    if [[ -n "$TOKEN" ]]; then
        if [[ "$path" == *\?* ]]; then sep='&'; else sep='?'; fi
        printf '%s%s%stoken=%s' "$base" "$path" "$sep" "$TOKEN"
    else
        printf '%s%s' "$base" "$path"
    fi
}

enc() {
    # URL-encode (som [uri]::EscapeDataString): alt undtagen A-Za-z0-9 - . _ ~ procent-kodes
    local LC_ALL=C
    local s="$1" out='' c i
    for (( i = 0; i < ${#s}; i++ )); do
        c="${s:i:1}"
        case "$c" in
            [a-zA-Z0-9.~_-]) out+="$c" ;;
            *) printf -v c '%%%02X' "'$c"; out+="$c" ;;
        esac
    done
    printf '%s' "$out"
}

# GET der returnerer body som tekst (kort timeout); fejlet curl aborterer IKKE scriptet
get_text() {
    local out rc
    out=$(curl -s -m 12 "$(q "$1")"); rc=$?
    if [[ $rc -ne 0 ]]; then
        printf 'ERR curl exit %s\n' "$rc"
        return 0
    fi
    # trim whitespace i begge ender
    out="${out#"${out%%[![:space:]]*}"}"
    out="${out%"${out##*[![:space:]]}"}"
    printf '%s\n' "$out"
}

wake() { get_text '/wake' > /dev/null; }

case "${VERB,,}" in

    health)
        echo "== $TARGET ($ip) =="
        echo "healthz: $(get_text '/healthz')"
        echo "flags:   $(get_text '/flags')"
        echo "info:    $(get_text '/info')"
        ;;

    wake)
        get_text '/wake'
        ;;

    shot)
        wake; sleep 0.7
        outdir="${TMPDIR:-/tmp}/husk-shots"
        mkdir -p "$outdir"
        stamp=$(date -u +%Y%m%d-%H%M%S)
        if [[ $# -ge 1 ]]; then file="$1"; else file="$outdir/$TARGET-$stamp.jpg"; fi
        if curl -s -m 12 -o "$file" "$(q '/screen.jpg')"; then
            sz=$(wc -c < "$file" | tr -d '[:space:]')
            echo "skaermbillede: $file ($sz B)"
            if [[ "$sz" -lt 2000 ]]; then
                echo "  ADVARSEL: lille fil -> skaermen sov maaske; koer 'shot' igen."
            fi
        else
            echo "ERR curl exit $?"
        fi
        ;;

    launch)
        if [[ $# -lt 1 ]]; then echo "brug: launch <action> [data] [pkg]  (fx launch android.settings.SETTINGS)" >&2; exit 1; fi
        wake
        qs="/launch?d=0&action=$(enc "$1")"
        if [[ $# -ge 2 && -n "$2" ]]; then qs="$qs&data=$(enc "$2")"; fi
        if [[ $# -ge 3 && -n "$3" ]]; then qs="$qs&pkg=$(enc "$3")"; fi
        get_text "$qs"
        ;;

    tap)
        if [[ $# -lt 2 ]]; then echo "brug: tap <x> <y>" >&2; exit 1; fi
        wake
        get_text "/tap?x=$1&y=$2&d=0"
        ;;

    swipe)
        if [[ $# -lt 4 ]]; then echo "brug: swipe <x1> <y1> <x2> <y2> [ms]" >&2; exit 1; fi
        wake
        ms="${5:-250}"
        get_text "/swipe?x1=$1&y1=$2&x2=$3&y2=$4&d=0&ms=$ms"
        ;;

    home|back|recents|notifications)
        wake
        get_text "/key?k=${VERB,,}"
        ;;

    text)
        if [[ $# -lt 1 ]]; then echo "brug: text <streng>" >&2; exit 1; fi
        get_text "/text?t=$(enc "$*")"
        ;;

    find)
        if [[ $# -lt 1 ]]; then echo "brug: find <regex>" >&2; exit 1; fi
        get_text "/find?d=0&match=$(enc "$*")"
        ;;

    dump)
        get_text '/dump?d=0'
        ;;

    exists)
        if [[ $# -lt 1 ]]; then echo "brug: exists <regex>" >&2; exit 1; fi
        get_text "/exists?d=0&match=$(enc "$*")"
        ;;

    rpc)
        if [[ $# -lt 1 ]]; then echo "brug: rpc \"<8127-kommando>\"  (fx rpc \"dump 0\")" >&2; exit 1; fi
        get_text "/rpc?cmd=$(enc "$*")"
        ;;

    update)
        wake
        get_text '/update?force=1'
        echo ""
        echo "Self-update trigget. Foelg /flags -> lastUpdate. NAAR OS'ets install-dialog kommer:"
        echo "  * Google Play Protect 'App scan recommended' (fersk sideload): koer 'shot',"
        echo "    tap 'More details', 'shot' igen, tap saa 'Install without scanning'."
        echo "  * Standard-dialog 'Do you want to install...': tap 'Install'."
        echo "  (a11y-'find' er upaalidelig paa disse system-dialoger -> laes koordinater fra 'shot'.)"
        echo "  Installen commit'er -> 8090 falder kort -> J4 rejser den igen (self-heal, ingen reboot)."
        ;;

    control)
        url=$(q '/control')
        echo "aabn i browser: $url"
        ;;

    *)
        echo "ukendt verb: $VERB"
        echo "verbs: health wake shot launch tap swipe home back recents notifications text find dump exists rpc update control"
        ;;
esac
