package ps.reso.instaeclipse.mods.ads;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class AdBlocker {

    private static final String[] INSERT_ITEM_MARKERS = {
            "Is ad pod",
            "SponsoredContentController.insertItem",
            "SponsoredContentController",
            "ad_pod_id"
    };

    public void disableSponsoredContent(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (FeatureFlags.isAdBlockEnabled) param.setResult(false);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("AdBlocker", classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                FeatureStatusTracker.setHooked("AdBlocker");
                return;
            }
        }

        try {
            for (String marker : INSERT_ITEM_MARKERS) {
                List<MethodData> methods = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create().usingStrings(marker)
                        )
                );

                if (methods.isEmpty()) {
                    ModuleLog.line("(InstaEclipse | AdBlocker): ⚠️ No methods found referencing '" + marker + "'");
                    continue;
                }

                for (MethodData method : methods) {
                    String returnType = String.valueOf(method.getReturnType());
                    if (!returnType.contains("boolean")) continue;

                    try {
                        Method targetMethod = method.getMethodInstance(classLoader);
                        DexKitCache.saveMethod("AdBlocker", targetMethod);
                        XposedBridge.hookMethod(targetMethod, hook);

                        ModuleLog.line("(InstaEclipse | AdBlocker): ✅ Hooked (dynamic check, marker='" + marker + "'): " +
                                method.getClassName() + "." + method.getName());
                        FeatureStatusTracker.setHooked("AdBlocker");
                        return;

                    } catch (Throwable hookEx) {
                        ModuleLog.line("(InstaEclipse | AdBlocker): ❌ Failed to hook: " +
                                method.getName() + " → " + hookEx.getMessage());
                    }
                }
            }

            ModuleLog.line("(InstaEclipse | AdBlocker): ❌ No valid methods hooked (all markers exhausted).");

        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | AdBlocker): ❌ Exception: " + t.getMessage());
        }
    }
}
