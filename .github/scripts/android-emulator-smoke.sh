#!/usr/bin/env bash
set -euo pipefail
mkdir -p artifacts

# Test only local Android app; no Cloudflare or 2Captcha credentials.
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop ai.katarina.visionlab || true
adb logcat -c
adb shell am start -n ai.katarina.visionlab/.MainActivity
sleep 12

adb shell dumpsys activity activities > artifacts/activity.txt
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml artifacts/window.xml
adb exec-out screencap -p > artifacts/screenshot.png
adb logcat -d -v time > artifacts/logcat.txt

test -s artifacts/screenshot.png
grep -q "KATARINA / VISION LAB" artifacts/window.xml
if grep -E "FATAL EXCEPTION|Process: ai.katarina.visionlab.*(has died|crashed)" artifacts/logcat.txt; then
    echo "FAIL: crash signatures found in emulator logcat"
    exit 1
fi

# Capture the lower settings/diagnostic area for remote visual review.
adb shell input swipe 500 1450 500 450 500 || true
sleep 2
adb exec-out screencap -p > artifacts/screenshot-diagnostics.png
adb shell uiautomator dump /sdcard/window-diagnostics.xml || true
adb pull /sdcard/window-diagnostics.xml artifacts/window-diagnostics.xml || true

echo "SMOKE_PASS: App launched; UI confirmed; screenshots and logcat saved."
