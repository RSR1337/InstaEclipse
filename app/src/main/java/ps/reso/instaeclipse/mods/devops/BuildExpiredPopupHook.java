package ps.reso.instaeclipse.mods.devops;

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

public class BuildExpiredPopupHook {

    private static final String CACHE_SHOW  = "BuildExpiredShow";
    private static final String CACHE_CHECK = "BuildExpiredCheck";

    public void install(DexKitBridge bridge, ClassLoader classLoader) {

        XC_MethodHook noOpHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.removeBuildExpiredPopup) {
                    param.setResult(null);
                }
            }
        };

        XC_MethodHook falseHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.removeBuildExpiredPopup) {
                    param.setResult(false);
                }
            }
        };

        boolean hookedMain = false;

        if (DexKitCache.isCacheValid()) {
            Method show = DexKitCache.loadMethod(CACHE_SHOW, classLoader);
            if (show != null) {
                XposedBridge.hookMethod(show, noOpHook);
                FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                hookedMain = true;
            }
            Method check = DexKitCache.loadMethod(CACHE_CHECK, classLoader);
            if (check != null) {
                XposedBridge.hookMethod(check, falseHook);
            }
            if (hookedMain) return;
        }

        try {

            List<MethodData> showMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("lockout_active")
                            .returnType("void")));

            for (MethodData md : showMethods) {
                Method method;
                try { method = md.getMethodInstance(classLoader); } catch (Throwable e) { continue; }

                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1) continue;

                if (!params[0].getName().contains("FragmentActivity")) continue;

                XposedBridge.hookMethod(method, noOpHook);
                DexKitCache.saveMethod(CACHE_SHOW, method);
                ModuleLog.line("(IE|BuildExpired) ✅ hooked show-popup → "
                        + md.getClassName() + "." + md.getName());
                FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                hookedMain = true;
                break;
            }

            if (!hookedMain) {
                ModuleLog.line("(IE|BuildExpired) ⚠️ show-popup method not found, falling back to boolean hook only");
            }

            List<MethodData> checkMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("snooze_expiration_lockout_manager")
                            .returnType("boolean")));

            for (MethodData md : checkMethods) {
                Method method;
                try { method = md.getMethodInstance(classLoader); } catch (Throwable e) { continue; }

                XposedBridge.hookMethod(method, falseHook);
                DexKitCache.saveMethod(CACHE_CHECK, method);
                ModuleLog.line("(IE|BuildExpired) ✅ hooked snooze-check → "
                        + md.getClassName() + "." + md.getName());
                if (!hookedMain) {
                    FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                }
                break;
            }

        } catch (Throwable t) {
            ModuleLog.line("(IE|BuildExpired) ❌ install: " + t);
        }
    }
}
