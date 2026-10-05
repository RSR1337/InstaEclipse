package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;

public class GhostViewOnceHook {

    private static final String CACHE_KEY = "GhostViewOnce_v2";

    private static final String[] SEEN_HANDLER_MARKERS = {
            "visual_threads/%s/item_seen/",
            "visual_items/%s/seen/"
    };

    public void handleViewOnceBlock(DexKitBridge bridge) {
        ClassLoader classLoader = Module.hostClassLoader;
        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods(CACHE_KEY, classLoader);
            if (cached != null && !cached.isEmpty()) {
                hookAll(cached, "cached");
                return;
            }
        }

        try {
            Set<Method> targets = new LinkedHashSet<>();

            List<MethodData> legacy = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("visual_item_seen")));
            for (MethodData md : legacy) {
                if (!"void".equals(md.getReturnTypeName())) continue;
                int params = md.getParamTypeNames().size();
                if (params < 3 || params > 4) continue;
                try {
                    targets.add(md.getMethodInstance(classLoader));
                } catch (Throwable ignored) {}
            }

            if (targets.isEmpty()) {
                for (String marker : SEEN_HANDLER_MARKERS) {
                    for (MethodData md : IgDex.findMethodsUsingAll(bridge, classLoader, marker)) {
                        if (!"void".equals(md.getReturnTypeName())) continue;
                        try {
                            targets.add(md.getMethodInstance(classLoader));
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (targets.isEmpty()) {
                ModuleLog.line("(InstaEclipse | ViewOnce): ❌ no visual-seen dispatcher found");
                return;
            }
            List<Method> list = new ArrayList<>(targets);
            DexKitCache.saveMethods(CACHE_KEY, list);
            hookAll(list, "dynamic");
        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | ViewOnce): ❌ Exception: " + e.getMessage());
        }
    }

    private static void hookAll(List<Method> methods, String how) {
        int hooked = 0;
        for (Method m : methods) {
            try {
                XposedBridge.hookMethod(m, buildViewOnceHook());
                hooked++;
                ModuleLog.line("(InstaEclipse | ViewOnce): ✅ Hooked (" + how + "): "
                        + m.getDeclaringClass().getName() + "." + m.getName());
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | ViewOnce): ❌ hook " + m.getName() + ": " + t.getMessage());
            }
        }
        if (hooked > 0) FeatureStatusTracker.setHooked("GhostViewOnce");
    }

    private static XC_MethodHook buildViewOnceHook() {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.isGhostViewOnce || param.args == null) return;
                for (Object arg : param.args) {
                    if (isVisualSeenMutation(arg)) {
                        param.setResult(null);
                        return;
                    }
                }
            }
        };
    }

    private static boolean isVisualSeenMutation(Object obj) {
        if (obj == null) return false;
        String cn = obj.getClass().getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.")) return false;
        for (Method m : obj.getClass().getDeclaredMethods()) {
            if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
            try {
                m.setAccessible(true);
                String value = (String) m.invoke(obj);
                if (value != null && (value.contains("visual_item_seen")
                        || value.contains("send_visual_item_seen_marker"))) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
