#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Kanonisk Husk release-build = NOEJAGTIG samme vej som F-Droid: Gradle assembleRelease.
# Koeres i WSL (Ubuntu), IKKE i git-bash/PowerShell (de har ikke JDK/SDK-miljoeet).
#
# Tre-trins-virkelighed (se docs/BUILD.md):
#   1) Faa kilden fra G: ind i WSL.  2) gradlew assembleRelease.  3) (valgfri) signér.
# Trin 1 er det skroebelige: /mnt/g (Google Drive i WSL) fejler intermitterende med
# "No such device". Derfor: synk helst fra git-bash-siden via //wsl.localhost (stabil),
# og koer saa dette script med HUSK_NOSYNC=1. Scriptet kan ogsaa selv rsync'e fra /mnt/g
# naar det virker.
#
# ---- ANBEFALET (robust) -------------------------------------------------------
#   # A) kopiér kilde G: -> WSL fra Bash-toolet/git-bash (læser G: stabilt):
#   cp -r "/g/My Drive/10_PROJEKTER/P_app_husk/"{app,gradle,*.gradle,gradle.properties,gradlew} \
#         "//wsl.localhost/Ubuntu/home/$USER/android-build/husk-build/"
#   # B) byg i WSL uden gen-synk:
#   wsl.exe --cd '~' -- bash -lc 'HUSK_NOSYNC=1 bash ~/android-build/husk-build-run.sh'  # se note nedenfor
#
#   (Note: dette script ligger paa G:. Naar /mnt/g er nede kan WSL ikke laese det herfra -
#    kopiér det med ind i husk-build, eller koer trinene manuelt jf. docs/BUILD.md afsnit 4.)
# ---- BEKVEMT (naar /mnt/g virker) --------------------------------------------
#   wsl.exe --cd '~' -- bash -lc 'bash "/mnt/g/My Drive/10_PROJEKTER/P_app_husk/gradle-build.sh"'
# -------------------------------------------------------------------------------
#
# Valgfri env:
#   HUSK_ENV=~/android-build/env21.sh    # byggemiljoe (default; env.sh = JDK17-varianten)
#   HUSK_WORK=~/android-build/husk-build  # WSL-arbejdsbibliotek (gradle-cache genbruges)
#   HUSK_SRC="/mnt/g/My Drive/10_PROJEKTER/P_app_husk"  # kilde til auto-synk
#   HUSK_NOSYNC=1                         # spring synk over (kilden er allerede i HUSK_WORK)
#   HUSK_KEYSTORE=~/android-build/husk-signing/debug.keystore  # signér output (kanonisk
#                                          # noegle CN=Debug,O=KHFRB; ellers kun unsigned APK)
#   HUSK_KS_PASS=android  HUSK_KEY_PASS=android
set -euo pipefail

ENVF="${HUSK_ENV:-$HOME/android-build/env21.sh}"
[ -f "$ENVF" ] || { echo "FEJL: byggemiljoe '$ENVF' mangler - se docs/BUILD.md 'Byggemiljoe'." >&2; exit 1; }
# shellcheck disable=SC1090
source "$ENVF"

WORK="${HUSK_WORK:-$HOME/android-build/husk-build}"
SRC="${HUSK_SRC:-/mnt/g/My Drive/10_PROJEKTER/P_app_husk}"
mkdir -p "$WORK"

if [ "${HUSK_NOSYNC:-0}" != "1" ]; then
  if ! head -1 "$SRC/build.gradle" >/dev/null 2>&1; then
    cat >&2 <<EOF
FEJL: kan ikke laese kilden via '$SRC' (typisk /mnt/g "No such device").
Synk i stedet fra git-bash-siden, og koer saa med HUSK_NOSYNC=1:

  cp -r "/g/My Drive/10_PROJEKTER/P_app_husk/"{app,gradle,*.gradle,gradle.properties,gradlew} \\
        "//wsl.localhost/Ubuntu/home/$USER/android-build/husk-build/"
  wsl.exe --cd '~' -- bash -lc 'HUSK_NOSYNC=1 HUSK_WORK=~/android-build/husk-build \\
        bash <kopi-af-dette-script-i-WSL>'

Se docs/BUILD.md afsnit 4 for den fulde manuelle vej.
EOF
    exit 2
  fi
  echo "== synk $SRC -> $WORK =="
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --exclude '.git' --exclude 'build/' --exclude '.gradle/' \
      --exclude 'obj/' --exclude 'bin/' --exclude '*.apk' "$SRC"/ "$WORK"/
  else
    cp -r "$SRC/app" "$SRC/gradle" "$SRC"/*.gradle "$SRC/gradle.properties" "$SRC/gradlew" "$WORK"/
  fi
else
  echo "== HUSK_NOSYNC=1: bygger eksisterende kilde i $WORK =="
fi

echo "sdk.dir=${ANDROID_HOME}" > "$WORK/local.properties"
cd "$WORK"
chmod +x gradlew
VC="$(grep -oE 'versionCode +[0-9]+' app/build.gradle | grep -oE '[0-9]+' | head -1)"
VN="$(grep -oE 'versionName +"[^"]+"' app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/' | head -1)"
echo "== gradlew assembleRelease  (v$VN / versionCode $VC) =="
echo "   foerste build henter Gradle 8.7 + AGP 8.5.2 (minutter); herefter cache'et (~40s)."
./gradlew --no-daemon assembleRelease

UNSIGNED="$WORK/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "FEJL: forventet artefakt mangler: $UNSIGNED" >&2; exit 1; }
echo
echo "OK unsigned APK (= praecis hvad F-Droid bygger): $UNSIGNED"

KS="${HUSK_KEYSTORE:-}"
if [ -n "$KS" ] && [ -f "$KS" ]; then
  AS="$ANDROID_HOME/build-tools/34.0.0/apksigner"
  OUT="$WORK/husk-v${VN//./}.apk"
  echo "== signér -> $OUT =="
  "$AS" sign --ks "$KS" --ks-pass "pass:${HUSK_KS_PASS:-android}" \
    --key-pass "pass:${HUSK_KEY_PASS:-android}" --out "$OUT" "$UNSIGNED"
  "$AS" verify --print-certs "$OUT" | grep -i "certificate DN" || true
  echo "SIGNED: $OUT"
  echo "ADVARSEL: GitHub-release + repo'ets husk-latest.apk SKAL signeres med SAMME noegle"
  echo "          (CN=Debug, O=KHFRB, C=DK) ellers afvises in-app-opdateringer. Se docs/BUILD.md."
else
  echo "Ikke signeret (HUSK_KEYSTORE ikke sat). F-Droid behoever ingen signering;"
  echo "GitHub/in-app-update SKAL bruge den kanoniske keystore - se docs/BUILD.md afsnit 5."
fi
