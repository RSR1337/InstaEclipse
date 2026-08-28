package ps.reso.instaeclipse.mods.misc;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class LsPatchVerifyErrorGuard {

    private static volatile boolean installed;
    private static volatile boolean logged;

    private LsPatchVerifyErrorGuard() {}

    public static boolean isCertVerifyError(Throwable throwable) {
        Throwable cur = throwable;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur instanceof VerifyError || cur instanceof NoClassDefFoundError) {
                String message = cur.getMessage();
                if (message != null
                        && (message.contains("java.security.cert")
                        || message.contains("X509Certificate")
                        || message.contains("lspatch/origin"))) {
                    return true;
                }
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    public static void swallow(Throwable throwable) {
        if (logged) return;
        logged = true;
        ModuleLog.line("(InstaEclipse | LSPatch): swallowed origin APK cert VerifyError");
    }

    public static void install() {
        if (installed) return;
        installed = true;
        hookHandlerSetter("setUncaughtExceptionHandler");
    }

    private static void hookHandlerSetter(String methodName) {
        try {
            XposedHelpers.findAndHookMethod(Thread.class, methodName, Thread.UncaughtExceptionHandler.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Thread.UncaughtExceptionHandler handler = (Thread.UncaughtExceptionHandler) param.args[0];
                    if (handler == null || handler instanceof SafeHandler) return;
                    param.args[0] = new SafeHandler(handler);
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): " + methodName + " guard failed: " + t.getMessage());
        }
    }

    private static final class SafeHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler original;

        SafeHandler(Thread.UncaughtExceptionHandler original) {
            this.original = original;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (isCertVerifyError(throwable)) {
                swallow(throwable);
                return;
            }
            if (original != null) original.uncaughtException(thread, throwable);
        }
    }
}
