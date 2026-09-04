#!/system/bin/sh
MODDIR=${0%/*}
MODDIR=${MODDIR%/*}
LOGDIR=/data/adb/gptpower/log
PKG=com.follow.clash
mkdir -p "$LOGDIR"

log_vpn() { echo "$(date '+%F %T') $*" >> "$LOGDIR/vpn.log"; }

if "$MODDIR/scripts/vpn-status.sh"; then
  log_vpn "VPN already ready"
  exit 0
fi

log_vpn "VPN start requested"
am force-stop "$PKG" >/dev/null 2>&1
sleep 0.2
pm unstop --user 0 "$PKG" >/dev/null 2>&1 || cmd package set-stopped-state --user 0 "$PKG" false >/dev/null 2>&1 || true
am broadcast --user 0 -f 0x10000020 -n "$PKG/.ServiceBroadcastReceiver" -a "$PKG.intent.action.VPN_START_REQUESTED" >> "$LOGDIR/vpn.log" 2>&1

COUNT=0
while [ "$COUNT" -lt 25 ]; do
  sleep 0.2
  if "$MODDIR/scripts/vpn-status.sh"; then
    log_vpn "VPN ready"
    exit 0
  fi
  COUNT=$((COUNT + 1))
done

log_vpn "VPN timeout"
exit 1
