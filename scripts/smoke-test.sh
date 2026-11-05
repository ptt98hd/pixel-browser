#!/usr/bin/env bash
# Install an APK on the connected device/emulator, launch it, open a page and fail on crash.
set -euo pipefail
APK="$1"
PKG=app.pixel.browser
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 20
adb shell pidof "$PKG" >/dev/null || { echo "App not running after launch"; adb logcat -d | tail -200; exit 1; }
if adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $PKG" ; then
  echo "Crash detected"; adb logcat -d | grep -A40 "FATAL EXCEPTION" || true; exit 1
fi
adb exec-out screencap -p > smoke-screenshot.png || true
echo "Smoke test passed"
