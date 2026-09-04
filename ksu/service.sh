#!/system/bin/sh
MODDIR=${0%/*}
LOGDIR=/data/adb/gptpower/log
mkdir -p "$LOGDIR"

POWER_GUARD=1
[ -f "$MODDIR/config.conf" ] && . "$MODDIR/config.conf"
LAST_REQUEST=$(settings get global gptpower_vpn_request 2>/dev/null)
TICK=0
settings put global gptpower_vpn_owned 0
settings put global gptpower_helper_heartbeat "$(date +%s)"

while true; do
  if [ -f "$MODDIR/disable" ]; then
    settings put global gptpower_helper_heartbeat 0
    exit 0
  fi

  REQUEST=$(settings get global gptpower_vpn_request 2>/dev/null)
  if [ -n "$REQUEST" ] && [ "$REQUEST" != "null" ] && [ "$REQUEST" != "$LAST_REQUEST" ]; then
    LAST_REQUEST="$REQUEST"
    TOKEN=${REQUEST#*:}
    case "$REQUEST" in
      start:*)
        if "$MODDIR/scripts/vpn-start.sh"; then
          settings put global gptpower_vpn_status "ready:$TOKEN"
        else
          settings put global gptpower_vpn_status "timeout:$TOKEN"
        fi
        ;;
      stop:*)
        "$MODDIR/scripts/vpn-stop.sh"
        settings put global gptpower_vpn_status "stopped:$TOKEN"
        ;;
    esac
  fi

  if [ "$TICK" -eq 0 ]; then
    settings put global gptpower_helper_heartbeat "$(date +%s)"
    [ "$POWER_GUARD" = "1" ] && "$MODDIR/scripts/power-fix.sh"
  fi
  TICK=$(( (TICK + 1) % 10 ))
  sleep 1
done
