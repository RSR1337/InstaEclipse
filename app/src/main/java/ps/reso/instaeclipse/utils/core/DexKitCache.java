package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class DexKitCache {

    private static final String PREF_NAME  = "instaeclipse_dexkit_cache";
    private static final String KEY_VER    = "_v";
    private static final char   SEP        = '\u0000';

    private static SharedPreferences prefs;
    private static boolean cacheValid = false;

    public static void init(Context context, String igVersion) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_VER, "");
        if (stored.equals(igVersion)) {
            cacheValid = true;
            ModuleLog.line("(DexKitCache) Cache valid for IG " + igVersion);
        } else {
            cacheValid = false;
            prefs.edit().clear().putString(KEY_VER, igVersion).apply();
            ModuleLog.line("(DexKitCache) Version " + stored + " → " + igVersion + ", cache cleared");
        }
    }

    public static boolean isCacheValid() {
        return cacheValid;
    }

    public static void clearCache() {
        if (prefs == null) return;
        prefs.edit().clear().apply();
        cacheValid = false;
        ModuleLog.line("(DexKitCache) Cache manually cleared — DexKit will re-run on next launch");
    }

    public static void saveMethod(String key, Method m) {
        if (prefs == null || m == null) return;
        prefs.edit().putString("m_" + key, encode(m)).apply();
    }

    public static Method loadMethod(String key, ClassLoader loader) {
        if (prefs == null) return null;
        String val = prefs.getString("m_" + key, null);
        return val != null ? decode(val, loader) : null;
    }

    public static void saveMethods(String key, List<Method> methods) {
        if (prefs == null || methods == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("mc_" + key, methods.size());
        for (int i = 0; i < methods.size(); i++) {
            ed.putString("m_" + key + "_" + i, encode(methods.get(i)));
        }
        ed.apply();
    }

    public static List<Method> loadMethods(String key, ClassLoader loader) {
        if (prefs == null) return null;
        int count = prefs.getInt("mc_" + key, -1);
        if (count < 0) return null;
        List<Method> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String val = prefs.getString("m_" + key + "_" + i, null);
            if (val == null) return null;
            Method m = decode(val, loader);
            if (m == null) return null;
            result.add(m);
        }
        return result;
    }

    public static void saveString(String key, String value) {
        if (prefs == null) return;
        prefs.edit().putString("s_" + key, value).apply();
    }

    public static String loadString(String key) {
        if (prefs == null) return null;
        return prefs.getString("s_" + key, null);
    }

    private static String encode(Method m) {
        return m.getDeclaringClass().getName() + SEP + m.getName() + SEP + descriptor(m);
    }

    private static Method decode(String encoded, ClassLoader loader) {
        try {
            int i1 = encoded.indexOf(SEP);
            int i2 = encoded.indexOf(SEP, i1 + 1);
            if (i1 < 0 || i2 < 0) return null;
            String className  = encoded.substring(0, i1);
            String methodName = encoded.substring(i1 + 1, i2);
            String desc       = encoded.substring(i2 + 1);
            Class<?> clazz = Class.forName(className, false, loader);
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && descriptor(m).equals(desc)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String descriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) typeDesc(sb, p);
        sb.append(")");
        typeDesc(sb, m.getReturnType());
        return sb.toString();
    }

    private static void typeDesc(StringBuilder sb, Class<?> t) {
        while (t.isArray()) { sb.append('['); t = t.getComponentType(); }
        if (t.isPrimitive()) {
            if      (t == void.class)    sb.append('V');
            else if (t == boolean.class) sb.append('Z');
            else if (t == byte.class)    sb.append('B');
            else if (t == char.class)    sb.append('C');
            else if (t == short.class)   sb.append('S');
            else if (t == int.class)     sb.append('I');
            else if (t == long.class)    sb.append('J');
            else if (t == float.class)   sb.append('F');
            else if (t == double.class)  sb.append('D');
        } else {
            sb.append('L').append(t.getName().replace('.', '/')).append(';');
        }
    }
}
