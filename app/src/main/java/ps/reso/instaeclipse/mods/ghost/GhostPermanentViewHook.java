package ps.reso.instaeclipse.mods.ghost;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;


public class GhostPermanentViewHook {

    // We rewrite the view_mode to a non-ephemeral value so IG shows the media as permanent /
    // re-viewable. To let ViewOnceBadgeHook still restore IG's native once-vs-twice corner badge,
    // we encode the ORIGINAL type in the value we write: view-once -> PERMANENT_ONCE, view-twice
    // (replayable) -> PERMANENT_TWICE. IG treats any value that is not exactly "once"/"replayable"
    // as non-ephemeral, so both markers get the permanent behavior; ViewOnceBadgeHook maps them
    // back to "once"/"replayable" only at the DM render props so the correct badge is drawn.
    // IG's re-viewable/no-consume behavior requires the view_mode to be EXACTLY "permanent" (a
    // different marker makes view-twice consume again). So both types get "permanent"; the original
    // once-vs-twice type is carried to the render side via ORIGINAL_BY_KEY, keyed by any id/timestamp
    // long on the media model that the DM render props (0E4S) also expose.
    public static final String PERMANENT = "permanent";
    public static final java.util.Map<Long, String> ORIGINAL_BY_KEY =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("ViewOnceMedia", classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, buildHook());
                FeatureStatusTracker.setHooked("PermanentViewMode");
                return;
            }
        }

        try {
            List<MethodData> methods = new java.util.ArrayList<>();
            for (MethodData md : IgDex.findMethodsUsingAll(bridge, classLoader, "archived_media_timestamp", "view_mode")) {
                if (md.getParamTypeNames().size() == 1) methods.add(md);
            }
            methods.sort((a, b) -> Boolean.compare(!"unsafeParseFromJson".equals(a.getName()),
                    !"unsafeParseFromJson".equals(b.getName())));

            if (methods.isEmpty()) {
                ModuleLog.line("(IE|ViewOnceMedia) ❌ unsafeParseFromJson not found");
                return;
            }

            
            Method target = null;
            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (m.getReturnType() != void.class) {
                        target = m;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (target == null) {
                
                try {
                    target = methods.get(0).getMethodInstance(classLoader);
                } catch (Throwable t) {
                    ModuleLog.line("(IE|ViewOnceMedia) ❌ Could not resolve method: " + t);
                    return;
                }
            }

            ModuleLog.line("(IE|ViewOnceMedia) ✅ hooking "
                    + target.getDeclaringClass().getName() + "." + target.getName());

            DexKitCache.saveMethod("ViewOnceMedia", target);
            XposedBridge.hookMethod(target, buildHook());

            FeatureStatusTracker.setHooked("PermanentViewMode");
            ModuleLog.line("(IE|ViewOnceMedia) ✅ hooked");

        } catch (Throwable t) {
            ModuleLog.line("(IE|ViewOnceMedia) ❌ " + t);
        }
    }

    private static XC_MethodHook buildHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (!FeatureFlags.permanentViewMode) return;
                Object result = param.getResult();
                if (result == null) return;

                
                int seenCount = 0;
                java.util.List<Long> keys = new java.util.ArrayList<>();
                Class<?> cls = result.getClass();
                while (cls != null && cls != Object.class) {
                    for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(cls)) {
                        if (f.getType() != int.class) continue;
                        f.setAccessible(true);
                        try { seenCount = f.getInt(result); } catch (Throwable ignored) {}
                    }
                    cls = cls.getSuperclass();
                }
                collectIdKeys(result, keys, 0,
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

                // Pass 2: rewrite the view_mode to "permanent" (both types) so IG shows it as
                // re-viewable media; stash the original once/twice type by each key for the render side.
                cls = result.getClass();
                while (cls != null && cls != Object.class) {
                    for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(cls)) {
                        if (f.getType() != String.class) continue;
                        f.setAccessible(true);
                        try {
                            String val = (String) f.get(result);
                            String original = null;
                            if ("once".equals(val)) {
                                if (seenCount >= 1) return; // already viewed — URL gone
                                original = "once";
                            } else if ("replayable".equals(val) || "allow_replay".equals(val)) {
                                if (seenCount >= 2) return; // replayable allows 2 views
                                original = "replayable";
                            }
                            if (original != null) {
                                for (Long k : keys) ORIGINAL_BY_KEY.put(k, original);
                                ModuleLog.line("(IE|ViewOnceMedia) mode=" + original + " keys=" + keys);
                                f.set(result, PERMANENT);
                            }
                        } catch (Throwable ignored) {}
                    }
                    cls = cls.getSuperclass();
                }
            }
        };
    }

    /**
     * Walks the parsed model's object graph (depth-limited) collecting id/timestamp longs and
     * numeric-string ids (e.g. the nested Media / ExtendedImageUrl media id). These are the keys
     * the render side (ViewOnceBadgeHook) also derives, letting us join the original view-once type.
     */
    private static void collectIdKeys(Object obj, java.util.List<Long> out, int depth, java.util.Set<Object> seen) {
        if (obj == null || depth > 4 || out.size() > 60 || !seen.add(obj)) return;
        if (obj instanceof Object[]) {
            for (Object e : (Object[]) obj) collectIdKeys(e, out, depth + 1, seen);
            return;
        }
        String cn = obj.getClass().getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.")) return;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(c)) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Class<?> t = f.getType();
                try {
                    if (t == long.class) {
                        long v = f.getLong(obj);
                        if (v != 0 && v != Long.MAX_VALUE && !out.contains(v)) out.add(v);
                    } else if (t == String.class) {
                        String s = (String) f.get(obj);
                        if (s != null && s.length() >= 8 && s.length() <= 22 && s.chars().allMatch(Character::isDigit)) {
                            try { long v = Long.parseLong(s); if (!out.contains(v)) out.add(v); } catch (NumberFormatException ignored) {}
                        }
                    } else if (!t.isPrimitive()) {
                        collectIdKeys(f.get(obj), out, depth + 1, seen);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}
