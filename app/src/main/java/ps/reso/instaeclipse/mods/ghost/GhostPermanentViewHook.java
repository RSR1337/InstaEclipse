package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;


public class GhostPermanentViewHook {

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("ViewOnceMedia", classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, buildHook());
                FeatureStatusTracker.setHooked("PermanentViewMode");
                return;
            }
        }

        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("archived_media_timestamp", "view_mode")
                            .paramCount(1)));

            if (methods.isEmpty()) {
                ModuleLog.line("(IE|ViewOnceMedia) ❌ unsafeParseFromJson not found");
                return;
            }

            
            Method target = null;
            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (m.getReturnType() != void.class) {
                        target = m;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (target == null) {
                
                try {
                    target = methods.get(0).getMethodInstance(classLoader);
                } catch (Throwable t) {
                    ModuleLog.line("(IE|ViewOnceMedia) ❌ Could not resolve method: " + t);
                    return;
                }
            }

            ModuleLog.line("(IE|ViewOnceMedia) ✅ hooking "
                    + target.getDeclaringClass().getName() + "." + target.getName());

            DexKitCache.saveMethod("ViewOnceMedia", target);
            XposedBridge.hookMethod(target, buildHook());

            FeatureStatusTracker.setHooked("PermanentViewMode");
            ModuleLog.line("(IE|ViewOnceMedia) ✅ hooked");

        } catch (Throwable t) {
            ModuleLog.line("(IE|ViewOnceMedia) ❌ " + t);
        }
    }

    private static XC_MethodHook buildHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.permanentViewMode) return;
                Object result = param.getResult();
                if (result == null) return;

                
                int seenCount = 0;
                Class<?> cls = result.getClass();
                while (cls != null && cls != Object.class) {
                    for (Field f : cls.getDeclaredFields()) {
                        if (f.getType() == int.class) {
                            f.setAccessible(true);
                            try { seenCount = f.getInt(result); } catch (Throwable ignored) {}
                        }
                    }
                    cls = cls.getSuperclass();
                }

                cls = result.getClass();
                while (cls != null && cls != Object.class) {
                    for (Field f : cls.getDeclaredFields()) {
                        if (f.getType() != String.class) continue;
                        f.setAccessible(true);
                        try {
                            String val = (String) f.get(result);
                            if ("once".equals(val)) {
                                
                                if (seenCount >= 1) return;
                                f.set(result, "permanent");
                            } else if ("replayable".equals(val) || "allow_replay".equals(val)) {
                                
                                if (seenCount >= 2) return;
                                f.set(result, "permanent");
                            }
                        } catch (Throwable ignored) {}
                    }
                    cls = cls.getSuperclass();
                }
            }
        };
    }
}
