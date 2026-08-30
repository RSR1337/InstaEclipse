package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class DirectCapabilitiesHook {

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (FeatureFlags.bypassChannelRestrictions) {
                    if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                        String capabilityName = param.args[0].toString();
                        if (capabilityName.contains("BROADCAST_CHANNEL_RESTRICTION_BYPASS")
                                || capabilityName.contains("MESSAGE_FORWARDING")
                                || capabilityName.contains("MESSAGE_SAVE_MEDIA")
                                || capabilityName.contains("ENABLE_VISUAL_MESSAGE_REPLY")) {
                            param.setResult(true);
                        }
                    }
                }
            }
        };

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("DirectCapabilities", Module.hostClassLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) {
                    XposedBridge.hookMethod(m, hook);
                    ModuleLog.line("(InstaEclipse | DirectCapabilities): ✅ Hooked: " + m.getDeclaringClass().getName() + "." + m.getName());
                }
                FeatureStatusTracker.setHooked("DirectCapabilities");
                return;
            }
        }

        try {
            Class<?> capabilitiesClass = null;
            try {
                capabilitiesClass = classLoader.loadClass("com.instagram.direct.capabilities.Capabilities");
            } catch (Throwable ignored) {}

            if (capabilitiesClass != null) {
                List<Method> matched = new ArrayList<>();
                for (Method m : capabilitiesClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getReturnType() == boolean.class) {
                        XposedBridge.hookMethod(m, hook);
                        ModuleLog.line("(InstaEclipse | DirectCapabilities): ✅ Hooked Capabilities." + m.getName());
                        matched.add(m);
                    }
                }
                if (!matched.isEmpty()) {
                    DexKitCache.saveMethods("DirectCapabilities", matched);
                    FeatureStatusTracker.setHooked("DirectCapabilities");
                }
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | DirectCapabilities): ❌ Error: " + t.getMessage());
        }
    }
}
