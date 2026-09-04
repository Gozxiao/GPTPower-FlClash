#!/system/bin/sh
MODDIR=${0%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

install_apk() {
  APK="$MODDIR/apk/GPTPower-LSPosed.apk"
  [ -f "$APK" ] || return 1
  EMBEDDED=$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)
  INSTALLED=$(dumpsys package cn.hui.gptpower 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n 1)
  if [ -z "$INSTALLED" ] || [ "$INSTALLED" -lt "$EMBEDDED" ]; then
    pm install -r "$APK" >> "$LOGDIR/boot.log" 2>&1
  fi
}

install_apk
"$MODDIR/scripts/power-fix.sh"
sleep 3
"$MODDIR/scripts/power-fix.sh"
sleep 7
"$MODDIR/scripts/power-fix.sh"
