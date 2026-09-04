#!/system/bin/sh
MODDIR=${0%/*}

pkill -f "$MODDIR/service.sh" 2>/dev/null
settings put global gptpower_helper_heartbeat 0
settings delete global gptpower_vpn_request
settings delete global gptpower_vpn_status
settings delete global gptpower_vpn_owned
settings put global power_button_long_press 1
