#!/bin/bash
# Fling the Library grid up and down and report frame stats. Usage: scroll_jank.sh [label]
# Expects the profile build installed and the phone unlocked.
set -e
PKG=com.subhashh.lumen.debug
adb shell am force-stop $PKG
adb shell am start -n $PKG/com.subhashh.lumen.MainActivity >/dev/null
sleep 4
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in 1 2 3 4 5 6; do
  adb shell input swipe 610 900 610 2100 70; sleep 1.3
  adb shell input swipe 610 2100 610 900 70; sleep 1.3
done
echo "== ${1:-run}"
adb shell dumpsys gfxinfo $PKG | grep -E 'Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile' 
