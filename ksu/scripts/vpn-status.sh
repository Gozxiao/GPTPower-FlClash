#!/system/bin/sh
MODDIR=${0%/*}
MODDIR=${MODDIR%/*}

dumpsys connectivity 2>/dev/null | grep -Eq 'TRANSPORT_VPN|Transports:[^]]*VPN|NetworkAgentInfo.*VPN'
