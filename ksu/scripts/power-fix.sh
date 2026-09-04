#!/system/bin/sh
MODDIR=${0%/*}
MODDIR=${MODDIR%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

CURRENT=$(settings get global power_button_long_press 2>/dev/null)
if [ "$CURRENT" != "5" ]; then
  settings put global power_button_long_press 5
  echo "$(date '+%F %T') power_button_long_press: $CURRENT -> 5" >> "$LOGDIR/ksu.log"
fi
