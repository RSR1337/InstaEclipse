package ps.reso.instaeclipse.mods.misc;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.toast.CustomToast;
import ps.reso.instaeclipse.utils.tracker.FollowIndicatorTracker;

public class FollowStatusHook {

    private static final ConcurrentHashMap<Integer, String> sPendingCallbacks =
            new ConcurrentHashMap<>();

    private static final Set<String> sHookedCallbackClasses =
            Collections.synchronizedSet(new HashSet<>());

    public static void handleRequest(URI uri, Object[] args) {
        String path = uri.getPath();
        if (!path.startsWith("/api/v1/friendships/show/")) return;

        String[] parts = path.split("/");
        if (parts.length < 6) return;

        final String capturedId = parts[5];
        FollowIndicatorTracker.currentlyViewedUserId = capturedId;

        if (args.length > 1) registerCallback(args[1], capturedId);
        if (args.length > 2) registerCallback(args[2], capturedId);
    }

    private static void registerCallback(Object cb, String userId) {
        if (cb == null) return;
        sPendingCallbacks.put(System.identityHashCode(cb), userId);

        String className = cb.getClass().getName();
        if (sHookedCallbackClasses.contains(className)) return;
        sHookedCallbackClasses.add(className);

        for (Method m : cb.getClass().getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            if (m.getParameterCount() == 0) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        int hash = System.identityHashCode(p.thisObject);
                        String uid = sPendingCallbacks.get(hash);
                        if (uid == null) return;

                        for (Object arg : p.args) {
                            Boolean followedBy = parseFollowedBy(arg);
                            if (followedBy != null) {
                                sPendingCallbacks.remove(hash);
                                showFollowToast(uid, followedBy);
                                return;
                            }
                        }

                        Boolean followedByFromObj = parseFollowedBy(p.thisObject);
                        if (followedByFromObj != null) {
                            sPendingCallbacks.remove(hash);
                            showFollowToast(uid, followedByFromObj);
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    private static Boolean parseFollowedBy(Object arg) {

        String body = toJsonString(arg, 3);
        if (body == null || !body.contains("\"followed_by\"")) return null;
        try {
            return new org.json.JSONObject(body).optBoolean("followed_by", false);
        } catch (Throwable ignored) {
            return body.contains("\"followed_by\":true") || body.contains("\"followed_by\": true");
        }
    }

    private static String toJsonString(Object obj) {
        return toJsonString(obj, 2);
    }

    private static String toJsonString(Object obj, int depth) {
        if (obj == null || depth < 0) return null;

        if (obj instanceof java.nio.ByteBuffer) {
            try {
                java.nio.ByteBuffer dup = ((java.nio.ByteBuffer) obj).duplicate();
                if (dup.hasArray()) {
                    String s = new String(dup.array(), dup.arrayOffset(), dup.limit(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    return s.contains("followed_by") ? s : null;
                }
            } catch (Throwable ignored) {}
        }

        if (obj instanceof byte[]) {
            try {
                return new String((byte[]) obj, "UTF-8");
            } catch (Throwable ignored) {}
        }

        if (depth > 0) {
            Class<?> cls = obj.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try {
                        Object val = f.get(obj);
                        if (val != null && val != obj && !(val instanceof Number)) {
                            String s = toJsonString(val, depth - 1);
                            if (s != null && s.contains("followed_by")) return s;
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private static void showFollowToast(String userId, boolean followedBy) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String currentTarget = FollowIndicatorTracker.currentlyViewedUserId;
                if (currentTarget == null || !userId.equals(currentTarget)) return;

                Context ctx = AndroidAppHelper.currentApplication().getApplicationContext();
                String statusStr = followedBy
                        ? I18n.t(ctx, R.string.ig_toast_follows_you)
                        : I18n.t(ctx, R.string.ig_toast_not_follows_you);

                CustomToast.showCustomToast(ctx, "(" + userId + ") " + statusStr);

                FollowIndicatorTracker.currentlyViewedUserId = null;
            } catch (Throwable ignored) {}
        });
    }
}
