package cn.hui.gptpower;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class GPTPower extends XposedModule {
  private static final String TAG = "GPTPower", FL = "com.follow.clash";
  private static final String START = "com.follow.clash.intent.action.VPN_START_REQUESTED";
  private static final AtomicBoolean OWNED = new AtomicBoolean();
  private static final AtomicBoolean REPLAY_PENDING = new AtomicBoolean();
  private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

  @Override public void onPackageReady(PackageReadyParam p) {
    if (!"system".equals(p.getPackageName())) return;
    try {
      Class<?> stub = Class.forName("com.android.server.voiceinteraction.VoiceInteractionManagerService$VoiceInteractionManagerServiceStub", false, p.getClassLoader());
      for (Method method : stub.getDeclaredMethods()) {
        if (method.getName().equals("showSessionForActiveService")) {
          method.setAccessible(true);
          hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (BYPASS.get() || hasVpn()) return chain.proceed();
            if (!REPLAY_PENDING.compareAndSet(false, true)) return defaultValue(method.getReturnType());
            try {
              Invocation invocation = snapshot(chain.getThisObject(), method, chain.getArgs());
              new Thread(() -> startVpnAndReplay(invocation), "GPTPower-VPN").start();
              return defaultValue(method.getReturnType());
            } catch (Throwable e) {
              REPLAY_PENDING.set(false);
              log(Log.ERROR, TAG, "Deferral failed; allowing original assistant call", e);
              return chain.proceed();
            }
          });
        }
        if (method.getName().equals("hideCurrentSession")) hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> { Object result = chain.proceed(); stopOwnedVpn(); return result; });
      }
      log(Log.INFO, TAG, "VoiceInteraction hooks installed");
    } catch (Throwable e) { log(Log.ERROR, TAG, "Hook failed", e); }
  }

  private void startVpnAndReplay(Invocation invocation) {
    try {
      Context context = systemContext();
      if (context != null && !hasVpn()) {
        forceStop(context);
        SystemClock.sleep(300);
        context.sendBroadcast(new Intent(START).setComponent(new ComponentName(FL, FL + ".ServiceBroadcastReceiver")).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND));
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (!hasVpn() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
        OWNED.set(hasVpn());
      }
    } catch (Throwable e) {
      log(Log.ERROR, TAG, "VPN start failed; replaying assistant call", e);
    }
    try { replay(invocation); }
    finally { REPLAY_PENDING.set(false); }
  }

  private void replay(Invocation invocation) {
    BYPASS.set(true);
    try {
      invocation.method.invoke(invocation.target, invocation.args);
    } catch (Throwable reflectionFailure) {
      try {
        getInvoker(invocation.method).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(invocation.target, invocation.args);
      } catch (Throwable originFailure) {
        reflectionFailure.addSuppressed(originFailure);
        log(Log.ERROR, TAG, "Assistant replay failed open", reflectionFailure);
      }
    } finally {
      BYPASS.remove();
    }
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

  private static Invocation snapshot(Object target, Method method, List<Object> source) {
    Object[] args = source.toArray();
    for (int i = 0; i < args.length; i++) if (args[i] instanceof Bundle bundle) args[i] = new Bundle(bundle);
    return new Invocation(target, method, args);
  }

  static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    return 0D;
  }

  private record Invocation(Object target, Method method, Object[] args) {}
}
