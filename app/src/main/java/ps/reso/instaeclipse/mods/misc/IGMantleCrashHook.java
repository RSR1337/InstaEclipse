package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class IGMantleCrashHook {

    private static final String CACHE_KEY = "IGMantleConfig";
    private static final String CLASS_NAME = "com.facebook.mantle.ig.IGMantle";
    private static final String METHOD_NAME = "runMantleWithConfigStr";

    private static final XC_MethodHook HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args == null || param.args.length == 0) return;
            if (param.args[0] != null) return;
            Object empty = emptyResult(param.method);
            if (empty == SKIP) return;
            param.setResult(empty);
        }
    };

    private static final Object SKIP = new Object();

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            if (hookByClassName(classLoader, CLASS_NAME)) {
                ModuleLog.line("(InstaEclipse | MantleCrash): hooked " + CLASS_NAME + "." + METHOD_NAME);
                return;
            }

            if (DexKitCache.isCacheValid()) {
                List<Method> cached = DexKitCache.loadMethods(CACHE_KEY, classLoader);
                if (cached != null && !cached.isEmpty()) {
                    for (Method method : cached) {
                        if (!isTargetMethod(method)) continue;
                        XposedBridge.hookMethod(method, HOOK);
                    }
                    ModuleLog.line("(InstaEclipse | MantleCrash): hooked cached method(s)");
                    return;
                }
            }

            if (bridge == null) {
                ModuleLog.line("(InstaEclipse | MantleCrash): class not found and DexKit unavailable");
                return;
            }

            List<Method> hooked = new ArrayList<>();
            hookDexKitExact(bridge, classLoader, hooked);
            if (hooked.isEmpty()) hookDexKitByClassString(bridge, classLoader, hooked);

            if (hooked.isEmpty()) {
                ModuleLog.line("(InstaEclipse | MantleCrash): method not found");
                return;
            }
            DexKitCache.saveMethods(CACHE_KEY, hooked);
            ModuleLog.line("(InstaEclipse | MantleCrash): hooked " + hooked.size() + " DexKit method(s)");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | MantleCrash): install failed: " + t.getMessage());
        }
    }

    private static boolean hookByClassName(ClassLoader classLoader, String className) {
        try {
            Class<?> cls = classLoader.loadClass(className);
            boolean hooked = false;
            for (Method method : cls.getDeclaredMethods()) {
                if (!isTargetMethod(method)) continue;
                XposedBridge.hookMethod(method, HOOK);
                hooked = true;
            }
            return hooked;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTargetMethod(Method method) {
        if (!METHOD_NAME.equals(method.getName())) return false;
        if (method.getParameterTypes().length < 1) return false;
        String owner = method.getDeclaringClass().getName();
        return CLASS_NAME.equals(owner) || owner.contains("mantle");
    }

    private static void hookDexKitExact(DexKitBridge bridge, ClassLoader classLoader, List<Method> hooked) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().name(METHOD_NAME)));
            for (MethodData data : methods) {
                try {
                    Method method = data.getMethodInstance(classLoader);
                    if (!isTargetMethod(method)) continue;
                    XposedBridge.hookMethod(method, HOOK);
                    hooked.add(method);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookDexKitByClassString(DexKitBridge bridge, ClassLoader classLoader, List<Method> hooked) {
        try {
            List<ClassData> classes = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings(METHOD_NAME)));
            for (ClassData classData : classes) {
                try {
                    Class<?> cls = classLoader.loadClass(classData.getName());
                    for (Method method : cls.getDeclaredMethods()) {
                        if (!isTargetMethod(method)) continue;
                        XposedBridge.hookMethod(method, HOOK);
                        hooked.add(method);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object emptyResult(Member member) {
        if (!(member instanceof Method)) return SKIP;
        Class<?> ret = ((Method) member).getReturnType();
        if (ret == void.class || ret == Void.class) return null;
        if (ret.isPrimitive()) return SKIP;
        if (Map.class.isAssignableFrom(ret)) {
            Object immutable = emptyImmutableMap(ret);
            if (immutable != null && ret.isAssignableFrom(immutable.getClass())) return immutable;
            if (ret.isAssignableFrom(HashMap.class)) return new HashMap<Object, Object>();
            return SKIP;
        }
        return null;
    }

    private static Object emptyImmutableMap(Class<?> returnType) {
        try {
            ClassLoader loader = returnType.getClassLoader();
            Class<?> immutable = loader != null
                    ? loader.loadClass("com.google.common.collect.ImmutableMap")
                    : Class.forName("com.google.common.collect.ImmutableMap");
            if (!returnType.isAssignableFrom(immutable)) return null;
            return XposedHelpers.callStaticMethod(immutable, "of");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
