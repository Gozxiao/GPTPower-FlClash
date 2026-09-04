#!/system/bin/sh
MODDIR=${0%/*}
MODDIR=${MODDIR%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

echo "$(date '+%F %T') Stopping FlClash" >> "$LOGDIR/vpn.log"
am force-stop com.follow.clash >/dev/null 2>&1
settings put global gptpower_vpn_owned 0
