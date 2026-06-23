#!/data/data/com.termux/files/usr/bin/bash
# Hurtig on-phone test-build af Husk-APK'en i Termux: aapt2 (compile+link -> R.java) -> ecj -> dx ->
# add dex -> apksigner. Samme idé som DexRPC, men tilpasset Gradle-layoutet (app/src/main/...) og med
# R.java-generering FOER Java-kompileringen (koden bruger nu string-resources). Den KANONISKE
# release-build er Gradle (./gradlew assembleRelease, det F-Droid koerer); dette er kun en hurtig dev-vej.
#
# Kraever android.jar (API 31+) + en debug.keystore. Genbruger DexRPC's byggeinput hvis de mangler.
# Termux-pakker: pkg install -y openjdk-21 ecj dx aapt2 apksigner
set -e
cd "$(dirname "$0")"

DEXRPC_DIR="${DEXRPC_DIR:-$HOME/dexrpc}"
AJ="$PWD/android.jar"
KS="$PWD/debug.keystore"
RES="app/src/main/res"
MANIFEST="app/src/main/AndroidManifest.xml"

# Version fra build.gradle (manifestet baerer den IKKE; Gradle injicerer normalt -> on-phone-build
# skal selv goere det, ellers bliver APK'en versionCode 0 og 'adb install -r' afvises som downgrade).
VC="$(grep -oE 'versionCode +[0-9]+' app/build.gradle | grep -oE '[0-9]+' | head -1)"
VN="$(grep -oE 'versionName +"[^"]+"' app/build.gradle | sed -E 's/.*"([^"]+)".*/\1/' | head -1)"
[ -n "$VC" ] || { echo "MANGLER versionCode i app/build.gradle"; exit 1; }
echo "version: $VN ($VC)"

[ -f "$AJ" ] || { [ -f "$DEXRPC_DIR/android.jar" ] && cp "$DEXRPC_DIR/android.jar" "$AJ" || { echo "MANGLER android.jar"; exit 1; }; }
[ -f "$KS" ] || { [ -f "$DEXRPC_DIR/debug.keystore" ] && cp "$DEXRPC_DIR/debug.keystore" "$KS" || {
  echo "genererer NY debug.keystore"; keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias ad -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug,O=xplat,C=DK" >/dev/null 2>&1; }; }

rm -rf obj bin gen; mkdir -p obj bin gen

echo "== aapt2 compile res =="
aapt2 compile --dir "$RES" -o bin/res.zip

echo "== aapt2 link (+ R.java) =="
aapt2 link -o bin/app-unaligned.apk -I "$AJ" \
  --manifest "$MANIFEST" \
  --java gen \
  --min-sdk-version 30 --target-sdk-version 33 \
  --version-code "$VC" --version-name "$VN" \
  bin/res.zip

echo "== compile java (ecj default-compliance; ingen lambdaer -> dx-kompatibel) =="
ecj -bootclasspath "$AJ" -d obj $(find app/src/main/java gen -name '*.java')

echo "== dex =="
dx --dex --output=bin/classes.dex obj

echo "== add dex =="
( cd bin && aapt add app-unaligned.apk classes.dex >/dev/null )

echo "== sign =="
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out bin/husk.apk bin/app-unaligned.apk

echo "BUILD-OK: $PWD/bin/husk.apk"
