package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class GhostDMSeenHook {
    public void handleSeenBlock(DexKitBridge bridge) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.isGhostSeen) param.setResult(null);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("GhostSeen", Module.hostClassLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                ModuleLog.line("(InstaEclipse | GhostModeSeen): ✅ Hooked: " + cached.getDeclaringClass().getName() + "." + cached.getName());
                FeatureStatusTracker.setHooked("GhostSeen");
                return;
            }
        }

        try {

            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("mark_thread_seen-")));

            if (methods.isEmpty()) {
                ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ No methods found using 'mark_thread_seen-'");
                return;
            }

            for (MethodData method : methods) {
                Method reflectMethod;
                try {
                    reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue;
                }

                int modifiers = reflectMethod.getModifiers();
                String returnType = String.valueOf(method.getReturnType());
                ClassDataList paramTypes = method.getParamTypes();

                if (Modifier.isStatic(modifiers)
                        && Modifier.isFinal(modifiers)
                        && returnType.contains("void")
                        && paramTypes.size() >= 3) {

                    try {
                        DexKitCache.saveMethod("GhostSeen", reflectMethod);
                        XposedBridge.hookMethod(reflectMethod, hook);

                        ModuleLog.line("(InstaEclipse | GhostModeSeen): ✅ Hooked: " +
                                method.getClassName() + "." + method.getName());
                        FeatureStatusTracker.setHooked("GhostSeen");
                        return;

                    } catch (Throwable e) {
                        ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ Hook error: " + e.getMessage());
                    }
                }
            }

            for (MethodData method : methods) {
                Method reflectMethod;
                try {
                    reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue;
                }
                String returnType = String.valueOf(method.getReturnType());
                if (!returnType.contains("void") || method.getParamTypes().size() < 2) continue;
                try {
                    DexKitCache.saveMethod("GhostSeen", reflectMethod);
                    XposedBridge.hookMethod(reflectMethod, hook);
                    ModuleLog.line("(InstaEclipse | GhostModeSeen): ✅ Hooked: " +
                            method.getClassName() + "." + method.getName());
                    FeatureStatusTracker.setHooked("GhostSeen");
                    return;
                } catch (Throwable e) {
                    ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ Hook error: " + e.getMessage());
                }
            }

        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ DexKit exception: " + e.getMessage());
        }
    }

}
