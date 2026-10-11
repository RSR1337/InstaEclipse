package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;

public class GhostReplayLimitHook {

    private static final String SYNC_CACHE_KEY = "Replays_sync_v2";

    private static final Set<Class<?>> VISUAL_MODEL_CLASSES = ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, List<Field>> VISUAL_FIELDS = new ConcurrentHashMap<>();

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        hookUpdateMethod(bridge, classLoader);
        hookParseFromJsonMethod(bridge, classLoader);
        hookSyncMethod(bridge, classLoader);
    }

    
    private void hookUpdateMethod(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.enableUnlimitedReplays) param.setResult(null);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("Replays_update", classLoader);
            if (cached != null) { XposedBridge.hookMethod(cached, hook); return; }
        }

        try {
            List<MethodData> methods = IgDex.findMethodsUsingAll(bridge, classLoader,
                    "Entry should exist before function call",
                    "Visual message is missing from thread entry");

            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (m.getReturnType() != void.class) continue;
                    DexKitCache.saveMethod("Replays_update", m);
                    XposedBridge.hookMethod(m, hook);
                    ModuleLog.line("(IE|Replays) ✅ update hook → " + md.getClassName() + "." + md.getName());
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|Replays) ❌ hookUpdateMethod: " + t);
        }
    }

    
    private void hookParseFromJsonMethod(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (result != null && result != param.thisObject) rememberVisualModel(result.getClass());
                if (!FeatureFlags.enableUnlimitedReplays) return;
                zeroReplayCountFields(param.thisObject);
                if (result != null && result != param.thisObject)
                    zeroReplayCountFields(result);
            }
        };

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("Replays_parse", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, hook);
                ModuleLog.line("[IE] ✅ Ghost Replay – parseFromJson");
                return;
            }
        }

        try {
            List<MethodData> methods = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("seen_count", "tap_models")));

            List<Method> hooked = new ArrayList<>();
            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    XposedBridge.hookMethod(m, hook);
                    hooked.add(m);
                    ModuleLog.line("(IE|Replays) ✅ parseFromJson hook → " + md.getClassName() + "." + md.getName());
                } catch (Throwable ignored) {}
            }
            if (hooked.isEmpty()) {
                ModuleLog.line("(IE|Replays) ❌ parseFromJson method not found");
            } else {
                DexKitCache.saveMethods("Replays_parse", hooked);
                ModuleLog.line("[IE] ✅ Ghost Replay – parseFromJson");
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|Replays) ❌ hookParseFromJsonMethod: " + t);
        }
    }

    
    private void hookSyncMethod(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (FeatureFlags.enableUnlimitedReplays) zeroVisualReplayCounts(param.thisObject);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod(SYNC_CACHE_KEY, classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                ModuleLog.line("[IE] ✅ Ghost Replay – sync");
                FeatureStatusTracker.setHooked("UnlimitedReplays");
                return;
            }
        }

        try {
            List<MethodData> methods = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramTypes("com.instagram.common.session.UserSession", null, null)
                            .returnType("void")));

            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (!isMessageMerge(m)) continue;
                    DexKitCache.saveMethod(SYNC_CACHE_KEY, m);
                    XposedBridge.hookMethod(m, hook);
                    ModuleLog.line("[IE] ✅ Ghost Replay – sync");
                    ModuleLog.line("(IE|Replays) ✅ sync hook → " + md.getClassName() + "." + md.getName());
                    FeatureStatusTracker.setHooked("UnlimitedReplays");
                    return;
                } catch (Throwable ignored) {}
            }
            ModuleLog.line("(IE|Replays) ❌ sync method not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Replays) ❌ hookSyncMethod: " + t);
        }
    }

    
    private static boolean isMessageMerge(Method m) {
        int mod = m.getModifiers();
        Class<?>[] p = m.getParameterTypes();
        return Modifier.isSynchronized(mod) && !Modifier.isStatic(mod)
                && p.length == 3 && p[1] == m.getDeclaringClass();
    }

    private static void rememberVisualModel(Class<?> cls) {
        String name = cls.getName();
        if (name.startsWith("X.") || name.startsWith("com.instagram.")) VISUAL_MODEL_CLASSES.add(cls);
    }

    private static void zeroVisualReplayCounts(Object message) {
        if (message == null || VISUAL_MODEL_CLASSES.isEmpty()) return;
        try {
            List<Field> fields = VISUAL_FIELDS.get(message.getClass());
            if (fields == null) {
                List<Field> found = new ArrayList<>();
                for (Class<?> c = message.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || !VISUAL_MODEL_CLASSES.contains(f.getType())) continue;
                        f.setAccessible(true);
                        found.add(f);
                    }
                }
                fields = Collections.unmodifiableList(found);
                if (!found.isEmpty()) VISUAL_FIELDS.put(message.getClass(), fields);
            }
            for (Field f : fields) zeroReplayCountFields(f.get(message));
        } catch (Throwable ignored) {}
    }

    private static void zeroReplayCountFields(Object obj) {
        if (obj == null) return;
        String owner = obj.getClass().getName();
        if (!owner.startsWith("X.") && !owner.startsWith("com.instagram.")) return;
        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                if (f.getType() != int.class) continue;
                f.setAccessible(true);
                int val = f.getInt(obj);
                if (val >= 1 && val <= 10) {
                    f.setInt(obj, 0);
                }
            }
        } catch (Throwable ignored) {}
    }
}