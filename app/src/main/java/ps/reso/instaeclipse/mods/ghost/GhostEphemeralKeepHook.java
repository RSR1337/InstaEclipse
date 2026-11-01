package ps.reso.instaeclipse.mods.ghost;

import ps.reso.instaeclipse.utils.core.DexKitQueryCache;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;


public class GhostEphemeralKeepHook {

    private static final String VANISH_CACHE_KEY = "Ephemeral_vanish_v2";
    private static final String EXPIRY_CACHE_KEY = "Ephemeral_expiry_v2";

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        hookVanishLocalDelete(bridge, classLoader);
        hookServerPing(bridge);
        hookExpiryParser(bridge, classLoader);
    }

    
    private void hookVanishLocalDelete(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (FeatureFlags.keepEphemeralMessages) param.setResult(null);
            }
        };

        // IG 447+ (verified 447/451/452): the removal itself lives in two methods of the thread
        // model (X.01eV on 452, X.04r2 on 451, X.01gH on 447), called on chat exit, by the
        // per-thread expiry timer and when a thread is loaded from disk:
        //   (Z)Z  remove expired disappearing messages — unique string below
        //   ()Z   remove vanish-mode ("is_shh_mode") messages — the class's only ()boolean
        // Both return "anything removed"; callers only commit on true, so false = keep all.
        XC_MethodHook keep = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (FeatureFlags.keepEphemeralMessages) param.setResult(false);
            }
        };
        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("Ephemeral_vanish_v2", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, keep);
                FeatureStatusTracker.setHooked("KeepEphemeralMessages");
                return;
            }
        }
        try {
            List<Method> found = new java.util.ArrayList<>();
            for (MethodData md : DexKitQueryCache.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("Unexpected null timestamp for disappearing message, removed message")
                            .paramTypes("boolean")
                            .returnType("boolean")))) {
                Method expired = md.getMethodInstance(classLoader);
                found.add(expired);
                Method shh = null;
                int candidates = 0;
                for (Method m : ps.reso.instaeclipse.utils.core.SafeReflect.methods(expired.getDeclaringClass())) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class
                            && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) { shh = m; candidates++; }
                }
                if (candidates == 1) found.add(shh);
                else ModuleLog.line("(IE|Ephemeral) ⚠️ vanish-mode purge ambiguous (" + candidates + " ()Z methods)");
                break;
            }
            if (!found.isEmpty()) {
                for (Method m : found) {
                    XposedBridge.hookMethod(m, keep);
                    ModuleLog.line("(IE|Ephemeral) ✅ local purge hook → "
                            + m.getDeclaringClass().getName() + "." + m.getName());
                }
                DexKitCache.saveMethods("Ephemeral_vanish_v2", found);
                FeatureStatusTracker.setHooked("KeepEphemeralMessages");
                return;
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|Ephemeral) ⚠️ local purge lookup: " + t.getMessage());
        }

        // Legacy (<=447): the orchestrating (DirectThreadKey, boolean) method.
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod(VANISH_CACHE_KEY, classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                FeatureStatusTracker.setHooked("KeepEphemeralMessages");
                return;
            }
        }

        try {
            List<MethodData> methods = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("igThreadIgid")
                            .paramTypes("com.instagram.model.direct.DirectThreadKey", "boolean")
                            .returnType("void")));

            if (methods.isEmpty()) {
                methods = IgDex.findMethod(bridge, FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramTypes("com.instagram.model.direct.DirectThreadKey", "boolean")
                                .returnType("void")
                                .addInvoke(MethodMatcher.create().usingStrings("igThreadIgid"))));
            }

            if (methods.isEmpty()) {
                methods = IgDex.findMethod(bridge, FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .usingStrings("igThreadIgid")
                                .paramCount(2)
                                .returnType("void")));
            }

            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    DexKitCache.saveMethod(VANISH_CACHE_KEY, m);
                    XposedBridge.hookMethod(m, hook);
                    ModuleLog.line("(IE|Ephemeral) ✅ vanish-local-delete hook → "
                            + md.getClassName() + "." + md.getName());
                    FeatureStatusTracker.setHooked("KeepEphemeralMessages");
                    return;
                } catch (Throwable ignored) {}
            }
            ModuleLog.line("(IE|Ephemeral) ❌ vanish-local-delete method not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Ephemeral) ❌ hookVanishLocalDelete: " + t.getMessage());
        }
    }

    
    private void hookServerPing(DexKitBridge bridge) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (FeatureFlags.keepEphemeralMessages) param.setResult(null);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("Ephemeral_ping", Module.hostClassLoader);
            if (cached != null) { XposedBridge.hookMethod(cached, hook); return; }
        }

        try {
            List<MethodData> methods = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("mark_ephemeral_item_ranges_viewed")));

            for (MethodData md : methods) {
                Method method;
                try {
                    method = md.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue;
                }
                if (method.getReturnType() != void.class) continue;

                DexKitCache.saveMethod("Ephemeral_ping", method);
                XposedBridge.hookMethod(method, hook);
                ModuleLog.line("(IE|Ephemeral) ✅ server-ping hook → "
                        + md.getClassName() + "." + md.getName());
                return;
            }
            ModuleLog.line("(IE|Ephemeral) ❌ server-ping method not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Ephemeral) ❌ hookServerPing: " + t.getMessage());
        }
    }

    
    private void hookExpiryParser(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                if (!FeatureFlags.keepEphemeralMessages) return;
                Object result = param.getResult();
                if (result != null) clearExpiryTimestamp(result);
            }
        };

        // Key renamed from "Ephemeral_expiry": that list included the static per-FIELD parser
        // (X.00zF.A01 on 452, called once for every JSON field of every DM message — ~5k calls
        // per 40 s of feed scrolling). Hooking such a hot method made ART's JIT compile its
        // hook backup, which crashes Instagram (SIGSEGV in DexCache::SetResolvedType) on
        // Vector/Android 13. It also never had an object to clean (static, void).
        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods(EXPIRY_CACHE_KEY, classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, hook);
                return;
            }
        }

        try {
            List<MethodData> methods = IgDex.findMethodsUsingAll(bridge, classLoader, "message_expiration_timestamp_ms");
            java.util.Map<String, MethodData> parsers = new java.util.LinkedHashMap<>();
            java.util.Set<String> fieldParserOwners = new java.util.LinkedHashSet<>();
            for (MethodData md : methods) {
                List<String> params = md.getParamTypeNames();
                if ("unsafeParseFromJson".equals(md.getName())) {
                    parsers.put(md.getDescriptor(), md);
                } else if ("void".equals(md.getReturnTypeName()) && params.size() == 3
                        && "java.lang.String".equals(params.get(2))) {
                    fieldParserOwners.add(md.getClassName());
                }
            }
            for (String owner : fieldParserOwners) {
                for (MethodData md : IgDex.findMethod(bridge, FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .declaredClass(owner)
                                .name("unsafeParseFromJson")))) {
                    parsers.put(md.getDescriptor(), md);
                }
            }
            if (!parsers.isEmpty()) methods = new java.util.ArrayList<>(parsers.values());

            // Hook only whole-object parse entry points (one call per parsed model, returning
            // it): the parser classes' parseFromJsonParser/unsafeParseFromJson, plus any anchor
            // method that returns an object. Never the static void per-field helpers.
            java.util.Set<Method> targets = new java.util.LinkedHashSet<>();
            java.util.Set<Class<?>> owners = new java.util.LinkedHashSet<>();
            for (MethodData md : methods) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    owners.add(m.getDeclaringClass());
                    if (m.getReturnType() != void.class && !m.getReturnType().isPrimitive()) targets.add(m);
                } catch (Throwable ignored) {}
            }
            for (Class<?> c : owners) {
                for (Method m : ps.reso.instaeclipse.utils.core.SafeReflect.methods(c)) {
                    String n = m.getName();
                    if ((n.equals("parseFromJsonParser") || n.equals("unsafeParseFromJson"))
                            && m.getReturnType() != void.class) targets.add(m);
                }
            }
            List<Method> hooked = new java.util.ArrayList<>();
            for (Method m : targets) {
                try {
                    XposedBridge.hookMethod(m, hook);
                    hooked.add(m);
                    ModuleLog.line("(IE|Ephemeral) ✅ expiry-parser hook → "
                            + m.getDeclaringClass().getName() + "." + m.getName());
                } catch (Throwable ignored) {}
            }
            if (hooked.isEmpty()) {
                ModuleLog.line("(IE|Ephemeral) ❌ no expiry-parser methods hooked");
            } else {
                DexKitCache.saveMethods(EXPIRY_CACHE_KEY, hooked);
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|Ephemeral) ❌ hookExpiryParser: " + t.getMessage());
        }
    }

    private static void clearExpiryTimestamp(Object obj) {
        if (obj == null) return;
        String owner = obj.getClass().getName();
        if (!owner.startsWith("X.") && !owner.startsWith("com.instagram.")) return;
        long now = System.currentTimeMillis();
        long year2100 = 4_102_444_800_000L;
        try {
            for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(obj.getClass())) {
                if (f.getType() != long.class) continue;
                f.setAccessible(true);
                long val = f.getLong(obj);
                if (val > now && val < year2100) {
                    f.setLong(obj, 0L);
                }
            }
        } catch (Throwable ignored) {}
    }
}
