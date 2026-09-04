#!/system/bin/sh
MODDIR=${0%/*}
MODDIR=${MODDIR%/*}

echo "GPTPower Version: $(sed -n 's/^version=//p' "$MODDIR/module.prop" | head -n 1)"
echo "Device: $(getprop ro.product.model) ($(getprop ro.product.device))"
echo "Android: $(getprop ro.build.version.release)"
echo "SDK: $(getprop ro.build.version.sdk)"
echo "HyperOS: $(getprop ro.mi.os.version.incremental) $(getprop ro.build.version.incremental)"
echo "power_button_long_press: $(settings get global power_button_long_press)"
echo "Assistant Role: $(cmd role get-role-holders android.app.role.ASSISTANT 2>&1)"
echo "assistant: $(settings get secure assistant)"
echo "voice_interaction_service: $(settings get secure voice_interaction_service)"
pm path com.follow.clash >/dev/null 2>&1 && echo "FlClash installed: yes" || echo "FlClash installed: no"
pm path com.openai.chatgpt >/dev/null 2>&1 && echo "ChatGPT installed: yes" || echo "ChatGPT installed: no"
pidof com.follow.clash >/dev/null 2>&1 && echo "FlClash running: yes" || echo "FlClash running: no"
"$MODDIR/scripts/vpn-status.sh" && echo "VPN: ready" || echo "VPN: not ready"
pm path cn.hui.gptpower >/dev/null 2>&1 && echo "GPTPower LSPosed APK installed: yes" || echo "GPTPower LSPosed APK installed: no"
echo "VPN owned by GPTPower: $(settings get global gptpower_vpn_owned)"
