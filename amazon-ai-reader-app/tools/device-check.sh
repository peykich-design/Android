#!/bin/bash
# エミュレーター上でアプリを起動し、スクリーンショット・画面構造・ログを集める（GitHub Actions 用）
set -x
PKG=com.peykich.amazonaireader
# 画面上の文字をタップする（uiautomator の画面構造から位置を探す）
tap_text() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null && adb pull /sdcard/u.xml /tmp/u.xml >/dev/null
  local b
  b=$(grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" /tmp/u.xml | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')
  set -- $b
  [ -n "$4" ] && adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
mkdir -p out
# 起動直後はGoogleのシステムアプリが再起動して、巻き添えでアプリが終了させられることがあるので落ち着くまで待つ
sleep 120
adb install -r "${1:-app.apk}"
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 20
adb exec-out screencap -p > out/1-launch.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml out/1-launch.xml
tap_text "比較"; sleep 3
adb exec-out screencap -p > out/4-compare.png
tap_text "設定"; sleep 3
adb exec-out screencap -p > out/5-settings.png
tap_text "変換"; sleep 2
adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'テスト https://www.amazon.co.jp/dp/B0GJCWGSWR'" -n $PKG/.MainActivity
sleep 45
adb exec-out screencap -p > out/2-share.png
adb shell input swipe 500 1500 500 300
sleep 2
adb exec-out screencap -p > out/3-scrolled.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml out/2-share.xml
adb logcat -d > out/logcat.txt
echo "===== app log ====="
PID=$(adb shell pidof $PKG)
echo "pid=$PID"
grep -E "AndroidRuntime|FATAL|$PKG|chromium|cr_|WebView|Compose| $PID " out/logcat.txt | tail -300
echo "===== ui dump ====="
cat out/1-launch.xml | head -c 4000
true
