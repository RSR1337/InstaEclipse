package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class IgApiLookupCrashHook {

    private static final String CACHE_KEY = "IgApiLookupA09";
    private static final String CLASS_444 = "X.0Ah0";
    private static final String METHOD_444 = "A09";
    private static final String SUPER_444 = "X.08aX";

    private static final XC_MethodHook HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0) return;
            if (param.args[0] != null) return;
            if (!(param.method instanceof Method)) return;
            Class<?> ret = ((Method) param.method).getReturnType();
            if (ret.isPrimitive()) return;
            param.setResult(null);
        }
    };

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            if (hookClass(classLoader, CLASS_444)) {
                ModuleLog.line("(InstaEclipse | ApiLookupCrash): hooked " + CLASS_444 + "." + METHOD_444);
                return;
            }

            if (DexKitCache.isCacheValid()) {
                List<Method> cached = DexKitCache.loadMethods(CACHE_KEY, classLoader);
                if (cached != null && !cached.isEmpty()) {
                    for (Method method : cached) {
                        if (!isLookupMethod(method)) continue;
                        XposedBridge.hookMethod(method, HOOK);
                    }
                    ModuleLog.line("(InstaEclipse | ApiLookupCrash): hooked " + cached.size() + " cached method(s)");
                    return;
                }
            }

            if (bridge == null) {
                ModuleLog.line("(InstaEclipse | ApiLookupCrash): class not found and DexKit unavailable");
                return;
            }

            List<Method> hooked = new ArrayList<>();
            hookDexKitByClassName(bridge, classLoader, CLASS_444, StringMatchType.Equals, hooked);
            if (hooked.isEmpty()) {
                hookDexKitByFingerprint(bridge, classLoader, hooked);
            }

            if (hooked.isEmpty()) {
                ModuleLog.line("(InstaEclipse | ApiLookupCrash): method not found");
                return;
            }
            DexKitCache.saveMethods(CACHE_KEY, hooked);
            ModuleLog.line("(InstaEclipse | ApiLookupCrash): hooked " + hooked.size() + " DexKit method(s)");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | ApiLookupCrash): install failed: " + t.getMessage());
        }
    }

    private static boolean hookClass(ClassLoader classLoader, String className) {
        try {
            Class<?> cls = classLoader.loadClass(className);
            return hookLookupMethods(cls);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hookLookupMethods(Class<?> cls) {
        boolean hooked = false;
        for (Method method : cls.getDeclaredMethods()) {
            if (!isLookupMethod(method)) continue;
            XposedBridge.hookMethod(method, HOOK);
            hooked = true;
        }
        return hooked;
    }

    private static boolean isLookupMethod(Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || params[0] != String.class) return false;
        Class<?> ret = method.getReturnType();
        if (ret.isPrimitive() || ret == void.class || ret == String.class) return false;
        if (!METHOD_444.equals(method.getName())) return false;
        String owner = method.getDeclaringClass().getName();
        return CLASS_444.equals(owner) || looksLikeLookupClass(method.getDeclaringClass());
    }

    private static boolean looksLikeLookupClass(Class<?> cls) {
        boolean hasHashMapGetter = false;
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == HashMap.class) {
                hasHashMapGetter = true;
                break;
            }
        }
        if (!hasHashMapGetter) return false;
        Class<?> superCls = cls.getSuperclass();
        while (superCls != null && superCls != Object.class) {
            String name = superCls.getName();
            if (SUPER_444.equals(name) || name.endsWith("08aX")) return true;
            superCls = superCls.getSuperclass();
        }
        return false;
    }

    private static void hookDexKitByClassName(DexKitBridge bridge, ClassLoader classLoader,
                                             String marker, StringMatchType matchType,
                                             List<Method> hooked) {
        try {
            List<ClassData> classes = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().className(marker, matchType, false)));
            for (ClassData classData : classes) {
                try {
                    Class<?> cls = classLoader.loadClass(classData.getName());
                    for (Method method : cls.getDeclaredMethods()) {
                        if (!isLookupMethod(method)) continue;
                        XposedBridge.hookMethod(method, HOOK);
                        hooked.add(method);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookDexKitByFingerprint(DexKitBridge bridge, ClassLoader classLoader,
                                               List<Method> hooked) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .name(METHOD_444)
                            .paramCount(1)
                            .paramTypes("java.lang.String")));
            for (MethodData data : methods) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (!looksLikeLookupClass(method.getDeclaringClass())) continue;
                    if (!isLookupMethod(method)) continue;
                    XposedBridge.hookMethod(method, HOOK);
                    hooked.add(method);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
