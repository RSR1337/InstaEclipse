package ps.reso.instaeclipse.mods.misc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class StaleStateCrashGuardHook {

    private static final String[] MAIN_ACTIVITIES = {
            "com.instagram.mainactivity.InstagramMainActivity",
            "com.instagram.mainactivity.LauncherActivity"
    };

    private static final String PREFS = "instaeclipse_crash_guard";
    private static final String KEY_PENDING = "pending_clean_restart";
    private static final String CACHE_KEY_PREFIX = "CrashGuard_onCreate_";

    private static final String[] FRAGMENT_BUNDLE_KEYS = {
            "android:fragments",
            "android:support:fragments",
            "androidx.fragment.app.FragmentManagerState",
            "android:view_state"
    };

    private static volatile boolean versionCheckConsumed;

    public static void flagPendingCleanRestart(Context context) {
        if (context == null) return;
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_PENDING, true)
                    .apply();
        } catch (Throwable ignored) {}
    }

    public static boolean looksLikeStaleFragmentState(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.US);
                if (lower.contains("unable to instantiate fragment")
                        || lower.contains("fragment class not found")
                        || lower.contains("error inflating class")
                        || (cur instanceof ClassNotFoundException && lower.contains("fragment"))) {
                    return true;
                }
            }
            String cn = cur.getClass().getName();
            if (cn.contains("Fragment$InstantiationException")
                    || cn.contains("Fragment.InstantiationException")
                    || cn.endsWith("Fragment$InstantiationException")) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    private final XC_MethodHook hook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0 || param.args[0] == null) return;
            if (!(param.args[0] instanceof Bundle)) return;
            Object self = param.thisObject;
            if (!(self instanceof Context) || !isMainShellActivity(self)) return;

            Context activity = (Context) self;
            SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            boolean pending = prefs.getBoolean(KEY_PENDING, false);
            boolean versionChanged = !versionCheckConsumed && !DexKitCache.isCacheValid();
            versionCheckConsumed = true;
            if (!pending && !versionChanged) return;

            Bundle original = (Bundle) param.args[0];
            if (pending) {
                param.args[0] = null;
                prefs.edit().putBoolean(KEY_PENDING, false).apply();
                ModuleLog.line("(InstaEclipse | CrashGuard): Dropped full savedInstanceState after a fragment-restore crash");
                return;
            }

            Bundle cleaned = stripFragmentState(original);
            param.args[0] = cleaned;
            ModuleLog.line("(InstaEclipse | CrashGuard): Stripped fragment restore state after Instagram version change");
        }
    };

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        int hooked = 0;
        for (String activity : MAIN_ACTIVITIES) {
            Method method = resolveOnCreate(bridge, classLoader, activity);
            if (method == null) continue;
            try {
                XposedBridge.hookMethod(method, hook);
                hooked++;
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CrashGuard): Failed to hook " + activity + ": " + t.getMessage());
            }
        }
        if (hooked == 0) {
            try {
                XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, hook);
                ModuleLog.line("(InstaEclipse | CrashGuard): Fallback Activity.onCreate hook installed");
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CrashGuard): onCreate method not found");
            }
        }
    }

    private static boolean isMainShellActivity(Object activity) {
        Class<?> cls = activity.getClass();
        while (cls != null && cls != Object.class) {
            String name = cls.getName();
            for (String target : MAIN_ACTIVITIES) {
                if (target.equals(name)) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static Bundle stripFragmentState(Bundle original) {
        Bundle copy;
        try {
            copy = new Bundle(original);
        } catch (Throwable t) {
            return null;
        }
        for (String key : FRAGMENT_BUNDLE_KEYS) {
            try {
                copy.remove(key);
            } catch (Throwable ignored) {}
        }
        try {
            List<String> extra = new ArrayList<>();
            for (String key : copy.keySet()) {
                if (key == null) continue;
                String lower = key.toLowerCase(Locale.US);
                if (lower.contains("fragment") || lower.contains("androidx.lifecycle.bundlablesavedstateregistry")) {
                    extra.add(key);
                }
            }
            for (String key : extra) {
                copy.remove(key);
            }
        } catch (Throwable ignored) {}
        return copy;
    }

    private Method resolveOnCreate(DexKitBridge bridge, ClassLoader classLoader, String activityClass) {
        String cacheKey = CACHE_KEY_PREFIX + activityClass;
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod(cacheKey, classLoader);
            if (cached != null) return cached;
        }
        if (bridge != null) {
            try {
                List<MethodData> candidates = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .declaredClass(activityClass)
                                        .name("onCreate")
                                        .paramTypes("android.os.Bundle")
                                        .returnType("void")
                        )
                );
                if (candidates == null || candidates.isEmpty()) {
                    candidates = bridge.findMethod(
                            FindMethod.create().matcher(
                                    MethodMatcher.create()
                                            .declaredClass(activityClass)
                                            .paramTypes("android.os.Bundle")
                                            .returnType("void")
                            )
                    );
                }
                if (candidates != null) {
                    for (MethodData md : candidates) {
                        try {
                            Method m = md.getMethodInstance(classLoader);
                            if (m.getParameterCount() != 1) continue;
                            if (m.getParameterTypes()[0] != Bundle.class) continue;
                            DexKitCache.saveMethod(cacheKey, m);
                            return m;
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            Method m = classLoader.loadClass(activityClass).getDeclaredMethod("onCreate", Bundle.class);
            m.setAccessible(true);
            DexKitCache.saveMethod(cacheKey, m);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
