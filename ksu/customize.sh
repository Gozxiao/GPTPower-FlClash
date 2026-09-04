#!/system/bin/sh
MODDIR=${0%/*}
[ -n "$MODPATH" ] && MODDIR="$MODPATH"

ui_print "- Installing GPTPower for KernelSU"
ui_print "- Embedded LSPosed APK: apk/GPTPower-LSPosed.apk"
ui_print "- After reboot, enable GPTPower in LSPosed"

set_perm_recursive "$MODDIR" 0 0 0755 0644
set_perm "$MODDIR/customize.sh" 0 0 0755
set_perm "$MODDIR/service.sh" 0 0 0755
set_perm "$MODDIR/boot-completed.sh" 0 0 0755
set_perm "$MODDIR/action.sh" 0 0 0755
set_perm "$MODDIR/uninstall.sh" 0 0 0755
set_perm_recursive "$MODDIR/scripts" 0 0 0755 0755
