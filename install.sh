#!/bin/sh
# Build and install Luma Home, then set it as the default HOME app.
# Usage: ./install.sh <TV_IP_or_ADB_serial>
set -eu
cd "$(dirname "$0")"
TARGET=${1:?Usage: ./install.sh TV_IP_or_ADB_serial}
command -v adb >/dev/null 2>&1 || { echo 'Install Android platform-tools (adb) first.' >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*) TV=$TARGET:5555 ;;
  *) TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
if [ "$(adb -s "$TV" get-state 2>/dev/null)" != device ]; then
  echo 'TV not connected or debugging not authorized. Check its IP, Wi-Fi, and ADB prompt.' >&2
  exit 1
fi

if [ ! -f out/luma-home.apk ]; then ./build.sh; fi
if ! adb -s "$TV" install -r out/luma-home.apk; then
  echo 'Install failed. If Android reports UPDATE_INCOMPATIBLE, build with the original private signing key via KEYSTORE=... KEYSTORE_PASS=... ./build.sh.' >&2
  exit 1
fi

APP=com.ansub.tvhome.stylish
ACTIVITY=$APP/com.ansub.tvhome.MainActivity
adb -s "$TV" shell am start -W -n "$ACTIVITY"
adb -s "$TV" shell cmd package set-home-activity "$ACTIVITY"
adb -s "$TV" shell input keyevent 3
DEFAULT_HOME=$(adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)
if [ "$DEFAULT_HOME" != "$ACTIVITY" ]; then
  echo "Installed, but default HOME is $DEFAULT_HOME. Choose Luma Home as Always on the TV." >&2
  exit 1
fi
echo "Luma Home is installed and the default HOME app on $TV."
