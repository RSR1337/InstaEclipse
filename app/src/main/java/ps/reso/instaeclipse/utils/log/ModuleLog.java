package ps.reso.instaeclipse.utils.log;

import android.util.Log;


public final class ModuleLog {

    private static final String TAG = "InstaEclipse";

    private ModuleLog() {}

    private static String getCallerInfo() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (!cn.equals(ModuleLog.class.getName()) && !cn.equals(Logging.class.getName())
                    && !cn.equals(Thread.class.getName()) && !cn.startsWith("de.robv.android.xposed.")) {
                String simpleName = cn.substring(cn.lastIndexOf('.') + 1);
                return "[" + simpleName + "." + stack[i].getMethodName() + ":" + stack[i].getLineNumber() + "] ";
            }
        }
        return "";
    }

    public static void line(String msg) {
        String formatted = getCallerInfo() + msg;
        Logging.append(formatted);
        Log.i(TAG, formatted);
    }

    public static void line(String msg, Throwable t) {
        String stackTraceStr = t != null ? Log.getStackTraceString(t) : "";
        String fullMsg = msg + (stackTraceStr.isEmpty() ? "" : "\n" + stackTraceStr);
        String formatted = getCallerInfo() + fullMsg;
        Logging.append(formatted);
        Log.i(TAG, formatted);
    }
}
