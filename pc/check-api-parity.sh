#!/usr/bin/env bash
# GATE: en Husk-release er IKKE faerdig foer /husk/api-dokumentationen er ajour med appen.
#
# xplat.co/husk/api + /husk/openapi.json genereres begge fra HUSK_API-kataloget i
# P_xplat/hosting/app.py. Denne gate fanger AUTOMATISK:
#   - endpoints i appen (ControlServer.java) der IKKE er i kataloget (udokumenteret), og
#   - endpoints i kataloget der IKKE er i appen (stale), og
#   - versionCode-drift mellem app/build.gradle og HUSK_VERSION_CODE.
# Den kan IKKE se aendrede params/response/adgangsmodel - det minder den om til slut.
#
# Kaldes i release-flowet (se CLAUDE.md RELEASE-PLIGT + docs/BUILD.md). Exit != 0 = gate fejlede.
# Brug:  bash pc/check-api-parity.sh   (fra P_app_husk-roden; stier kan overrides som $1/$2)
set -uo pipefail

HUSK="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
XPLAT="${2:-$HUSK/../P_xplat}"
CS="$HUSK/app/src/main/java/co/xplat/husk/ControlServer.java"
APP="$XPLAT/hosting/app.py"

[ -f "$CS" ]  || { echo "GATE FEJL: finder ikke ControlServer.java ($CS)"; exit 2; }
[ -f "$APP" ] || { echo "GATE FEJL: finder ikke P_xplat/hosting/app.py ($APP) - saet sti som \$2"; exit 2; }

# Appens faktiske endpoints: path.equals("/x") i ControlServer. Undtag "/" (HTML-viewer-rod, ikke et
# dokumenteret API-endpoint).
app_eps=$(grep -oE 'path\.equals\("/[a-zA-Z0-9._]*"\)' "$CS" \
          | grep -oE '"/[a-zA-Z0-9._]*"' | tr -d '"' | grep -vx '/' | sort -u)
# Katalogets endpoints: "p": "/x" i HUSK_API.
cat_eps=$(grep -oE '"p": "/[a-zA-Z0-9._]*"' "$APP" \
          | grep -oE '/[a-zA-Z0-9._]*' | sort -u)

missing=$(comm -23 <(printf '%s\n' "$app_eps") <(printf '%s\n' "$cat_eps"))   # i app, ikke i katalog
stale=$(comm -13 <(printf '%s\n' "$app_eps") <(printf '%s\n' "$cat_eps"))     # i katalog, ikke i app

gv=$(grep -oE 'versionCode +[0-9]+' "$HUSK/app/build.gradle" | grep -oE '[0-9]+' | head -1)
xv=$(grep -oE 'HUSK_VERSION_CODE *= *[0-9]+' "$APP" | grep -oE '[0-9]+' | head -1)

rc=0
if [ -n "$missing" ]; then
  echo "GATE FEJL: endpoints i appen mangler i HUSK_API-kataloget (=/husk/api er stale):"
  printf '%s\n' "$missing" | sed 's/^/  + /'
  rc=1
fi
if [ -n "$stale" ]; then
  echo "GATE FEJL: endpoints i HUSK_API findes IKKE (laengere) i appen:"
  printf '%s\n' "$stale" | sed 's/^/  - /'
  rc=1
fi
if [ "${gv:-x}" != "${xv:-y}" ]; then
  echo "GATE FEJL: versionCode-drift: app/build.gradle=$gv vs P_xplat HUSK_VERSION_CODE=$xv"
  rc=1
fi
if [ "$rc" = 0 ]; then
  echo "GATE OK: HUSK_API daekker alle $(printf '%s\n' "$app_eps" | grep -c .) app-endpoints; versionCode=$gv matcher."
fi

echo
echo "MINDER (kan ikke auto-tjekkes): afspejler HUSK_API + beskrivelsen ogsaa aendrede PARAMS,"
echo "RESPONSE-format og ADGANGSMODEL (auth/CSRF/Host/headers) i denne release? Og er xplat.co DEPLOYET"
echo "(begge sider genereres derfra)? Opgaven er foerst faerdig naar /husk/api live er ajour."
exit $rc
