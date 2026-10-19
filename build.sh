#!/bin/sh
# Build Luma Home with Android SDK platform/build-tools 34 and Java 17.
set -eu
cd "$(dirname "$0")"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64; do
    if [ -d "$candidate" ]; then JAVA_HOME=$candidate; break; fi
  done
fi
if [ -n "${JAVA_HOME:-}" ]; then PATH="$JAVA_HOME/bin:$PATH"; export PATH; fi
command -v javac >/dev/null 2>&1 || { echo 'Java 17 (javac) is required.' >&2; exit 1; }
command -v keytool >/dev/null 2>&1 || { echo 'Java keytool is required.' >&2; exit 1; }
command -v zip >/dev/null 2>&1 || { echo 'zip is required.' >&2; exit 1; }

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ]; then
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/homebrew/share/android-commandlinetools; do
    if [ -d "$candidate" ]; then SDK=$candidate; break; fi
  done
fi
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
if [ ! -f "$JAR" ] || [ ! -x "$BT/aapt2" ] || [ ! -x "$BT/d8" ] || [ ! -x "$BT/apksigner" ]; then
  echo 'Set ANDROID_HOME and install Android SDK platforms;android-34 and build-tools;34.0.0.' >&2
  exit 1
fi

rm -rf out
mkdir -p out/classes out/dex .local
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 21 --target-sdk-version 34
javac -nowarn -Xlint:-options -source 1.8 -target 1.8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
  -d out/classes src/com/ansub/tvhome/MainActivity.java
"$BT/d8" --min-api 21 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk
(cd out/dex && zip -q ../unsigned.apk classes.dex)
zip -q -r out/unsigned.apk assets
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

# Keep the private signing key outside Git. To update an existing installation,
# point KEYSTORE at the key that signed that installation.
KS=${KEYSTORE:-.local/key.jks}
PASS=${KEYSTORE_PASS:-tvhome123}
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias tvhome \
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Luma Home' >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias tvhome --ks-pass "pass:$PASS" \
  --out out/luma-home.apk out/aligned.apk
"$BT/apksigner" verify out/luma-home.apk
echo 'Built out/luma-home.apk'
