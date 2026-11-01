package ps.reso.instaeclipse.mods.feed;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;
import ps.reso.instaeclipse.utils.core.DexKitQueryCache;

public class HideSuggestedFeedItemsHook {

    private static final String CACHE_KEY_PARSER = "FeedItemParserClass";
    private static final String CACHE_KEY_REELS_RESPONSES = "ReelsResponseClasses_v3";

    private static volatile Class<?> sClipsItemTypeEnumClass = null;
    private static final String CACHE_KEY_HAS_LIKED = "MediaHasLikedGetter";

    // "has_liked" getter: Media.()Boolean on IG 447+, LiveTreeMediaDict.()Boolean on <= 446 (436)
    // — reached through the Media field of that type. Resolved once at install (#249).
    private static volatile Method hasLikedGetter;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        installFeedHook(bridge, classLoader);
        installReelsHook(bridge, classLoader);
        markHookedForEnabledFlags();
    }

    private void installFeedHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook filterHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (!FeatureFlags.hideSuggestionsInFeed && !FeatureFlags.hideThreadsSuggestions
                        && !FeatureFlags.hideLikedPosts) return;

                Object result = param.getResult();
                if (result == null) return;

                boolean hasMedia = false;
                boolean isThreadsUnit = false;
                Object media = null;
                for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(result.getClass())) {
                    String typeName = f.getType().getSimpleName();
                    if (typeName.equals("Media")) {
                        try {
                            f.setAccessible(true);
                            media = f.get(result);
                            if (media != null) { hasMedia = true; break; }
                        } catch (Throwable ignored) {}
                    } else if (typeName.contains("Threads") || typeName.startsWith("TextApp") || typeName.startsWith("XDTTextApp")) {
                        try {
                            f.setAccessible(true);
                            if (f.get(result) != null) isThreadsUnit = true;
                        } catch (Throwable ignored) {}
                    }
                }

                if (hasMedia) {
                    // Real post: only hidden when "Hide liked posts" is on and you already liked it.
                    // Applied at parse time, so a post liked now disappears on the next refresh.
                    if (FeatureFlags.hideLikedPosts && isLiked(media)) param.setResult(null);
                    return;
                }
                if (!FeatureFlags.hideSuggestionsInFeed && !FeatureFlags.hideThreadsSuggestions) return;

                boolean shouldHide = isThreadsUnit ? FeatureFlags.hideThreadsSuggestions
                                                    : FeatureFlags.hideSuggestionsInFeed;
                if (!shouldHide) return;

                param.setResult(null);
            }
        };

        resolveHasLikedGetter(bridge, classLoader);

        if (DexKitCache.isCacheValid()) {
            String cached = DexKitCache.loadString(CACHE_KEY_PARSER);
            if (cached != null && isFeedItemParser(cached, classLoader)) {
                try {
                    hookBridgeMethod(cached, classLoader, filterHook);
                    ModuleLog.line("(InstaEclipse | HideSuggestedFeed): ✅ Cache hook succeeded: " + cached);
                    return;
                } catch (Throwable t) {
                    ModuleLog.line("(InstaEclipse | HideSuggestedFeed): ⚠️ Cache hook failed: " + t.getMessage());
                }
            }
        }

        try {
            // Other classes use the same strings (on 451 a large string table, X.0000, matches
            // first), so every match is checked to really be the FeedItem parser.
            String targetClass = null;
            String[][] queries = {
                    {"clips_netego", "suggested_users", "Unknown FeedItem Type"},
                    {"clips_netego", "media_or_ad"},
                    {"clips_netego", "stories_netego", "bloks_netego"},
                    {"bloks_netego", "media_or_ad"},
                    {"Unknown FeedItem Type"}};
            for (String[] strings : queries) {
                for (MethodData md : IgDex.findMethod(bridge, FindMethod.create().matcher(
                        MethodMatcher.create().usingStrings(strings)))) {
                    if (isFeedItemParser(md.getClassName(), classLoader)) { targetClass = md.getClassName(); break; }
                }
                if (targetClass != null) break;
            }

            if (targetClass == null) {
                ModuleLog.line("(InstaEclipse | HideSuggested): ❌ FeedItem parser not found.");
                return;
            }

            DexKitCache.saveString(CACHE_KEY_PARSER, targetClass);
            hookBridgeMethod(targetClass, classLoader, filterHook);
            ModuleLog.line("(InstaEclipse | HideSuggestedFeed): ✅ Hooked: " + targetClass);

        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | HideSuggestedFeed): ❌ Exception: " + t.getMessage());
        }
    }

    private void installReelsHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook responseFilterHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.hideSuggestionsInFeed && !FeatureFlags.hideThreadsSuggestions) return;

                Object result = param.getResult();
                if (!(result instanceof List)) return;

                List<?> list = (List<?>) result;
                if (list.isEmpty()) return;

                boolean modified = false;
                List<Object> filtered = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (shouldHideClipsItem(item)) {
                        modified = true;
                    } else {
                        filtered.add(item);
                    }
                }

                if (modified) {
                    param.setResult(filtered);
                }
            }
        };

        if (DexKitCache.isCacheValid()) {
            String cached = DexKitCache.loadString(CACHE_KEY_REELS_RESPONSES);
            if (cached != null && !cached.isEmpty()) {
                int cachedHooked = 0;
                for (String clsName : cached.split(";")) {
                    try {
                        cachedHooked += hookReelsResponseClass(clsName, classLoader, responseFilterHook);
                    } catch (Throwable ignored) {}
                }
                if (cachedHooked > 0) {
                    ModuleLog.line("(InstaEclipse | HideSuggestedReels): ✅ Cache response hooks succeeded (" + cachedHooked + " methods).");
                    return;
                }
            }
        }

        try {
            Set<String> targetClasses = new HashSet<>();

            // Strategy 1: Method containing ClipsItemsListResponseRest marker
            try {
                List<MethodData> methods = IgDex.findMethod(bridge, 
                        FindMethod.create().matcher(
                                MethodMatcher.create().addUsingString("ClipsItemsListResponseRest", StringMatchType.Contains)
                        )
                );
                for (MethodData md : methods) {
                    targetClasses.add(md.getClassName());
                }
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | HideSuggestedReels): Strategy 1 error: " + t.getMessage());
            }

            // Strategy 2: Method containing getClipsItemsOrEmpty marker
            if (targetClasses.isEmpty()) {
                try {
                    List<MethodData> methods = IgDex.findMethod(bridge, 
                            FindMethod.create().matcher(
                                    MethodMatcher.create().addUsingString("getClipsItemsOrEmpty", StringMatchType.Contains)
                            )
                    );
                    for (MethodData md : methods) {
                        targetClasses.add(md.getClassName());
                    }
                } catch (Throwable ignored) {}
            }

            // Strategy 3: Method containing getMediasForCarrera marker
            if (targetClasses.isEmpty()) {
                try {
                    List<MethodData> methods = IgDex.findMethod(bridge, 
                            FindMethod.create().matcher(
                                    MethodMatcher.create().addUsingString("getMediasForCarrera", StringMatchType.Contains)
                            )
                    );
                    for (MethodData md : methods) {
                        targetClasses.add(md.getClassName());
                    }
                } catch (Throwable ignored) {}
            }

            // Also discover all implementations of the response interface (e.g. C2QH REST & C43714GyF GraphQL)
            Set<String> allRelatedClasses = new HashSet<>(targetClasses);
            for (String clsName : targetClasses) {
                Class<?> cls = loadClassSafe(clsName, classLoader);
                if (cls == null) continue;
                for (Class<?> itf : cls.getInterfaces()) {
                    if (hasListReturningMethod(itf)) {
                        try {
                            List<ClassData> implementers = IgDex.findClass(bridge, 
                                    FindClass.create().matcher(
                                            ClassMatcher.create().addInterface(itf.getName())
                                    )
                            );
                            for (ClassData cd : implementers) {
                                allRelatedClasses.add(cd.getName());
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            int totalHooked = 0;
            for (String clsName : allRelatedClasses) {
                try {
                    totalHooked += hookReelsResponseClass(clsName, classLoader, responseFilterHook);
                } catch (Throwable ignored) {}
            }

            if (totalHooked > 0) {
                DexKitCache.saveString(CACHE_KEY_REELS_RESPONSES, String.join(";", allRelatedClasses));
                ModuleLog.line("(InstaEclipse | HideSuggestedReels): ✅ Hooked " + totalHooked + " response method(s) across " + allRelatedClasses.size() + " class(es).");
            } else {
                ModuleLog.line("(InstaEclipse | HideSuggestedReels): ⚠️ No Reels response methods found.");
            }

        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | HideSuggestedReels): ❌ Response hook error: " + t.getMessage());
        }
    }

    private boolean shouldHideClipsItem(Object item) {
        if (item == null) return false;

        Enum<?> clipsType = getClipsItemTypeEnum(item);
        if (clipsType != null) {
            String name = clipsType.name();

            // 1. Organic reels are videos - NEVER hide them!
            if ("ORGANIC".equals(name)) {
                return false;
            }

            // 2. Ads are handled separately by AdBlockerHook - do not touch them here
            if (name.startsWith("AD") || "MULTI_ADS".equals(name)) {
                return false;
            }

            // 3. Threads promotions in Reels
            if ("NETEGO_THREADS_IN_REELS_UNIT_ACQUISITION".equals(name) || name.contains("THREADS")) {
                if (FeatureFlags.hideThreadsSuggestions || FeatureFlags.hideSuggestionsInFeed) {
                    ModuleLog.line("(InstaEclipse | HideSuggestedReels): 🚫 Hiding Threads item in Reels (" + name + ")");
                    return true;
                }
                return false;
            }

            // 4. Suggested accounts, suggested creators, Netego cards, Midcards
            // These correspond exactly to the 2x2 suggested user grid and Netego units
            if (FeatureFlags.hideSuggestionsInFeed) {
                if ("SUGGESTED_USERS".equals(name) ||
                    "NETEGO_SUGGESTED_USERS".equals(name) ||
                    "NETEGO_SUGGESTED_CREATORS".equals(name) ||
                    "CREATORS_YOU_MAY_FOLLOW".equals(name) ||
                    "NETEGO".equals(name) ||
                    "NETEGO_AD4AD".equals(name) ||
                    "NETEGO_AD4AD_CREATORS".equals(name) ||
                    "MIDCARD".equals(name) ||
                    "QPMIDCARD".equals(name) ||
                    "SCREENTIME_MIDCARD".equals(name) ||
                    "SCROLL_PIVOT".equals(name) ||
                    "STORIES_IN_REELS".equals(name) ||
                    "REELS_QP_UNIT".equals(name) ||
                    "TYA_IN_REELS".equals(name) ||
                    "GLIMMER".equals(name) ||
                    "ATN_MIDCARD".equals(name) ||
                    name.contains("SUGGESTED") ||
                    name.contains("CREATOR") ||
                    name.contains("MIDCARD")) {
                    ModuleLog.line("(InstaEclipse | HideSuggestedReels): 🚫 Hiding suggested Reels item (" + name + ")");
                    return true;
                }
            }

            return false;
        }

        // Safety principle: never hide unknown items to prevent breaking normal reel videos/views
        return false;
    }

    private static Enum<?> getClipsItemTypeEnum(Object item) {
        if (item == null) return null;
        Class<?> cls = item.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().isEnum()) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(item);
                        if (val instanceof Enum) {
                            Enum<?> enumVal = (Enum<?>) val;
                            Class<?> enumCls = enumVal.getDeclaringClass();
                            Class<?> cached = sClipsItemTypeEnumClass;
                            if (cached != null) {
                                if (cached.equals(enumCls)) {
                                    return enumVal;
                                }
                            } else if (isClipsItemTypeEnumClass(enumCls)) {
                                sClipsItemTypeEnumClass = enumCls;
                                return enumVal;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean isClipsItemTypeEnumClass(Class<?> enumCls) {
        Object[] constants = enumCls.getEnumConstants();
        if (constants == null || constants.length < 5) return false;
        boolean hasOrganic = false;
        boolean hasNetegoOrSuggested = false;
        for (Object c : constants) {
            String name = ((Enum<?>) c).name();
            if ("ORGANIC".equals(name)) {
                hasOrganic = true;
            } else if ("SUGGESTED_USERS".equals(name) || "NETEGO".equals(name) || "MIDCARD".equals(name)) {
                hasNetegoOrSuggested = true;
            }
        }
        return hasOrganic && hasNetegoOrSuggested;
    }

    private boolean hasListReturningMethod(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            if (List.class.isAssignableFrom(m.getReturnType()) && m.getParameterTypes().length >= 1) {
                for (Class<?> p : m.getParameterTypes()) {
                    if (p.getName().contains("UserSession") || p.getSimpleName().equals("UserSession")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int hookReelsResponseClass(String className, ClassLoader classLoader, XC_MethodHook hook)
            throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, false, classLoader);
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) return 0;

        int hookedCount = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (List.class.isAssignableFrom(m.getReturnType())) {
                Class<?>[] params = m.getParameterTypes();
                boolean takesUserSession = false;
                for (Class<?> p : params) {
                    if (p.getName().contains("UserSession") || p.getSimpleName().equals("UserSession")) {
                        takesUserSession = true;
                        break;
                    }
                }
                // ONLY hook methods that take UserSession (such as Bf5 and Bf7 which return the stream items).
                // Do NOT hook methods taking 0 params (such as Ccb and Ccd which return List<Media>).
                if (takesUserSession) {
                    XposedBridge.hookMethod(m, hook);
                    hookedCount++;
                }
            }
        }
        return hookedCount;
    }

    private Class<?> loadClassSafe(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The FeedItem JSON parser: has bridge parse methods and a static serializer
     * (writer, FeedItem) whose FeedItem declares a Media field.
     */
    private static boolean isFeedItemParser(String className, ClassLoader classLoader) {
        try {
            Class<?> c = Class.forName(className, false, classLoader);
            boolean bridges = false, serializer = false;
            for (Method m : ps.reso.instaeclipse.utils.core.SafeReflect.methods(c)) {
                if (m.isBridge()) bridges = true;
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2) {
                    for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(m.getParameterTypes()[1]))
                        if (f.getType().getName().equals("com.instagram.feed.media.Media")) { serializer = true; break; }
                }
            }
            return bridges && serializer;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void resolveHasLikedGetter(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            if (DexKitCache.isCacheValid()) {
                Method cached = DexKitCache.loadMethod(CACHE_KEY_HAS_LIKED, classLoader);
                if (cached != null) { hasLikedGetter = cached; return; }
            }
            for (String owner : new String[]{"com.instagram.feed.media.Media",
                    "com.instagram.feed.media.LiveTreeMediaDict"}) {
                List<MethodData> found;
                try {
                    found = DexKitQueryCache.findMethod(bridge, FindMethod.create().matcher(MethodMatcher.create()
                            .declaredClass(owner)
                            .usingEqStrings(java.util.List.of("has_liked"))
                            .returnType("java.lang.Boolean")
                            .paramCount(0)));
                } catch (Throwable t) { continue; }
                if (found.size() != 1) continue;
                Method m = found.get(0).getMethodInstance(classLoader);
                m.setAccessible(true);
                hasLikedGetter = m;
                DexKitCache.saveMethod(CACHE_KEY_HAS_LIKED, m);
                ModuleLog.line("(InstaEclipse | HideLiked): ✅ has_liked getter " + owner + "." + m.getName());
                return;
            }
            ModuleLog.line("(InstaEclipse | HideLiked): ❌ has_liked getter not found");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | HideLiked): ❌ " + t.getMessage());
        }
    }

    public static boolean isLiked(Object media) {
        Method getter = hasLikedGetter;
        if (media == null || getter == null) return false;
        try {
            Object target = media;
            Class<?> owner = getter.getDeclaringClass();
            if (!owner.isInstance(media)) {
                target = null;
                for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(media.getClass())) {
                    if (!owner.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    target = f.get(media);
                    break;
                }
                if (target == null) return false;
            }
            return Boolean.TRUE.equals(getter.invoke(target));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hookBridgeMethod(String className, ClassLoader classLoader, XC_MethodHook hook)
            throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className, false, classLoader);
        for (Method m : ps.reso.instaeclipse.utils.core.SafeReflect.methods(clazz)) {
            if (m.isBridge()) {
                XposedBridge.hookMethod(m, hook);
            }
        }
    }

    private void markHookedForEnabledFlags() {
        if (FeatureFlags.hideSuggestionsInFeed) FeatureStatusTracker.setHooked("HideSuggestionsInFeed");
        if (FeatureFlags.hideThreadsSuggestions) FeatureStatusTracker.setHooked("HideThreadsSuggestions");
        if (FeatureFlags.hideLikedPosts && hasLikedGetter != null) FeatureStatusTracker.setHooked("HideLikedPosts");
    }
}
