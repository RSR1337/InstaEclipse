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

/**
 * Handles Ghost Mode for Voice Notes / Audio Messages in Instagram DMs.
 * Suppresses sending audio_played / voice listened receipts.
 */
public class GhostVoiceSeenHook {

    public void handleVoiceSeenBlock(DexKitBridge bridge) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.isGhostVoiceSeen) {
                    param.setResult(null);
                }
            }
        };

        // Cache hit — skip DexKit
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("GhostVoiceSeen", Module.hostClassLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                ModuleLog.line("(InstaEclipse | GhostVoiceSeen): ✅ Hooked: " + cached.getDeclaringClass().getName() + "." + cached.getName());
                FeatureStatusTracker.setHooked("GhostVoiceSeen");
                return;
            }
        }

        try {
            // Find methods referencing "send_voice_item_seen_marker" or "voice_item_seen" or "audio_played"
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("send_voice_item_seen_marker")));

            if (methods.isEmpty()) {
                methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create().usingStrings("audio_played")));
            }

            for (MethodData method : methods) {
                Method reflectMethod;
                try {
                    reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue;
                }

                try {
                    DexKitCache.saveMethod("GhostVoiceSeen", reflectMethod);
                    XposedBridge.hookMethod(reflectMethod, hook);

                    ModuleLog.line("(InstaEclipse | GhostVoiceSeen): ✅ Hooked: " +
                            method.getClassName() + "." + method.getName());
                    FeatureStatusTracker.setHooked("GhostVoiceSeen");
                    return;
                } catch (Throwable e) {
                    ModuleLog.line("(InstaEclipse | GhostVoiceSeen): ❌ Hook error: " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | GhostVoiceSeen): ❌ Error searching methods: " + t.getMessage());
        }
    }
}
