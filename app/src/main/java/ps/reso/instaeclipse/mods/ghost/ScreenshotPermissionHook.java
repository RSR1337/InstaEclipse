package ps.reso.instaeclipse.mods.ghost;

import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class ScreenshotPermissionHook {

    public void install(ClassLoader classLoader) {
        try {

            XposedHelpers.findAndHookMethod(Window.class, "setFlags",
                    int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!FeatureFlags.allowScreenshots) return;
                            param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                            param.args[1] = (int) param.args[1] & ~WindowManager.LayoutParams.FLAG_SECURE;
                        }
                    });

            XposedHelpers.findAndHookMethod(Window.class, "addFlags",
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!FeatureFlags.allowScreenshots) return;
                            param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                        }
                    });

            ModuleLog.line("(InstaEclipse | ScreenshotPermission): ✅ Hooked Window.setFlags + addFlags");
            FeatureStatusTracker.setHooked("AllowScreenshots");

        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | ScreenshotPermission): ❌ " + e.getMessage());
        }
    }
}
