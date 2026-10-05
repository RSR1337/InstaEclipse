package ps.reso.instaeclipse.utils.core;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class IgModelClasses {

    private static final String REEL_CACHE_KEY = "IgModel_ReelClass";
    private static final String REEL_LEGACY_NAME = "com.instagram.model.reels.Reel";
    private static final String REEL_ITEM = "com.instagram.model.reels.ReelItem";
    private static final String USER_SESSION = "com.instagram.common.session.UserSession";

    private static volatile Class<?> reelClass;
    private static volatile boolean reelResolved;

    private IgModelClasses() {}

    public static Class<?> reel(DexKitBridge bridge, ClassLoader classLoader) {
        if (reelResolved) return reelClass;
        synchronized (IgModelClasses.class) {
            if (reelResolved) return reelClass;
            reelClass = resolveReel(bridge, classLoader);
            reelResolved = true;
            ModuleLog.line("(IgModel) Reel class=" + (reelClass != null ? reelClass.getName() : "not found"));
            return reelClass;
        }
    }

    private static Class<?> resolveReel(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            return classLoader.loadClass(REEL_LEGACY_NAME);
        } catch (Throwable ignored) {}
        if (DexKitCache.isCacheValid()) {
            String cached = DexKitCache.loadString(REEL_CACHE_KEY);
            if (cached != null) {
                try {
                    Class<?> cls = classLoader.loadClass(cached);
                    if (looksLikeReel(cls)) return cls;
                } catch (Throwable ignored) {}
            }
        }
        if (bridge == null) return null;
        try {
            List<ClassData> hits = IgDex.findClass(bridge, FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("Reel ID:")));
            for (ClassData cd : hits) {
                try {
                    Class<?> cls = classLoader.loadClass(cd.getName());
                    if (!looksLikeReel(cls)) continue;
                    DexKitCache.saveString(REEL_CACHE_KEY, cls.getName());
                    return cls;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            ModuleLog.line("(IgModel) Reel lookup failed: " + t.getMessage());
        }
        return null;
    }

    private static boolean looksLikeReel(Class<?> cls) {
        boolean reelItemField = false;
        boolean sessionField = false;
        for (Field f : cls.getDeclaredFields()) {
            String type = f.getType().getName();
            if (REEL_ITEM.equals(type)) reelItemField = true;
            if (USER_SESSION.equals(type)) sessionField = true;
        }
        if (!reelItemField || !sessionField) return false;
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (List.class.isAssignableFrom(m.getReturnType()) && p.length == 1
                    && USER_SESSION.equals(p[0].getName())) {
                return true;
            }
        }
        return false;
    }
}
