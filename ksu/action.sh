#!/system/bin/sh
MODDIR=${0%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

"$MODDIR/scripts/diagnostics.sh"
echo
echo "1. Repair Power Assistant = 5"
echo "2. Start FlClash VPN silently"
echo "3. Stop FlClash VPN silently"
echo "4. Test VPN status"
echo "5. Reinstall embedded LSPosed APK"
echo "6. Save full diagnostics log"
printf "Select [1-6]: "
read -r CHOICE

case "$CHOICE" in
  1) "$MODDIR/scripts/power-fix.sh" ;;
  2) "$MODDIR/scripts/vpn-start.sh" ;;
  3) "$MODDIR/scripts/vpn-stop.sh" ;;
  4) "$MODDIR/scripts/vpn-status.sh" && echo "VPN READY" || echo "VPN NOT READY" ;;
  5) pm install -r "$MODDIR/apk/GPTPower-LSPosed.apk" ;;
  6) "$MODDIR/scripts/diagnostics.sh" | tee "$LOGDIR/diagnostics-$(date +%Y%m%d-%H%M%S).log" ;;
  *) echo "No action selected." ;;
esac
