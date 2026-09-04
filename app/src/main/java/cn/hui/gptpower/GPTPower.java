package cn.hui.gptpower;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GPTPower extends XposedModule {
  private static final String TAG = "GPTPower";
  private static final String CHATGPT = "com.openai.chatgpt";
  private static final String ASSISTANT = CHATGPT + "/com.openai.feature.assistant.impl.AssistantVoiceInteractionService";
  private static final String REQUEST = "gptpower_vpn_request";
  private static final String HEARTBEAT = "gptpower_helper_heartbeat";
  private static final String OWNED_SETTING = "gptpower_vpn_owned";
  private static final int POWER_ASSIST_INVOCATION = 6;
  private static final AtomicBoolean OWNED = new AtomicBoolean();
  private static final AtomicBoolean POWER_PENDING = new AtomicBoolean();
  private static final AtomicBoolean APP_START_PENDING = new AtomicBoolean();
  private static final AtomicBoolean ASSISTANT_ACTIVE = new AtomicBoolean();
  private static final AtomicBoolean POWER_DISABLED = new AtomicBoolean();
  private static final AtomicBoolean STOP_CHECK_PENDING = new AtomicBoolean();
  private static final AtomicInteger START_FAILURES = new AtomicInteger();
  private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

  @Override public void onModuleLoaded(ModuleLoadedParam param) {
    log(Log.INFO, TAG, "Module loaded");
  }

  @Override public void onPackageReady(PackageReadyParam param) {
    log(Log.INFO, TAG, "Package ready: " + param.getPackageName());
    if (!"system".equals(param.getPackageName())) return;
    OWNED.set("1".equals(getGlobal(OWNED_SETTING)));
    hookPowerAssist(param.getClassLoader());
    hookSessionHide(param.getClassLoader());
    hookChatGptForeground(param.getClassLoader());
  }

  private void hookPowerAssist(ClassLoader loader) {
    try {
      Class<?> policy = Class.forName("com.android.server.policy.PhoneWindowManager", false, loader);
      int installed = 0;
      for (Method method : policy.getDeclaredMethods()) {
        if (!method.getName().contains("launchAssistAction")) continue;
        method.setAccessible(true);
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
          if (BYPASS.get() || !isPowerInvocation(method, chain.getArgs())) return chain.proceed();
          log(Log.INFO, TAG, "Power long press detected");
          if (powerMode() != 5 || !isChatGptAssistant()) return chain.proceed();
          if (hasVpn()) {
            log(Log.INFO, TAG, "VPN already ready");
            return chain.proceed();
          }
          if (POWER_DISABLED.get() || !helperAlive()) {
            log(Log.WARN, TAG, "Fail-open reason: KSU helper unavailable or interception disabled");
            return chain.proceed();
          }
          if (!POWER_PENDING.compareAndSet(false, true)) return defaultValue(method.getReturnType());
          try {
            Invocation invocation = snapshot(chain.getThisObject(), method, chain.getArgs());
            new Thread(() -> startVpnAndReplay(invocation), "GPTPower-Power").start();
            return defaultValue(method.getReturnType());
          } catch (Throwable error) {
            POWER_PENDING.set(false);
            log(Log.ERROR, TAG, "Fail-open reason: unable to defer power assist", error);
            return chain.proceed();
          }
        });
        log(Log.INFO, TAG, "Hooked power assist: " + method.toGenericString());
        installed++;
      }
      log(installed == 0 ? Log.WARN : Log.INFO, TAG, "PhoneWindowManager assist hooks: " + installed);
    } catch (Throwable error) {
      log(Log.ERROR, TAG, "Fail-open reason: PhoneWindowManager hook unavailable", error);
    }
  }

  private void hookSessionHide(ClassLoader loader) {
    try {
      Class<?> stub = Class.forName("com.android.server.voiceinteraction.VoiceInteractionManagerService$VoiceInteractionManagerServiceStub", false, loader);
      for (Method method : stub.getDeclaredMethods()) {
        if (!method.getName().equals("hideCurrentSession")) continue;
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
          Object result = chain.proceed();
          ASSISTANT_ACTIVE.set(false);
          log(Log.INFO, TAG, "Session hidden");
          scheduleStopCheck();
          return result;
        });
      }
    } catch (Throwable error) {
      log(Log.ERROR, TAG, "Fail-open reason: session hide hook unavailable", error);
    }
  }

  private void hookChatGptForeground(ClassLoader loader) {
    try {
      Class<?> record = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
      for (Method method : record.getDeclaredMethods()) {
        if (!method.getName().equals("setState") || method.getParameterCount() == 0) continue;
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
          Object result = chain.proceed();
          try {
            if (!CHATGPT.equals(packageName(chain.getThisObject()))) return result;
            String state = String.valueOf(chain.getArg(0));
            if (state.contains("RESUMED")) {
              log(Log.INFO, TAG, "ChatGPT foreground");
              ensureVpnForApp();
            } else if (state.contains("PAUSED") || state.contains("STOPPED") || state.contains("DESTROYED")) {
              scheduleStopCheck();
            }
          } catch (Throwable error) {
            log(Log.WARN, TAG, "Fail-open reason: ChatGPT state inspection failed", error);
          }
          return result;
        });
      }
    } catch (Throwable error) {
      log(Log.ERROR, TAG, "Fail-open reason: ActivityRecord hook unavailable", error);
    }
  }

  private void startVpnAndReplay(Invocation invocation) {
    boolean ready = startVpnAndWait();
    if (ready) ASSISTANT_ACTIVE.set(true);
    try {
      log(Log.INFO, TAG, "Assistant replay");
      if (!replay(invocation)) {
        ASSISTANT_ACTIVE.set(false);
        scheduleStopCheck();
      }
    } finally {
      POWER_PENDING.set(false);
    }
  }

  private void ensureVpnForApp() {
    if (hasVpn() || !helperAlive() || !APP_START_PENDING.compareAndSet(false, true)) return;
    new Thread(() -> {
      try { startVpnAndWait(); }
      finally { APP_START_PENDING.set(false); }
    }, "GPTPower-App").start();
  }

  private boolean startVpnAndWait() {
    if (hasVpn()) return true;
    try {
      log(Log.INFO, TAG, "VPN start requested");
      requestRoot("start");
      long deadline = SystemClock.elapsedRealtime() + 5000;
      while (SystemClock.elapsedRealtime() < deadline) {
        if (hasVpn()) {
          OWNED.set(true);
          putGlobal(OWNED_SETTING, "1");
          START_FAILURES.set(0);
          log(Log.INFO, TAG, "VPN ready");
          return true;
        }
        SystemClock.sleep(100);
      }
      int failures = START_FAILURES.incrementAndGet();
      log(Log.WARN, TAG, "VPN timeout");
      if (failures >= 3) {
        POWER_DISABLED.set(true);
        log(Log.WARN, TAG, "Fail-open reason: three VPN failures this boot");
      }
    } catch (Throwable error) {
      log(Log.ERROR, TAG, "Fail-open reason: VPN request failed", error);
    }
    return false;
  }

  private void scheduleStopCheck() {
    if (!OWNED.get() || !STOP_CHECK_PENDING.compareAndSet(false, true)) return;
    new Thread(() -> {
      try {
        SystemClock.sleep(1000);
        if (!ASSISTANT_ACTIVE.get() && !isChatGptForeground() && OWNED.compareAndSet(true, false)) {
          log(Log.INFO, TAG, "Stopping FlClash");
          putGlobal(OWNED_SETTING, "0");
          requestRoot("stop");
        }
      } catch (Throwable error) {
        log(Log.ERROR, TAG, "Fail-open reason: stop check failed", error);
      } finally {
        STOP_CHECK_PENDING.set(false);
      }
    }, "GPTPower-Stop").start();
  }

  private boolean replay(Invocation invocation) {
    BYPASS.set(true);
    try {
      invocation.method.invoke(invocation.target, invocation.args);
      return true;
    } catch (Throwable reflectionFailure) {
      try {
        getInvoker(invocation.method).setType(XposedInterface.Invoker.Type.ORIGIN).invoke(invocation.target, invocation.args);
        return true;
      } catch (Throwable originFailure) {
        reflectionFailure.addSuppressed(originFailure);
        log(Log.ERROR, TAG, "Fail-open reason: assistant replay failed", reflectionFailure);
        return false;
      }
    } finally {
      BYPASS.remove();
    }
  }

  private static boolean isPowerInvocation(Method method, List<Object> args) {
    Class<?>[] types = method.getParameterTypes();
    for (int i = types.length - 1; i >= 0; i--) {
      if (types[i] == int.class || types[i] == Integer.class) {
        return i < args.size() && args.get(i) instanceof Number number && number.intValue() == POWER_ASSIST_INVOCATION;
      }
    }
    return false;
  }

  private static boolean isChatGptAssistant() {
    try {
      Context context = systemContext();
      if (context == null) return false;
      String assistant = Settings.Secure.getString(context.getContentResolver(), "assistant");
      String voice = Settings.Secure.getString(context.getContentResolver(), "voice_interaction_service");
      Log.i(TAG, "Current assistant: " + assistant);
      return ASSISTANT.equals(assistant) || ASSISTANT.equals(voice);
    } catch (Throwable ignored) { return false; }
  }

  private static int powerMode() {
    try {
      Context context = systemContext();
      return context == null ? -1 : Settings.Global.getInt(context.getContentResolver(), "power_button_long_press", -1);
    } catch (Throwable ignored) { return -1; }
  }

  private static boolean helperAlive() {
    try {
      String value = getGlobal(HEARTBEAT);
      return value != null && Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(value)) <= 30;
    } catch (Throwable ignored) { return false; }
  }

  private static void requestRoot(String action) {
    if (!putGlobal(REQUEST, action + ":" + SystemClock.elapsedRealtimeNanos())) throw new IllegalStateException("KSU request rejected");
  }

  private static String getGlobal(String key) {
    Context context = systemContext();
    return context == null ? null : Settings.Global.getString(context.getContentResolver(), key);
  }

  private static boolean putGlobal(String key, String value) {
    Context context = systemContext();
    return context != null && Settings.Global.putString(context.getContentResolver(), key, value);
  }

  private static boolean isChatGptForeground() {
    try {
      Context context = systemContext();
      ActivityManager manager = context == null ? null : context.getSystemService(ActivityManager.class);
      if (manager == null) return true;
      List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
      if (tasks.isEmpty()) return false;
      ComponentName top = tasks.get(0).topActivity;
      return top != null && CHATGPT.equals(top.getPackageName());
    } catch (Throwable ignored) { return true; }
  }

  private static String packageName(Object record) {
    Object value = readField(record, "packageName");
    if (value instanceof String name) return name;
    value = readField(record, "mActivityComponent");
    return value instanceof ComponentName component ? component.getPackageName() : null;
  }

  private static Object readField(Object target, String name) {
    if (target == null) return null;
    for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
      } catch (NoSuchFieldException ignored) {
      } catch (Throwable ignored) { return null; }
    }
    return null;
  }

  private static Context systemContext() {
    try {
      Class<?> activityThread = Class.forName("android.app.ActivityThread");
      Object thread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null);
      return (Context) activityThread.getDeclaredMethod("getSystemContext").invoke(thread);
    } catch (Throwable ignored) { return null; }
  }

  private static boolean hasVpn() {
    try {
      Context context = systemContext();
      ConnectivityManager manager = context == null ? null : context.getSystemService(ConnectivityManager.class);
      if (manager == null) return true;
      for (Network network : manager.getAllNetworks()) {
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
      }
    } catch (Throwable ignored) { return true; }
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
