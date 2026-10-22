#!/bin/bash
# エミュレーター上でアプリを起動し、スクリーンショット・画面構造・ログを集める（GitHub Actions 用）
set -x
PKG=com.peykich.amazonaireader
mkdir -p out
adb install -r app.apk
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 20
adb exec-out screencap -p > out/1-launch.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml out/1-launch.xml
adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "テスト https://www.amazon.co.jp/dp/B0GJCWGSWR" -n $PKG/.MainActivity
sleep 40
adb exec-out screencap -p > out/2-share.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml out/2-share.xml
adb logcat -d > out/logcat.txt
echo "===== app log ====="
PID=$(adb shell pidof $PKG)
echo "pid=$PID"
grep -E "AndroidRuntime|FATAL|$PKG|chromium|cr_|WebView|Compose| $PID " out/logcat.txt | tail -300
echo "===== ui dump ====="
cat out/1-launch.xml | head -c 4000
true
