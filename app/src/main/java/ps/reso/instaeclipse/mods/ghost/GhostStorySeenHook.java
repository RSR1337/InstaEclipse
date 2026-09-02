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

public class GhostStorySeenHook {

    public void handleStorySeenBlock(DexKitBridge bridge) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.isGhostStory) param.setResult(null);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("GhostStorySeen", Module.hostClassLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                ModuleLog.line("(InstaEclipse | StoryBlock): ✅ Hooked (dynamic check): " + cached.getDeclaringClass().getName() + "." + cached.getName());
                FeatureStatusTracker.setHooked("GhostStories");
                return;
            }
        }

        try {

            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("media/seen/")));

            if (methods.isEmpty()) {
                ModuleLog.line("(InstaEclipse | StoryBlock): ❌ No methods found containing 'media/seen/'");
                return;
            }

            for (MethodData method : methods) {
                ClassDataList paramTypes = method.getParamTypes();
                String returnType = String.valueOf(method.getReturnType());

                Method reflectMethod;
                try {
                    reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue;
                }

                int modifiers = reflectMethod.getModifiers();

                if (Modifier.isFinal(modifiers) &&
                        returnType.contains("void") &&
                        paramTypes.size() <= 1) {

                    try {
                        DexKitCache.saveMethod("GhostStorySeen", reflectMethod);
                        XposedBridge.hookMethod(reflectMethod, hook);

                        ModuleLog.line("(InstaEclipse | StoryBlock): ✅ Hooked (dynamic check): " +
                                method.getClassName() + "." + method.getName());
                        FeatureStatusTracker.setHooked("GhostStories");
                        return;

                    } catch (Throwable e) {
                        ModuleLog.line("(InstaEclipse | StoryBlock): ❌ Hook error: " + e.getMessage());
                    }
                }
            }

        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | StoryBlock): ❌ Exception: " + t.getMessage());
        }
    }
}
