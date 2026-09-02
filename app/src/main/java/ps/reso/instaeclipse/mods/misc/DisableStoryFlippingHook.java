package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class DisableStoryFlippingHook {

    private static final XC_MethodHook HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (FeatureFlags.disableStoryFlipping) param.setResult(null);
        }
    };

    public void handleStoryFlippingDisable(DexKitBridge bridge) {
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("StoryFlipping", Module.hostClassLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, HOOK);
                ModuleLog.line("(InstaEclipse | StoryFlipping): ✅ Hooked (dynamic check): " + cached.getDeclaringClass().getName() + "." + cached.getName());
                return;
            }
        }
        try {
            findAndHookMethod(bridge);
        } catch (Exception e) {
            ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ Error handling Story Flipping hook: " + e.getMessage());
        }
    }

    private void findAndHookMethod(DexKitBridge bridge) {
        try {

            List<MethodData> methods = bridge.findMethod(
                    FindMethod.create().matcher(
                            MethodMatcher.create()
                                    .declaredClass("instagram.features.stories.fragment.ReelViewerFragment")
                                    .paramTypes("java.lang.Object")
                                    .returnType("void")
                                    .usingStrings("userSession")
                    )
            );

            if (methods.isEmpty()) {
                methods = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .usingStrings("end_scene", "userSession")
                                        .paramTypes("java.lang.Object")
                                        .returnType("void")
                        )
                );
            }

            if (methods.isEmpty()) {
                methods = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create()
                                        .usingStrings("end_scene")
                                        .paramCount(1)
                                        .returnType("void")
                        )
                );
            }

            if (methods.isEmpty()) {
                List<org.luckypray.dexkit.result.ClassData> reelClasses = bridge.findClass(
                        org.luckypray.dexkit.query.FindClass.create().matcher(
                                org.luckypray.dexkit.query.matchers.ClassMatcher.create()
                                        .usingStrings("ReelViewerFragment")
                        )
                );
                for (org.luckypray.dexkit.result.ClassData classData : reelClasses) {
                    methods = bridge.findMethod(
                            FindMethod.create().matcher(
                                    MethodMatcher.create()
                                            .declaredClass(classData.getName())
                                            .paramTypes("java.lang.Object")
                                            .returnType("void")
                                            .usingStrings("userSession")
                            )
                    );
                    if (!methods.isEmpty()) break;
                }
            }

            if (methods.isEmpty()) {
                ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ No methods found referencing 'end_scene'.");
                return;
            }

            for (MethodData method : methods) {
                try {
                    Method targetMethod = method.getMethodInstance(Module.hostClassLoader);
                    DexKitCache.saveMethod("StoryFlipping", targetMethod);
                    XposedBridge.hookMethod(targetMethod, HOOK);

                    ModuleLog.line("(InstaEclipse | StoryFlipping): ✅ Hooked (dynamic check): " +
                            method.getClassName() + "." + method.getName());
                    return;

                } catch (Exception e) {
                    ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ Error hooking method: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ Error during dynamic method discovery: " + e.getMessage());
        }
    }
}
