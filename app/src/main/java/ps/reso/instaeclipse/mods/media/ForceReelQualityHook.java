package ps.reso.instaeclipse.mods.media;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class ForceReelQualityHook {

    private static final String DICT_CLASS = "com.instagram.feed.media.LiveTreeMediaDict";
    private static final String VIDEO_VERSION_CLASS = "com.instagram.model.mediasize.ImmutablePandoVideoVersion";
    private static final int HEIGHT_HASH = "height".hashCode();

    private static final String CACHE_GETTER_KEY = "ForceReelQuality_VideoVersionsGetter";
    private static final String CACHE_HEIGHT_KEY = "ForceReelQuality_HeightGetterName";

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            Method videoVersionsGetter;
            String heightGetterName;

            if (DexKitCache.isCacheValid()) {
                videoVersionsGetter = DexKitCache.loadMethod(CACHE_GETTER_KEY, classLoader);
                heightGetterName = DexKitCache.loadString(CACHE_HEIGHT_KEY);
            } else {
                videoVersionsGetter = null;
                heightGetterName = null;
            }

            if (videoVersionsGetter == null || heightGetterName == null) {
                videoVersionsGetter = resolveVideoVersionsGetter(bridge, classLoader);
                heightGetterName = resolveHeightGetterName(bridge, classLoader);

                if (videoVersionsGetter == null || heightGetterName == null) {
                    ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ discovery failed");
                    return;
                }

                DexKitCache.saveMethod(CACHE_GETTER_KEY, videoVersionsGetter);
                DexKitCache.saveString(CACHE_HEIGHT_KEY, heightGetterName);
            }

            videoVersionsGetter.setAccessible(true);
            String finalHeightGetterName = heightGetterName;

            XposedBridge.hookMethod(videoVersionsGetter, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (FeatureFlags.forceReelQuality <= 0) return;
                    try {
                        Object result = param.getResult();
                        if (!(result instanceof List)) return;
                        List<?> versions = (List<?>) result;
                        if (versions.size() <= 1) return;

                        Object chosen = pickBestQuality(versions, FeatureFlags.forceReelQuality, finalHeightGetterName);
                        if (chosen != null) {
                            param.setResult(Collections.singletonList(chosen));
                        }
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ hook body – " + t);
                    }
                }
            });

            FeatureStatusTracker.setHooked("ForceReelQuality");
            ModuleLog.line("(InstaEclipse | ForceReelQuality): ✅ Hooked " + DICT_CLASS
                    + " (height=" + heightGetterName + ")");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ install – " + t);
        }
    }

    private static Method resolveVideoVersionsGetter(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            List<MethodData> results = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(DICT_CLASS)
                            .paramCount(0)
                            .usingEqStrings(List.of("video_versions"))));

            if (results.isEmpty()) {
                results = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramCount(0)
                                .usingEqStrings(List.of("video_versions"))));
            }

            for (MethodData md : results) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (m.getReturnType() != List.class) continue;
                    return m;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ resolveVideoVersionsGetter – " + t);
        }
        return null;
    }

    private static String resolveHeightGetterName(DexKitBridge bridge, ClassLoader classLoader) {
        String[] versionClasses = {
                VIDEO_VERSION_CLASS,
                "com.instagram.model.mediasize.VideoVersionIntf",
                "com.instagram.api.schemas.VideoVersionIntf"
        };
        for (String className : versionClasses) {
            try {
                List<MethodData> results = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .declaredClass(className)
                                .paramCount(0)
                                .returnType("java.lang.Integer")
                                .usingNumbers(List.of(HEIGHT_HASH))));
                if (!results.isEmpty()) return results.get(0).getName();
            } catch (Throwable ignored) {}
        }
        try {
            List<MethodData> results = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramCount(0)
                            .returnType("java.lang.Integer")
                            .usingNumbers(List.of(HEIGHT_HASH))));
            for (MethodData md : results) {
                String cn = md.getClassName();
                if (cn.contains("VideoVersion") || cn.contains("mediasize")) return md.getName();
            }
            if (!results.isEmpty()) return results.get(0).getName();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ resolveHeightGetterName – " + t);
        }
        return null;
    }

    private static Object pickBestQuality(List<?> items, int desired, String heightGetterName) {
        Object best = null;
        int bestDelta = Integer.MAX_VALUE;
        int bestHeight = -1;
        for (Object item : items) {
            if (item == null) continue;
            try {
                Method m = item.getClass().getMethod(heightGetterName);
                Object hObj = m.invoke(item);
                if (!(hObj instanceof Integer)) continue;
                int h = (Integer) hObj;
                if (h <= 0) continue;

                if (desired == Integer.MAX_VALUE) {
                    if (h > bestHeight) { bestHeight = h; best = item; }
                } else {
                    int delta = Math.abs(h - desired);
                    if (delta < bestDelta) { bestDelta = delta; best = item; }
                }
            } catch (Throwable ignored) {}
        }
        return best;
    }
}
