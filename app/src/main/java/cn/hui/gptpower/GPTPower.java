package cn.hui.gptpower;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

public final class GPTPower extends XposedModule {
  private static final String TAG = "GPTPower", FL = "com.follow.clash";
  private static final String START = "com.follow.clash.intent.action.VPN_START_REQUESTED";
  private static final AtomicBoolean OWNED = new AtomicBoolean();

  @Override public void onPackageReady(PackageReadyParam p) {
    if (!"system".equals(p.getPackageName())) return;
    try {
      Class<?> stub = Class.forName("com.android.server.voiceinteraction.VoiceInteractionManagerService$VoiceInteractionManagerServiceStub", false, p.getClassLoader());
      for (Method method : stub.getDeclaredMethods()) {
        if (method.getName().equals("showSessionForActiveService")) hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> { startVpnAndWait(); return chain.proceed(); });
        if (method.getName().equals("hideCurrentSession")) hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> { Object result = chain.proceed(); stopOwnedVpn(); return result; });
      }
      log(Log.INFO, TAG, "VoiceInteraction hooks installed");
    } catch (Throwable e) { log(Log.ERROR, TAG, "Hook failed", e); }
  }

  private void startVpnAndWait() {
    if (hasVpn()) return;
    Context context = systemContext();
    if (context == null) return;
    forceStop(context);
    SystemClock.sleep(300);
    context.sendBroadcast(new Intent(START).setComponent(new ComponentName(FL, FL + ".ServiceBroadcastReceiver")).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND));
    long deadline = SystemClock.elapsedRealtime() + 5000;
    while (!hasVpn() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
    OWNED.set(hasVpn());
  }

  private void stopOwnedVpn() {
    if (!OWNED.compareAndSet(true, false)) return;
    Context context = systemContext();
    if (context != null) forceStop(context);
  }

  private void forceStop(Context context) {
    try { ActivityManager.class.getDeclaredMethod("forceStopPackage", String.class).invoke(context.getSystemService(ActivityManager.class), FL); }
    catch (Throwable e) { log(Log.ERROR, TAG, "force-stop failed", e); }
  }

  private static Context systemContext() {
    try { Class<?> c = Class.forName("android.app.ActivityThread"); Object thread = c.getDeclaredMethod("currentActivityThread").invoke(null); return (Context) c.getDeclaredMethod("getSystemContext").invoke(thread); }
    catch (Throwable ignored) { return null; }
  }

  private static boolean hasVpn() {
    Context c = systemContext(); if (c == null) return false; ConnectivityManager cm = c.getSystemService(ConnectivityManager.class); if (cm == null) return false;
    for (Network n : cm.getAllNetworks()) { NetworkCapabilities caps = cm.getNetworkCapabilities(n); if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true; }
    return false;
  }
}
