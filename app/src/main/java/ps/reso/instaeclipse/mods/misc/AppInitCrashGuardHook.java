package ps.reso.instaeclipse.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

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
    private static final String CACHE_KEY_PANDO = "AppInit_Pando_Methods";

    private static final String CLASS_NEED_INIT_444 = "X.01qA";
    private static final String CLASS_PLUGIN_INIT_444 = "X.04lh";
    private static final String CLASS_PLUGIN_HELPER_444 = "X.04lm";
    private static final String CLASS_WORKER_TASK_444 = "X.01gf";
    private static final String CLASS_THREAD_WRAPPER_444 = "X.01gh";
    private static final String[] KNOWN_GKO_CLASSES_444 = {
            "X.04sj", "X.04tl", "X.05rn", "X.0Mhh"
    };

    private static final String CLASS_WORKER_TASK_445 = "X.1gr";
    private static final String CLASS_THREAD_WRAPPER_445 = "X.1gt";
    private static final String CLASS_ORDERED_TASK_445 = "X.1qz";
    private static final String CLASS_FUTURE_CALLABLE_445 = "X.LCF";
    private static final String CLASS_PANDO_INIT_445 = "X.6jl";
    private static final String CLASS_GRAPHQL_FACTORY_445 = "X.6ix";
    private static final String CLASS_REPLAY_RECEIVER = "com.instagram.process.asyncinit.IgAppInitReplayBroadcastReceiver";
    private static final String CLASS_LAUNCHER_SYNC_RECEIVER = "com.instagram.api.realtimepeak.LauncherSyncBootReceiver";
    private static final String CLASS_FBNS_INIT_RECEIVER = "com.instagram.push.FbnsInitBroadcastReceiver";
    private static final String CLASS_REPLAY_RUNNABLE_445 = "X.0CH";
    private static final String CLASS_HTTP_URL_BUILDER_445 = "X.3l5";

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
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed error in PluginInitializer: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook WORKER_RUNNABLE_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed crash in AppInit worker runnable: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook CALLABLE_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed crash in AppInit callable: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook PANDO_INIT_GUARD_HOOK = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed crash in PandoGraphQL init: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook GRAPHQL_STRING_NULL_GUARD = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable() && param.getThrowable() instanceof NullPointerException) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed NPE in GraphQL factory: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook INTENT_ACTION_NULL_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                for (Object arg : param.args) {
                    if (arg instanceof android.content.Intent) {
                        String action = ((android.content.Intent) arg).getAction();
                        if (action == null) {
                            ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Dropped broadcast with null action in "
                                    + param.method.getDeclaringClass().getSimpleName());
                            param.setResult(null);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                Throwable t = param.getThrowable();
                if (t instanceof NullPointerException || t instanceof SecurityException) {
                    ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed " + t.getClass().getSimpleName()
                            + " in async-init receiver: " + t.getMessage());
                    param.setThrowable(null);
                    param.setResult(null);
                }
            }
        }
    };

    private static final XC_MethodHook REPLAY_RUNNABLE_GUARD = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) {
                Throwable t = param.getThrowable();
                if (t instanceof SecurityException || t instanceof NullPointerException) {
                    ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed " + t.getClass().getSimpleName()
                            + " in async-init replay runnable: " + t.getMessage());
                    param.setThrowable(null);
                    param.setResult(null);
                }
            }
        }
    };

    private static final XC_MethodHook HTTP_HEADER_NULL_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length >= 1 && param.args[0] == null) {
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook URL_ENCODE_NULL_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length >= 1 && param.args[0] == null) {
                param.args[0] = "";
            }
        }
    };

    private static final XC_MethodHook SCHEDULER_TASK_GUARD = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable() && param.getThrowable() instanceof NullPointerException) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed NPE in scheduled HTTP task: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook NULL_STRING_FALSE_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length >= 1 && param.args[0] == null) {
                param.setResult(false);
            }
        }
    };

    private static final XC_MethodHook PANDO_SERVICE_NULL_GUARD = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable() && param.getThrowable() instanceof NullPointerException) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Suppressed NPE in Pando service: " + param.getThrowable().getMessage());
                param.setThrowable(null);
                param.setResult(null);
            }
        }
    };

    private static final XC_MethodHook ANALYTICS_NULL_KEY_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length >= 1 && param.args[0] == null) {
                param.args[0] = "ie_null";
            }
            if (param.args != null && param.args.length >= 2 && param.args[1] == null
                    && param.method instanceof Method
                    && ((Method) param.method).getParameterTypes()[1] == String.class) {
                param.args[1] = "";
            }
        }
    };

    private static final XC_MethodHook PREFS_NULL_KEY_GUARD = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length >= 1 && param.args[0] == null) {
                String name = param.method.getName();
                if ("getBoolean".equals(name)) {
                    param.setResult(param.args.length > 1 ? param.args[1] : Boolean.FALSE);
                } else if ("getString".equals(name)) {
                    param.setResult(param.args.length > 1 ? param.args[1] : null);
                } else if ("getInt".equals(name)) {
                    param.setResult(param.args.length > 1 ? param.args[1] : 0);
                } else if ("getLong".equals(name)) {
                    param.setResult(param.args.length > 1 ? param.args[1] : 0L);
                } else if ("contains".equals(name)) {
                    param.setResult(false);
                } else {
                    param.setResult(param.args.length > 1 ? param.args[1] : null);
                }
            }
        }
    };

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            int e69Count = 0;
            int gkoCount = 0;
            int pluginCount = 0;
            int workerCount = 0;
            int pandoCount = 0;

            boolean direct = hookDirectClasses(classLoader);

            if (DexKitCache.isCacheValid()) {
                List<Method> cachedE69 = DexKitCache.loadMethods(CACHE_KEY_E69, classLoader);
                if (cachedE69 != null) {
                    for (Method m : cachedE69) {
                        hookOnce(m, E69_GUARD_HOOK);
                        e69Count++;
                    }
                }
                List<Method> cachedGKo = DexKitCache.loadMethods(CACHE_KEY_GKO, classLoader);
                if (cachedGKo != null) {
                    for (Method m : cachedGKo) {
                        hookOnce(m, GKO_NULL_GUARD_HOOK);
                        gkoCount++;
                    }
                }
                List<Method> cachedPlugin = DexKitCache.loadMethods(CACHE_KEY_PLUGIN, classLoader);
                if (cachedPlugin != null) {
                    for (Method m : cachedPlugin) {
                        hookOnce(m, PLUGIN_INIT_GUARD_HOOK);
                        pluginCount++;
                    }
                }
                List<Method> cachedWorker = DexKitCache.loadMethods(CACHE_KEY_WORKER, classLoader);
                if (cachedWorker != null) {
                    for (Method m : cachedWorker) {
                        hookOnce(m, WORKER_RUNNABLE_GUARD_HOOK);
                        workerCount++;
                    }
                }
                List<Method> cachedPando = DexKitCache.loadMethods(CACHE_KEY_PANDO, classLoader);
                if (cachedPando != null) {
                    for (Method m : cachedPando) {
                        hookOnce(m, PANDO_INIT_GUARD_HOOK);
                        pandoCount++;
                    }
                }
            }

            if (direct) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Hooked AppInit methods (direct=true"
                        + ", E69=" + e69Count + ", GKo=" + gkoCount + ", Plugin=" + pluginCount
                        + ", Worker=" + workerCount + ", Pando=" + pandoCount + ")");
                return;
            }

            if (e69Count > 0 || workerCount > 0 || pandoCount > 0) {
                ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Hooked cached AppInit methods (E69=" + e69Count
                        + ", GKo=" + gkoCount + ", Plugin=" + pluginCount
                        + ", Worker=" + workerCount + ", Pando=" + pandoCount + ")");
                return;
            }

            if (bridge == null) {
                return;
            }

            List<Method> discoveredPlugin = new ArrayList<>();
            List<Method> discoveredPando = new ArrayList<>();

            try {
                List<ClassData> pluginInitClasses = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings("PluginInitializer")));
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

            try {
                List<ClassData> pandoClasses = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings("PandoGraphQLInitializer")));
                for (ClassData cd : pandoClasses) {
                    try {
                        Class<?> cls = classLoader.loadClass(cd.getName());
                        for (Method m : cls.getDeclaredMethods()) {
                            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class) {
                                hookOnce(m, PANDO_INIT_GUARD_HOOK);
                                discoveredPando.add(m);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            if (!discoveredPlugin.isEmpty()) {
                DexKitCache.saveMethods(CACHE_KEY_PLUGIN, discoveredPlugin);
            }
            if (!discoveredPando.isEmpty()) {
                DexKitCache.saveMethods(CACHE_KEY_PANDO, discoveredPando);
            }

            ModuleLog.line("(InstaEclipse | AppInitGuard): ✅ Dynamically hooked AppInit methods (Plugin="
                    + discoveredPlugin.size() + ", Pando=" + discoveredPando.size() + ")");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | AppInitGuard): ❌ Installation failed: " + t.getMessage());
        }
    }

    private static boolean hookDirectClasses(ClassLoader classLoader) {
        boolean hookedAny = false;

        hookedAny |= hookRunMethods(classLoader, CLASS_NEED_INIT_444, "E69", 0, E69_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_WORKER_TASK_444, "run", 0, WORKER_RUNNABLE_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_THREAD_WRAPPER_444, "run", 0, WORKER_RUNNABLE_GUARD_HOOK);
        hookedAny |= hookAllZeroArg(classLoader, CLASS_PLUGIN_INIT_444, PLUGIN_INIT_GUARD_HOOK);
        hookedAny |= hookAllZeroArg(classLoader, CLASS_PLUGIN_HELPER_444, PLUGIN_INIT_GUARD_HOOK);
        for (String className : KNOWN_GKO_CLASSES_444) {
            hookedAny |= hookRunMethods(classLoader, className, "GKo", 0, GKO_NULL_GUARD_HOOK);
        }

        hookedAny |= hookRunMethods(classLoader, CLASS_WORKER_TASK_445, "run", 0, WORKER_RUNNABLE_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_THREAD_WRAPPER_445, "run", 0, WORKER_RUNNABLE_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_ORDERED_TASK_445, "E87", 0, WORKER_RUNNABLE_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_FUTURE_CALLABLE_445, "call", 0, CALLABLE_GUARD_HOOK);
        hookedAny |= hookRunMethods(classLoader, CLASS_PANDO_INIT_445, "GC7", 3, PANDO_INIT_GUARD_HOOK);
        hookedAny |= hookNamed(classLoader, CLASS_GRAPHQL_FACTORY_445, "A06", GRAPHQL_STRING_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, CLASS_REPLAY_RECEIVER, "A00", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, CLASS_REPLAY_RECEIVER, "doReceive", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, CLASS_LAUNCHER_SYNC_RECEIVER, "onReceive", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, CLASS_FBNS_INIT_RECEIVER, "onReceive", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, "X.1Nl", "A01", NULL_STRING_FALSE_GUARD);
        hookedAny |= hookNamed(classLoader, "X.Aay", "processOnReceive", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, "X.Aay", "onReceive", INTENT_ACTION_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, "X.6hA", "BBq", PANDO_SERVICE_NULL_GUARD);
        hookedAny |= hookNamed(classLoader, "X.6hA", "BBn", PANDO_SERVICE_NULL_GUARD);
        hookedAny |= hookRunMethods(classLoader, CLASS_REPLAY_RUNNABLE_445, "run", 0, REPLAY_RUNNABLE_GUARD);
        hookedAny |= hookHttpHeaderGuards(classLoader);
        hookedAny |= hookNamed(classLoader, CLASS_HTTP_URL_BUILDER_445, "A00", SCHEDULER_TASK_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "getBoolean", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "getString", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "getInt", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "getLong", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "getFloat", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.3Qe", "contains", PREFS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "com.facebook.graphql.calls.GraphQlCallInput", "put", ANALYTICS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.2mu", "AQY", ANALYTICS_NULL_KEY_GUARD);
        hookedAny |= hookNamed(classLoader, "X.0to", "A0q", ANALYTICS_NULL_KEY_GUARD);

        try {
            Method encode = java.net.URLEncoder.class.getDeclaredMethod("encode", String.class, String.class);
            hookOnce(encode, URL_ENCODE_NULL_GUARD);
            hookedAny = true;
        } catch (Throwable ignored) {}
        try {
            Method encode = java.net.URLEncoder.class.getDeclaredMethod("encode", String.class);
            hookOnce(encode, URL_ENCODE_NULL_GUARD);
            hookedAny = true;
        } catch (Throwable ignored) {}

        return hookedAny;
    }

    private static boolean hookHttpHeaderGuards(ClassLoader classLoader) {
        boolean hooked = false;
        for (String cls : new String[]{
                "com.android.okhttp.internal.huc.HttpURLConnectionImpl",
                "java.net.URLConnection",
                "java.net.HttpURLConnection"
        }) {
            try {
                Class<?> c = Class.forName(cls, false, classLoader);
                for (Method m : c.getDeclaredMethods()) {
                    if (("addRequestProperty".equals(m.getName()) || "setRequestProperty".equals(m.getName()))
                            && m.getParameterCount() == 2) {
                        hookOnce(m, HTTP_HEADER_NULL_GUARD);
                        hooked = true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return hooked;
    }

    private static boolean hookRunMethods(ClassLoader classLoader, String className, String methodName, int paramCount, XC_MethodHook hook) {
        boolean hooked = false;
        try {
            Class<?> cls = classLoader.loadClass(className);
            for (Method m : cls.getDeclaredMethods()) {
                if (methodName.equals(m.getName()) && m.getParameterCount() == paramCount) {
                    hookOnce(m, hook);
                    hooked = true;
                }
            }
        } catch (Throwable ignored) {}
        return hooked;
    }

    private static boolean hookAllZeroArg(ClassLoader classLoader, String className, XC_MethodHook hook) {
        boolean hooked = false;
        try {
            Class<?> cls = classLoader.loadClass(className);
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() == 0) {
                    hookOnce(m, hook);
                    hooked = true;
                }
            }
        } catch (Throwable ignored) {}
        return hooked;
    }

    private static boolean hookNamed(ClassLoader classLoader, String className, String methodName, XC_MethodHook hook) {
        boolean hooked = false;
        try {
            Class<?> cls = classLoader.loadClass(className);
            for (Method m : cls.getDeclaredMethods()) {
                if (methodName.equals(m.getName())) {
                    hookOnce(m, hook);
                    hooked = true;
                }
            }
        } catch (Throwable ignored) {}
        return hooked;
    }
}
