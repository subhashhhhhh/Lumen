#!/bin/bash
# Open a photo from the Library and flick it closed, several times; report frame stats.
set -e
PKG=com.subhashh.lumen.debug
adb shell am force-stop $PKG
adb shell am start -n $PKG/com.subhashh.lumen.MainActivity >/dev/null
sleep 4
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for xy in "200 1500" "610 1500" "1000 1500" "200 1900" "610 1900" "1000 1900"; do
  adb shell input tap $xy; sleep 1.6
  adb shell input swipe 610 1100 640 1800 110; sleep 1.6
done
echo "== ${1:-run}"
adb shell dumpsys gfxinfo $PKG | grep -E 'Total frames rendered|Janky frames:|99th percentile|Slow UI thread'
