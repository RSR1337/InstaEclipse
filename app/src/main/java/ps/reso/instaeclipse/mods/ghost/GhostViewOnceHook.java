package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class GhostViewOnceHook {

    public void handleViewOnceBlock(DexKitBridge bridge) {
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("GhostViewOnce", Module.hostClassLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, buildViewOnceHook());
                ModuleLog.line("(InstaEclipse | ViewOnce): ✅ Hooked (dynamic check): " + cached.getDeclaringClass().getName() + "." + cached.getName());
                FeatureStatusTracker.setHooked("GhostViewOnce");
                return;
            }
        }

        try {
            
            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create().usingStrings("visual_item_seen")
                    )
            );

            if (methods.isEmpty()) {
                ModuleLog.line("(InstaEclipse | ViewOnce): ❌ No methods found containing 'visual_item_seen'");
                return;
            }

            for (MethodData method : methods) {
                ClassDataList paramTypes = method.getParamTypes();
                String returnType = String.valueOf(method.getReturnType());

                
                if (paramTypes.size() >= 2 && paramTypes.size() <= 4 && returnType.contains("void")) {

                    Method reflectMethod;
                    try {
                        reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                    } catch (Throwable e) {
                        
                        continue;
                    }

                    
                    DexKitCache.saveMethod("GhostViewOnce", reflectMethod);
                    XposedBridge.hookMethod(reflectMethod, buildViewOnceHook());

                    ModuleLog.line("(InstaEclipse | ViewOnce): ✅ Hooked (dynamic check): " +
                            method.getClassName() + "." + method.getName());
                    FeatureStatusTracker.setHooked("GhostViewOnce");
                    return;
                }
            }

        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | ViewOnce): ❌ Exception: " + e.getMessage());
        }
    }

    private static XC_MethodHook buildViewOnceHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!FeatureFlags.isGhostViewOnce) return;
                Object rw = param.args[2];
                if (rw == null) return;
                for (Method m : rw.getClass().getDeclaredMethods()) {
                    if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
                    try {
                        m.setAccessible(true);
                        String value = (String) m.invoke(rw);
                        if (value != null && (value.contains("visual_item_seen") ||
                                value.contains("send_visual_item_seen_marker"))) {
                            param.setResult(null);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        };
    }
}
