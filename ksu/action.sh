#!/system/bin/sh
MODDIR=${0%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

"$MODDIR/scripts/diagnostics.sh"
CHOICE=1
echo
echo "Volume Up: next item"
echo "Volume Down: run selected item"
while true; do
  case "$CHOICE" in
    1) LABEL="Repair Power Assistant = 5" ;;
    2) LABEL="Start FlClash VPN silently" ;;
    3) LABEL="Stop FlClash VPN silently" ;;
    4) LABEL="Test VPN status" ;;
    5) LABEL="Reinstall embedded LSPosed APK" ;;
    6) LABEL="Save full diagnostics log" ;;
  esac
  echo "Selected $CHOICE: $LABEL"
  EVENT=$(getevent -qlc 1 2>/dev/null)
  case "$EVENT" in
    *KEY_VOLUMEUP*DOWN*) CHOICE=$((CHOICE % 6 + 1)) ;;
    *KEY_VOLUMEDOWN*DOWN*) break ;;
  esac
done

case "$CHOICE" in
  1) "$MODDIR/scripts/power-fix.sh" ;;
  2) "$MODDIR/scripts/vpn-start.sh" ;;
  3) "$MODDIR/scripts/vpn-stop.sh" ;;
  4) "$MODDIR/scripts/vpn-status.sh" && echo "VPN READY" || echo "VPN NOT READY" ;;
  5) pm install -r "$MODDIR/apk/GPTPower-LSPosed.apk" ;;
  6) "$MODDIR/scripts/diagnostics.sh" | tee "$LOGDIR/diagnostics-$(date +%Y%m%d-%H%M%S).log" ;;
  *) echo "No action selected." ;;
esac
