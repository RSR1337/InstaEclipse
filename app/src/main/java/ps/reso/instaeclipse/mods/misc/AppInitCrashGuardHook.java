package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class AppInitCrashGuardHook {

    private static final String CACHE_KEY_E69 = "AppInit_E69_Methods";
    private static final String CACHE_KEY_GKO = "AppInit_GKo_Methods";
    private static final String CACHE_KEY_PLUGIN = "AppInit_Plugin_Methods";
    private static final String CACHE_KEY_WORKER = "AppInit_Worker_Methods";

    private static final String CLASS_NEED_INIT_444 = "X.01qA";
    private static final String CLASS_PLUGIN_INIT_444 = "X.04lh";
    private static final String CLASS_PLUGIN_HELPER_444 = "X.04lm";
    private static final String CLASS_WORKER_TASK_444 = "X.01gf";
    private static final String CLASS_THREAD_WRAPPER_444 = "X.01gh";
    private static final String[] KNOWN_GKO_CLASSES_444 = {
            "X.04sj", "X.04tl", "X.05rn", "X.0Mhh"
    };

    private static final Set<Method> HOOKED_METHODS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static void hookOnce(Method m, XC_MethodHook hook) {
        if (HOOKED_METHODS.add(m)) {
            XposedBridge.hookMethod(m, hook);
        }
    }

    private static final XC_MethodHook E69_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                Throwable t = param.getThrowable();
                String taskName = "unknown";
                try {
                    Object nameField = XposedHelpers.getObjectField(param.thisObject, "A01");
                    if (nameField instanceof String) {
                        taskName = (String) nameField;
                    }
                } catch (Throwable ignored) {}

                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed crash during AppInit task [" + taskName + "]: " + t.getMessage());
                param.setThrowable(null);
                param.setResult(null);

                try {
                    Object initObj = XposedHelpers.callMethod(param.thisObject, "A00");
                    if (initObj != null) {
                        XposedHelpers.setBooleanField(initObj, "A00", true);
                    }
                } catch (Throwable ignored) {}
            }
        }
    };

    private static final XC_MethodHook GKO_NULL_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                param.setThrowable(null);
                param.setResult(Collections.emptyList());
            } else if (param.getResult() == null) {
                param.setResult(Collections.emptyList());
            }
        }
    };

    private static final XC_MethodHook PLUGIN_INIT_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                Throwable t = param.getThrowable();
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed error in PluginInitializer: " + t.getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook WORKER_RUNNABLE_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                Throwable t = param.getThrowable();
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed crash in AppInit worker runnable: " + t.getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            int e69Count = 0;
            int gkoCount = 0;
            int pluginCount = 0;
            int workerCount = 0;

            if (hookDirectClasses(classLoader)) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Hooked AppInit guard via direct fast-path");
            }

            if (DexKitCache.isCacheValid()) {
                List<Method> cachedE69 = DexKitCache.loadMethods(CACHE_KEY_E69, classLoader);
                if (cachedE69 != null && !cachedE69.isEmpty()) {
                    for (Method m : cachedE69) {
                        hookOnce(m, E69_GUARD_HOOK);
                        e69Count++;
                    }
                }
                List<Method> cachedGKo = DexKitCache.loadMethods(CACHE_KEY_GKO, classLoader);
                if (cachedGKo != null && !cachedGKo.isEmpty()) {
                    for (Method m : cachedGKo) {
                        hookOnce(m, GKO_NULL_GUARD_HOOK);
                        gkoCount++;
                    }
                }
                List<Method> cachedPlugin = DexKitCache.loadMethods(CACHE_KEY_PLUGIN, classLoader);
                if (cachedPlugin != null && !cachedPlugin.isEmpty()) {
                    for (Method m : cachedPlugin) {
                        hookOnce(m, PLUGIN_INIT_GUARD_HOOK);
                        pluginCount++;
                    }
                }
                List<Method> cachedWorker = DexKitCache.loadMethods(CACHE_KEY_WORKER, classLoader);
                if (cachedWorker != null && !cachedWorker.isEmpty()) {
                    for (Method m : cachedWorker) {
                        hookOnce(m, WORKER_RUNNABLE_GUARD_HOOK);
                        workerCount++;
                    }
                }
                if (e69Count > 0 || gkoCount > 0 || pluginCount > 0 || workerCount > 0) {
                    ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Hooked cached AppInit methods (E69=" + e69Count + ", GKo=" + gkoCount + ", Plugin=" + pluginCount + ", Worker=" + workerCount + ")");
                    return;
                }
            }

            if (bridge == null) {
                return;
            }

            List<Method> discoveredE69 = new ArrayList<>();
            List<Method> discoveredGKo = new ArrayList<>();
            List<Method> discoveredPlugin = new ArrayList<>();
            List<Method> discoveredWorker = new ArrayList<>();

            try {
                List<MethodData> e69Methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .name("E69")
                                .paramCount(0)
                                .returnType("void"))
                );
                for (MethodData md : e69Methods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        hookOnce(m, E69_GUARD_HOOK);
                        discoveredE69.add(m);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            try {
                List<MethodData> gkoMethods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .name("GKo")
                                .paramCount(0)
                                .returnType("java.util.List"))
                );
                for (MethodData md : gkoMethods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        hookOnce(m, GKO_NULL_GUARD_HOOK);
                        discoveredGKo.add(m);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            try {
                List<ClassData> pluginInitClasses = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings("PluginInitializer"))
                );
                for (ClassData cd : pluginInitClasses) {
                    try {
                        Class<?> cls = classLoader.loadClass(cd.getName());
                        for (Method m : cls.getDeclaredMethods()) {
                            if (m.getParameterCount() == 0 && m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers())) {
                                hookOnce(m, PLUGIN_INIT_GUARD_HOOK);
                                discoveredPlugin.add(m);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            if (!discoveredE69.isEmpty()) {
                DexKitCache.saveMethods(CACHE_KEY_E69, discoveredE69);
            }
            if (!discoveredGKo.isEmpty()) {
                DexKitCache.saveMethods(CACHE_KEY_GKO, discoveredGKo);
            }
            if (!discoveredPlugin.isEmpty()) {
                DexKitCache.saveMethods(CACHE_KEY_PLUGIN, discoveredPlugin);
            }

            ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Dynamically hooked AppInit methods (E69=" + discoveredE69.size() + ", GKo=" + discoveredGKo.size() + ", Plugin=" + discoveredPlugin.size() + ")");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | AppInitGuard): ❌ Installation failed: " + t.getMessage());
        }
    }

    private static boolean hookDirectClasses(ClassLoader classLoader) {
        boolean hookedAny = false;

        try {
            Class<?> needInitCls = classLoader.loadClass(CLASS_NEED_INIT_444);
            for (Method m : needInitCls.getDeclaredMethods()) {
                if ("E69".equals(m.getName()) && m.getParameterCount() == 0) {
                    XposedBridge.hookMethod(m, E69_GUARD_HOOK);
                    hookedAny = true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> workerCls = classLoader.loadClass(CLASS_WORKER_TASK_444);
            for (Method m : workerCls.getDeclaredMethods()) {
                if ("run".equals(m.getName()) && m.getParameterCount() == 0) {
                    XposedBridge.hookMethod(m, WORKER_RUNNABLE_GUARD_HOOK);
                    hookedAny = true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> threadWrapperCls = classLoader.loadClass(CLASS_THREAD_WRAPPER_444);
            for (Method m : threadWrapperCls.getDeclaredMethods()) {
                if ("run".equals(m.getName()) && m.getParameterCount() == 0) {
                    XposedBridge.hookMethod(m, WORKER_RUNNABLE_GUARD_HOOK);
                    hookedAny = true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> pluginInitCls = classLoader.loadClass(CLASS_PLUGIN_INIT_444);
            for (Method m : pluginInitCls.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    XposedBridge.hookMethod(m, PLUGIN_INIT_GUARD_HOOK);
                    hookedAny = true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> pluginHelperCls = classLoader.loadClass(CLASS_PLUGIN_HELPER_444);
            for (Method m : pluginHelperCls.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    XposedBridge.hookMethod(m, PLUGIN_INIT_GUARD_HOOK);
                    hookedAny = true;
                }
            }
        } catch (Throwable ignored) {}

        for (String className : KNOWN_GKO_CLASSES_444) {
            try {
                Class<?> gkoCls = classLoader.loadClass(className);
                for (Method m : gkoCls.getDeclaredMethods()) {
                    if ("GKo".equals(m.getName()) && m.getParameterCount() == 0) {
                        hookOnce(m, GKO_NULL_GUARD_HOOK);
                        hookedAny = true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return hookedAny;
    }
}
