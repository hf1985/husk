#!/data/data/com.termux/files/usr/bin/bash
# Idempotent on-phone provisioner for Husk (hurtig dev/office-deploy). Bygger (hvis noedvendigt),
# installerer, giver CAMERA, batteri-whitelister, saetter stay-awake, og (valgfrit) aktiverer
# a11y-servicen + DeX-reconnect. Den KANONISKE release er Gradle/F-Droid; dette er dev/office-vejen.
#   bash setup.sh                          # build + install + CAMERA + start
#   ENABLE_A11Y=1 bash setup.sh            # + aktiver a11y (motor + WD-recovery + adb-bro)
#   DEX_RECONNECT=1 ENABLE_A11Y=1 bash setup.sh   # + slaa DeX-reconnect til (kun DeX-enheder)
#   RIG_TOKEN=hemmelig bash setup.sh       # delt token paa stream/control-endpoints
set -uo pipefail
cd "$(dirname "$0")"

PKG="co.xplat.husk"
A11Y_SVC="$PKG/$PKG.RigAccessibilityService"
ENABLE_A11Y="${ENABLE_A11Y:-0}"
DEX_RECONNECT="${DEX_RECONNECT:-0}"
RIG_TOKEN="${RIG_TOKEN:-}"

say() { printf '\n== %s ==\n' "$*"; }

if [ ! -f bin/husk.apk ]; then say "bygger (bin/husk.apk mangler)"; bash build.sh; else say "apk findes - 'bash build.sh' for at genbygge"; fi

say "install -r"
adb install -r bin/husk.apk

say "CAMERA-permission"; adb shell pm grant "$PKG" android.permission.CAMERA || true
say "batteri-whitelist"; adb shell dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1 || true
say "stay-awake while charging"; adb shell settings put global stay_on_while_plugged_in 7 || true

if [ "$ENABLE_A11Y" = "1" ]; then
  say "aktiver a11y-service additivt"
  cur="$(adb shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')"
  case "$cur" in
    *"$A11Y_SVC"*) echo "allerede aktiveret" ;;
    null|"")       adb shell settings put secure enabled_accessibility_services "$A11Y_SVC" ;;
    *)             adb shell settings put secure enabled_accessibility_services "$cur:$A11Y_SVC" ;;
  esac
  adb shell settings put secure accessibility_enabled 1
fi

# ADVARSEL (incident 2026-06-19): start KUN headless (--ez finish true). Launch ALDRIG MainActivity
# til FORGRUND paa en koerende DeX-rig - Samsungs "app running on another display"-dialog tvinger en
# app-genstart der kan dræbe a11y/8127 + kameraet. Headless (finish=true) finisher foer noget vindue
# vises og er sikker. Daglig drift starter motoren via boot (BootReceiver -> CameraService direkte).
say "start app headless (FGS via MainActivity --ez finish true; saetter evt. DeX-reconnect + token)"
EXTRAS="--ez finish true"
[ "$DEX_RECONNECT" = "1" ] && EXTRAS="$EXTRAS --ez dexreconnect true"
[ -n "$RIG_TOKEN" ] && EXTRAS="$EXTRAS --es token $RIG_TOKEN"
adb shell am start -n "$PKG/$PKG.MainActivity" $EXTRAS || true

say "status"; sleep 3
if command -v curl >/dev/null 2>&1; then
  echo "/healthz -> $(curl -s --max-time 6 http://127.0.0.1:8090/healthz)"
  echo "/snapshot -> HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 8 http://127.0.0.1:8090/snapshot${RIG_TOKEN:+?token=$RIG_TOKEN})"
fi
echo "FAERDIG. Stream http://<tailscale>:8090/stream | scrcpy: adb connect <tailscale>:5557 (efter /pair)"
