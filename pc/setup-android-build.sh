#!/usr/bin/env bash
# Engangs-opsaetning af Husks byggemiljoe i WSL (docs/BUILD.md afsnit 3), sudo-frit.
# Samme JDK som tidligere releases (Temurin 21.0.11+10), fordi F-Droid sammenligner vores APK
# med sin egen build og en anden javac kan give andre bytes.
# Koeres i WSL: bash /mnt/c/<sti>/setup-android-build.sh   (idempotent; springer det faerdige over)
# ASCII-only (projektkonvention for .sh).
set -euo pipefail

AB="$HOME/android-build"
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz"
CMD_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
mkdir -p "$AB/dl" "$AB/sdk/cmdline-tools"

if [ ! -x "$AB/jdk-21.0.11+10/bin/java" ]; then
  curl -fsSL -o "$AB/dl/jdk21.tar.gz" "$JDK_URL"
  tar -xzf "$AB/dl/jdk21.tar.gz" -C "$AB"
fi
if [ ! -x "$AB/sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fsSL -o "$AB/dl/cmdtools.zip" "$CMD_URL"
  rm -rf "$AB/sdk/cmdline-tools/tmp"
  mkdir -p "$AB/sdk/cmdline-tools/tmp"
  python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$AB/dl/cmdtools.zip" "$AB/sdk/cmdline-tools/tmp"
  mv "$AB/sdk/cmdline-tools/tmp/cmdline-tools" "$AB/sdk/cmdline-tools/latest"
  rmdir "$AB/sdk/cmdline-tools/tmp"
  chmod +x "$AB/sdk/cmdline-tools/latest/bin/"*
fi

printf '%s\n' \
  "export JAVA_HOME=\"$AB/jdk-21.0.11+10\"" \
  "export ANDROID_HOME=\"$AB/sdk\"" \
  'export ANDROID_SDK_ROOT="$ANDROID_HOME"' \
  'export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"' \
  > "$AB/env21.sh"
. "$AB/env21.sh"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null
java -version 2>&1 | head -1
ls "$ANDROID_HOME/build-tools"
echo "OK: $AB"
