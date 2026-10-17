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

# Full offline OCR acceptance test: a real ML Kit recognition run in the emulator.
adb logcat -c
adb shell am force-stop ai.katarina.visionlab || true
adb shell am start -n ai.katarina.visionlab/.MainActivity --ez katarina_demo_ocr true
for attempt in $(seq 1 24); do
    adb logcat -d -s KatarinaOCR:I '*:S' > artifacts/demo-ocr-log.txt
    if grep -q "OCR_DEMO_PASS" artifacts/demo-ocr-log.txt; then
        echo "OCR_DEMO_PASS: Google ML Kit recognized synthetic TEST exactly."
        break
    fi
    if grep -q "OCR_DEMO_FAILED\|OCR_DEMO_MISMATCH" artifacts/demo-ocr-log.txt; then
        cat artifacts/demo-ocr-log.txt
        echo "FAIL: offline OCR demo did not recognize TEST exactly"
        exit 1
    fi
    sleep 2
done
grep -q "OCR_DEMO_PASS" artifacts/demo-ocr-log.txt
adb exec-out screencap -p > artifacts/demo-ocr-screenshot.png
adb shell uiautomator dump /sdcard/window-demo.xml
adb pull /sdcard/window-demo.xml artifacts/window-demo.xml
# Recognition result is validated via the app's OCR_DEMO_PASS log. The accessibility
# tree only includes visible nodes, and offscreen results depend on scroll position.
# Preserve the full XML/screenshot for visual QA, but do not require offscreen text.
test -s artifacts/window-demo.xml
test -s artifacts/demo-ocr-screenshot.png
echo "SMOKE_PASS: App launched; offline ML Kit image OCR recognized TEST; screenshots and logcat saved."
