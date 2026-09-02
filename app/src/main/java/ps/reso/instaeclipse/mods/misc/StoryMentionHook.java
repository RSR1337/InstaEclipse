package ps.reso.instaeclipse.mods.misc;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.users.UserUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class StoryMentionHook {

    private static volatile Method rawMentionsGetter;
    private static volatile Method mentionsConverter;
    private static volatile Class<?> mentionTappableClass;
    private static volatile Class<?> userClass;
    private static volatile Class<?> userSessionClass;
    private static volatile Class<?> reelClass;
    private static volatile Class<?> reelItemClass;
    private static volatile Class<?> userDetailActivityClass;
    private static volatile Class<?> modalActivityClass;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final class MentionedUser {
        final String username;
        final String userId;

        MentionedUser(String username, String userId) {
            this.username = username;
            this.userId = userId;
        }
    }

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        resolveMentionPipeline(bridge, classLoader);
        installButtonHook(bridge, classLoader);
        installClickHook(bridge, classLoader);
        FeatureStatusTracker.setHooked("StoryMentions");
    }

    private static void resolveMentionPipeline(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            userClass = classLoader.loadClass("com.instagram.user.model.User");
        } catch (Throwable ignored) {}
        try {
            userSessionClass = classLoader.loadClass("com.instagram.common.session.UserSession");
        } catch (Throwable ignored) {}
        try {
            reelClass = classLoader.loadClass("com.instagram.model.reels.Reel");
        } catch (Throwable ignored) {}
        try {
            reelItemClass = classLoader.loadClass("com.instagram.model.reels.ReelItem");
        } catch (Throwable ignored) {}
        resolveProfileActivities(classLoader);
        resolveMentionTappableClass(bridge, classLoader);

        if (DexKitCache.isCacheValid()) {
            Method g = DexKitCache.loadMethod("MentionsRawGetter", classLoader);
            Method c = DexKitCache.loadMethod("MentionsConverter", classLoader);
            if (g != null && c != null) {
                rawMentionsGetter = g;
                mentionsConverter = c;
                ModuleLog.line("(IE|Mention) ✅ pipeline resolved from cache");
                return;
            }
        }

        if (rawMentionsGetter == null) try {
            List<MethodData> getters = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.instagram.feed.media.LiveTreeMediaDict")
                            .paramCount(0)
                            .usingEqStrings(List.of("reel_mentions"))));
            if (getters.isEmpty()) {
                getters = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramCount(0)
                                .usingEqStrings(List.of("reel_mentions"))));
            }
            for (MethodData md : getters) {
                if (md.getName().equals("<clinit>")) continue;
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                    String cn = md.getClassName();
                    if (!cn.contains("MediaDict") && !cn.contains("LiveTree") && !cn.contains("feed.media")
                            && getters.size() > 4) continue;
                    m.setAccessible(true);
                    rawMentionsGetter = m;
                    DexKitCache.saveMethod("MentionsRawGetter", m);
                    break;
                } catch (Throwable ignored) {}
            }
            if (rawMentionsGetter == null) ModuleLog.line("(IE|Mention) ❌ rawMentionsGetter not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ❌ rawMentionsGetter query failed: " + t);
        }

        if (mentionsConverter == null) try {
            List<MethodData> converters = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramCount(1)
                            .usingEqStrings(List.of("MentionTappableObject.user is null; dropping mention sticker"))));
            for (MethodData md : converters) {
                try {
                    Method m = md.getMethodInstance(classLoader);
                    if (!List.class.isAssignableFrom(m.getReturnType())) continue;
                    m.setAccessible(true);
                    mentionsConverter = m;
                    DexKitCache.saveMethod("MentionsConverter", m);
                    break;
                } catch (Throwable ignored) {}
            }
            if (mentionsConverter == null) ModuleLog.line("(IE|Mention) ❌ mentionsConverter not found");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ❌ mentionsConverter query failed: " + t);
        }

        if (rawMentionsGetter != null && mentionsConverter != null) {
            ModuleLog.line("(IE|Mention) ✅ pipeline resolved: " + rawMentionsGetter.getName() + " -> " + mentionsConverter.getName());
        }
    }

    private static void resolveMentionTappableClass(DexKitBridge bridge, ClassLoader classLoader) {
        String[] names = {
                "com.instagram.reels.interactive.tappable.MentionTappableObject",
                "com.instagram.reels.interactive.MentionTappableObject",
                "com.instagram.feed.media.ReelMention"
        };
        for (String n : names) {
            try {
                mentionTappableClass = classLoader.loadClass(n);
                return;
            } catch (Throwable ignored) {}
        }
        try {
            List<ClassData> found = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings("MentionTappableObject")));
            for (ClassData cd : found) {
                String cn = cd.getName();
                String simple = cn.substring(cn.lastIndexOf('.') + 1);
                if (!simple.contains("MentionTappable") && !simple.contains("ReelMention")) continue;
                try {
                    Class<?> cls = classLoader.loadClass(cn);
                    if (hasUserField(cls)) {
                        mentionTappableClass = cls;
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static boolean hasUserField(Class<?> cls) {
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (isUserType(f.getType())) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static Object findFieldByType(Object obj, String typeName) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().getName().equals(typeName)) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v != null) return v;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Object findFieldByTypeContains(Object obj, String typePart) {
        if (obj == null || typePart == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().getName().contains(typePart)) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v != null) return v;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private void installButtonHook(DexKitBridge bridge, ClassLoader classLoader) {
        Method method = null;

        if (DexKitCache.isCacheValid()) {
            method = DexKitCache.loadMethod("MentionButton", classLoader);
        }

        if (method == null) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .usingStrings("[INTERNAL] Pause Playback")
                                .paramCount(1)));

                for (MethodData md : methods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        if (m.getReturnType().isArray() &&
                                CharSequence.class.isAssignableFrom(m.getReturnType().getComponentType())) {
                            method = m;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                ModuleLog.line("(IE|Mention) ❌ button hook DexKit: " + t);
            }
        }

        if (method == null) {
            ModuleLog.line("(IE|Mention) ❌ button builder not found");
            return;
        }
        DexKitCache.saveMethod("MentionButton", method);

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.enableStoryMentions) return;
                    CharSequence[] original = (CharSequence[]) param.getResult();
                    if (original == null) return;
                    String mentionLabel = I18n.t(AndroidAppHelper.currentApplication(), R.string.ig_btn_view_mentions);
                    for (CharSequence cs : original) {
                        if (mentionLabel.contentEquals(cs)) return;
                    }
                    CharSequence[] extended = new CharSequence[original.length + 1];
                    System.arraycopy(original, 0, extended, 0, original.length);
                    extended[original.length] = mentionLabel;
                    param.setResult(extended);
                }
            });
            ModuleLog.line("(IE|Mention) ✅ button hook installed");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ❌ button hook: " + t);
        }
    }

    private void installClickHook(DexKitBridge bridge, ClassLoader classLoader) {
        Method method = null;

        if (DexKitCache.isCacheValid()) {
            method = DexKitCache.loadMethod("MentionClick", classLoader);
        }

        if (method == null) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .returnType("void")
                                .usingStrings("explore_viewer",
                                        "friendships/mute_friend_reel/%s/",
                                        "[INTERNAL] Pause Playback")));
                if (methods.isEmpty()) {
                    ModuleLog.line("(IE|Mention) ❌ click handler not found");
                    return;
                }
                method = methods.get(0).getMethodInstance(classLoader);
                DexKitCache.saveMethod("MentionClick", method);
            } catch (Throwable t) {
                ModuleLog.line("(IE|Mention) ❌ click hook DexKit: " + t);
                return;
            }
        }

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!FeatureFlags.enableStoryMentions) return;

                        CharSequence tapped = null;
                        for (Object a : param.args) {
                            if (a instanceof CharSequence cs && tapped == null) tapped = cs;
                        }
                        String mentionLabel = I18n.t(AndroidAppHelper.currentApplication(), R.string.ig_btn_view_mentions);
                        if (tapped == null || !mentionLabel.contentEquals(tapped)) return;

                        param.setResult(null);

                        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                        Object media = null;
                        Object userSession = null;
                        Object reel = null;
                        Object reelItem = null;
                        Context ctx = null;

                        if (param.thisObject != null) {
                            media = findMediaInGraph(param.thisObject, 0, visited);
                            ctx = findContext(param.thisObject);
                        }
                        for (Object a : param.args) {
                            if (a == null) continue;
                            if (media == null) media = findMediaInGraph(a, 0, visited);
                            if (ctx == null) ctx = findContext(a);
                        }
                        userSession = findNearbyTyped(param, userSessionClass);
                        reel = findNearbyTyped(param, reelClass);
                        reelItem = findNearbyTyped(param, reelItemClass);

                        if (ctx == null) { ModuleLog.line("(IE|Mention) ❌ context not found"); return; }
                        if (media == null) { ModuleLog.line("(IE|Mention) ❌ Media not found"); return; }

                        showMentionsDialog(ctx, resolveMentions(media, userSession, reel, reelItem));
                    } catch (Throwable t) {
                        ModuleLog.line("(IE|Mention) ❌ click handler: " + t);
                    }
                }
            });
            ModuleLog.line("(IE|Mention) ✅ click hook installed");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ❌ click hook: " + t);
        }
    }

    private static void resolveProfileActivities(ClassLoader classLoader) {
        String[] detailNames = {
                "com.instagram.profile.activity.UserDetailActivity",
                "com.instagram.user.userlist.UserDetailActivity"
        };
        for (String n : detailNames) {
            try {
                userDetailActivityClass = classLoader.loadClass(n);
                break;
            } catch (Throwable ignored) {}
        }
        String[] modalNames = {
                "com.instagram.modal.TransparentModalActivity",
                "com.instagram.modal.ModalActivity"
        };
        for (String n : modalNames) {
            try {
                modalActivityClass = classLoader.loadClass(n);
                break;
            } catch (Throwable ignored) {}
        }
    }

    private static List<MentionedUser> resolveMentions(Object media, Object userSession, Object reel, Object reelItem) {
        List<MentionedUser> mentions = new ArrayList<>();
        try {
            if (rawMentionsGetter == null || mentionsConverter == null) {
                ModuleLog.line("(IE|Mention) ❌ mention pipeline not resolved");
                return mentions;
            }

            Object dict = findFieldByType(media, "com.instagram.feed.media.LiveTreeMediaDict");
            if (dict == null) dict = findFieldByTypeContains(media, "LiveTreeMediaDict");
            if (dict == null) dict = findFieldByTypeContains(media, "MediaDict");
            if (dict == null) dict = media;
            if (dict == null) {
                ModuleLog.line("(IE|Mention) ❌ LiveTreeMediaDict not found on media");
                return mentions;
            }

            Object rawResult = rawMentionsGetter.invoke(dict);
            if (!(rawResult instanceof List<?> raw) || raw.isEmpty()) return mentions;

            Object convertedResult = mentionsConverter.invoke(null, raw);
            if (!(convertedResult instanceof List<?> list)) return mentions;

            Set<String> excludeNames = new HashSet<>();
            Set<String> excludeIds = new HashSet<>();
            collectExcludedPeople(media, dict, userSession, reel, reelItem, excludeNames, excludeIds);

            LinkedHashMap<String, String> unique = new LinkedHashMap<>();
            LinkedHashSet<String> skipped = new LinkedHashSet<>();
            for (Object item : list) {
                collectMentionedFromSticker(item, unique, skipped, excludeNames, excludeIds);
            }
            if (unique.isEmpty()) {
                for (Object item : raw) {
                    collectMentionedFromRaw(item, unique, skipped, excludeNames, excludeIds);
                }
            }
            unique.entrySet().removeIf(e -> {
                String u = e.getKey();
                if (u != null && excludeNames.contains(u.toLowerCase(Locale.ROOT))) {
                    skipped.add(u);
                    return true;
                }
                return false;
            });
            ModuleLog.line("(IE|Mention) kept=" + unique.keySet() + " skipped=" + skipped);
            for (Map.Entry<String, String> e : unique.entrySet()) {
                mentions.add(new MentionedUser(e.getKey(), e.getValue()));
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) resolveMentions exception: " + t);
        }
        return mentions;
    }

    private static void collectExcludedPeople(Object media, Object dict, Object userSession,
                                              Object reel, Object reelItem,
                                              Set<String> names, Set<String> ids) {
        collectExcludeFromHost(media, names, ids);
        if (dict != null && dict != media) collectExcludeFromHost(dict, names, ids);
        collectExcludeFromHost(reel, names, ids);
        collectExcludeFromHost(reelItem, names, ids);
        collectExcludeFromHost(userSession, names, ids);
    }

    private static void collectExcludeFromHost(Object host, Set<String> names, Set<String> ids) {
        if (host == null) return;
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (!isUserType(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    addExcludedUser(f.get(host), names, ids);
                } catch (Throwable ignored) {}
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!isUserType(m.getReturnType())) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;
                String mn = m.getName();
                if (mn.equals("toString") || mn.equals("hashCode") || mn.toLowerCase(Locale.ROOT).contains("mention")) continue;
                try {
                    m.setAccessible(true);
                    addExcludedUser(m.invoke(host), names, ids);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void addExcludedUser(Object user, Set<String> names, Set<String> ids) {
        if (user == null || !isUserType(user.getClass())) return;
        String n = UserUtils.callUsernameGetter(user);
        if (n != null && !n.isEmpty()) names.add(n.toLowerCase(Locale.ROOT));
        String id = callUserIdGetter(user);
        if (id != null && !id.isEmpty()) ids.add(id);
    }

    private static boolean isExcluded(Object user, String username, Set<String> names, Set<String> ids) {
        if (user != null) {
            String id = callUserIdGetter(user);
            if (id != null && ids.contains(id)) return true;
        }
        return username != null && names.contains(username.toLowerCase(Locale.ROOT));
    }

    private static void considerUser(Object user, LinkedHashMap<String, String> out, LinkedHashSet<String> skipped,
                                     Set<String> excludeNames, Set<String> excludeIds) {
        if (user == null || !isUserType(user.getClass())) return;
        String username = UserUtils.callUsernameGetter(user);
        if (username == null || username.isEmpty()) return;
        if (isExcluded(user, username, excludeNames, excludeIds)) {
            skipped.add(username);
            return;
        }
        if (!out.containsKey(username)) {
            out.put(username, callUserIdGetter(user));
        }
    }

    private static void collectMentionedFromSticker(Object item, LinkedHashMap<String, String> out,
                                                    LinkedHashSet<String> skipped,
                                                    Set<String> excludeNames, Set<String> excludeIds) {
        if (item == null) return;
        if (isUserType(item.getClass())) {
            considerUser(item, out, skipped, excludeNames, excludeIds);
            return;
        }
        if (isMentionTappable(item)) {
            collectUsersOnObject(item, out, skipped, excludeNames, excludeIds);
            return;
        }

        int before = out.size();
        Class<?> cls = item.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft.isArray()) continue;
                if (isUserType(ft)) continue;
                if (isIgnoredHostType(ft)) continue;
                String tn = ft.getName();
                if (tn.startsWith("android.") || tn.startsWith("java.") || tn.startsWith("javax.")) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v == null) continue;
                    if (v instanceof Collection<?> col) {
                        for (Object el : col) {
                            if (el == null) continue;
                            if (isMentionTappable(el) || looksLikeMentionHolder(el)) {
                                collectUsersOnObject(el, out, skipped, excludeNames, excludeIds);
                            }
                        }
                        continue;
                    }
                    if (v instanceof Map) continue;
                    if (isIgnoredHost(v)) continue;
                    if (isUserType(v.getClass())) continue;
                    if (isMentionTappable(v) || looksLikeMentionHolder(v)) {
                        collectUsersOnObject(v, out, skipped, excludeNames, excludeIds);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }

        if (out.size() == before) {
            collectUsersOnObject(item, out, skipped, excludeNames, excludeIds);
        }
    }

    private static void collectMentionedFromRaw(Object item, LinkedHashMap<String, String> out,
                                                LinkedHashSet<String> skipped,
                                                Set<String> excludeNames, Set<String> excludeIds) {
        if (item == null || isIgnoredHost(item)) return;
        if (isUserType(item.getClass())) {
            considerUser(item, out, skipped, excludeNames, excludeIds);
            return;
        }
        collectUsersOnObject(item, out, skipped, excludeNames, excludeIds);
    }

    private static void collectUsersOnObject(Object obj, LinkedHashMap<String, String> out,
                                             LinkedHashSet<String> skipped,
                                             Set<String> excludeNames, Set<String> excludeIds) {
        if (obj == null) return;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (!isUserType(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    considerUser(f.get(obj), out, skipped, excludeNames, excludeIds);
                } catch (Throwable ignored) {}
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !isUserType(m.getReturnType())) continue;
                if (Modifier.isStatic(m.getModifiers())) continue;
                try {
                    m.setAccessible(true);
                    considerUser(m.invoke(obj), out, skipped, excludeNames, excludeIds);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean isMentionTappable(Object obj) {
        if (obj == null) return false;
        if (mentionTappableClass != null && mentionTappableClass.isInstance(obj)) return true;
        String n = obj.getClass().getName();
        String simple = n.substring(n.lastIndexOf('.') + 1);
        return simple.contains("MentionTappable") || simple.contains("ReelMention");
    }

    private static boolean looksLikeMentionHolder(Object obj) {
        if (obj == null) return false;
        String n = obj.getClass().getName();
        String simple = n.substring(n.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (simple.contains("mention") || simple.contains("tappable")) return true;
        return hasUserField(obj.getClass()) && !isIgnoredHost(obj) && !isUserType(obj.getClass());
    }

    private static boolean isIgnoredHost(Object obj) {
        return obj != null && isIgnoredHostType(obj.getClass());
    }

    private static boolean isIgnoredHostType(Class<?> ft) {
        if (ft == null) return false;
        String tn = ft.getName();
        if (tn.equals("com.instagram.feed.media.Media") || tn.contains("MediaDict")) return true;
        if (tn.equals("com.instagram.model.reels.Reel") || tn.equals("com.instagram.model.reels.ReelItem")) return true;
        if (tn.equals("com.instagram.common.session.UserSession")) return true;
        if (userSessionClass != null && userSessionClass.isAssignableFrom(ft)) return true;
        if (reelClass != null && reelClass.isAssignableFrom(ft)) return true;
        if (reelItemClass != null && reelItemClass.isAssignableFrom(ft)) return true;
        return false;
    }

    private static boolean isUserType(Class<?> type) {
        if (type == null) return false;
        if (userClass != null && (type == userClass || userClass.isAssignableFrom(type))) return true;
        String n = type.getName();
        return n.equals("com.instagram.user.model.User") || n.endsWith(".user.model.User");
    }

    private static String callUserIdGetter(Object user) {
        if (user == null) return null;
        String[] preferred = {"getId", "getPk", "Cpk", "getStrongId"};
        for (String name : preferred) {
            try {
                Method m = user.getClass().getMethod(name);
                String id = normalizeUserId(m.invoke(user));
                if (id != null) return id;
            } catch (Throwable ignored) {}
        }
        for (Method m : user.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            Class<?> rt = m.getReturnType();
            if (rt != String.class && rt != Long.class && rt != long.class) continue;
            String mn = m.getName().toLowerCase(Locale.ROOT);
            if (!mn.contains("id") && !mn.contains("pk")) continue;
            try {
                String id = normalizeUserId(m.invoke(user));
                if (id != null) return id;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String normalizeUserId(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        int us = s.indexOf('_');
        if (us > 0) s = s.substring(0, us);
        if (s.length() < 4 || s.length() > 22) return null;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return null;
        }
        return s;
    }

    private static Object findNearbyTyped(XC_MethodHook.MethodHookParam param, Class<?> type) {
        if (type == null) return null;
        if (param.thisObject != null) {
            Object found = findTypedInGraph(param.thisObject, type, 0,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (found != null) return found;
        }
        for (Object a : param.args) {
            if (a == null) continue;
            Object found = findTypedInGraph(a, type, 0,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            if (found != null) return found;
        }
        return null;
    }

    private static Object findTypedInGraph(Object obj, Class<?> type, int depth, Set<Object> visited) {
        if (obj == null || type == null || depth > GRAPH_MAX_DEPTH) return null;
        if (!visited.add(obj)) return null;
        if (type.isInstance(obj)) return obj;
        String className = obj.getClass().getName();
        if (!className.startsWith("com.instagram.") &&
                !className.startsWith("com.facebook.") &&
                !className.startsWith("X.")) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft.isArray()) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (type.isInstance(val)) return val;
                    String vn = val.getClass().getName();
                    if (vn.startsWith("com.instagram.") || vn.startsWith("com.facebook.") || vn.startsWith("X.")) {
                        Object found = findTypedInGraph(val, type, depth + 1, visited);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static final int GRAPH_MAX_DEPTH = 6;

    private static Object findMediaInGraph(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > GRAPH_MAX_DEPTH) return null;
        if (!visited.add(obj)) return null;

        String className = obj.getClass().getName();
        if (!className.startsWith("com.instagram.") &&
                !className.startsWith("com.facebook.") &&
                !className.startsWith("X.")) return null;

        if (className.equals("com.instagram.feed.media.Media")) return obj;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft.isArray()) continue;
                f.setAccessible(true);
                Object val;
                try { val = f.get(obj); } catch (Throwable ignored) { continue; }
                if (val == null) continue;

                String vn = val.getClass().getName();
                if (vn.equals("com.instagram.feed.media.Media")) return val;
                if (vn.startsWith("com.instagram.") || vn.startsWith("com.facebook.") || vn.startsWith("X.")) {
                    Object found = findMediaInGraph(val, depth + 1, visited);
                    if (found != null) return found;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void showMentionsDialog(Context ctx, List<MentionedUser> mentions) {
        mainHandler.post(() -> {
            try {
                float dp   = ctx.getResources().getDisplayMetrics().density;
                boolean dk = (ctx.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

                int sheetBg    = dk ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
                int cardBg     = dk ? Color.parseColor("#2C2C2E") : Color.parseColor("#FFFFFF");
                int textPrim   = dk ? Color.WHITE                 : Color.parseColor("#1C1C1E");
                int textSec    = dk ? Color.parseColor("#AEAEB2") : Color.parseColor("#6C6C70");
                int accentBg   = Color.parseColor("#0A84FF");
                int handleClr  = dk ? Color.parseColor("#48484A") : Color.parseColor("#C7C7CC");

                LinearLayout sheet = new LinearLayout(ctx);
                sheet.setOrientation(LinearLayout.VERTICAL);
                sheet.setBackground(roundRect(sheetBg, 20, ctx, dp));
                int hPad = (int)(20 * dp);
                sheet.setPadding(hPad, (int)(12 * dp), hPad, (int)(28 * dp));

                View handle = new View(ctx);
                LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                        (int)(40 * dp), (int)(4 * dp));
                handleLp.gravity = Gravity.CENTER_HORIZONTAL;
                handleLp.bottomMargin = (int)(16 * dp);
                handle.setLayoutParams(handleLp);
                handle.setBackground(roundRect(handleClr, 2, ctx, dp));
                sheet.addView(handle);

                TextView title = new TextView(ctx);
                title.setText(I18n.t(ctx, R.string.ig_mention_dialog_title));
                title.setTextColor(textPrim);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                title.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = (int)(4 * dp);
                title.setLayoutParams(titleLp);
                sheet.addView(title);

                TextView subtitle = new TextView(ctx);
                subtitle.setText(mentions.isEmpty()
                        ? I18n.t(ctx, R.string.ig_mention_no_mentions)
                        : I18n.t(ctx, R.string.ig_mention_subtitle, mentions.size()));
                subtitle.setTextColor(textSec);
                subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                subLp.bottomMargin = (int)(14 * dp);
                subtitle.setLayoutParams(subLp);
                sheet.addView(subtitle);

                Dialog dialog = new Dialog(ctx);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                if (!mentions.isEmpty()) {
                    ScrollView scroll = new ScrollView(ctx);
                    scroll.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                    LinearLayout list = new LinearLayout(ctx);
                    list.setOrientation(LinearLayout.VERTICAL);

                    for (MentionedUser mention : mentions) {
                        String username = mention.username;
                        String userId = mention.userId;
                        TextView row = new TextView(ctx);
                        row.setText("@" + username);
                        row.setTextColor(textPrim);
                        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                        row.setTypeface(null, Typeface.BOLD);
                        int rowPad = (int)(14 * dp);
                        row.setPadding(rowPad, rowPad, rowPad, rowPad);
                        row.setBackground(roundRect(cardBg, 12, ctx, dp));
                        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);
                        rowLp.bottomMargin = (int)(8 * dp);
                        row.setLayoutParams(rowLp);
                        row.setOnClickListener(v -> {
                            dialog.dismiss();
                            openMentionProfile(ctx, username, userId);
                        });
                        row.setOnLongClickListener(v -> {
                            ClipboardManager cm = (ClipboardManager)
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("username", username));
                                Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_mention_copied, username), Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        });
                        list.addView(row);
                    }
                    scroll.addView(list);
                    sheet.addView(scroll);

                    if (mentions.size() > 1) {
                        Button btnAll = makePillButton(ctx, I18n.t(ctx, R.string.ig_mention_copy_all), accentBg, Color.WHITE, dp);
                        btnAll.setOnClickListener(v -> {
                            dialog.dismiss();
                            StringBuilder sb = new StringBuilder();
                            for (MentionedUser m : mentions) sb.append("@").append(m.username).append("\n");
                            ClipboardManager cm = (ClipboardManager)
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("mentions", sb.toString().trim()));
                                Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_all_mentions_copied), Toast.LENGTH_SHORT).show();
                            }
                        });
                        sheet.addView(btnAll);
                    }
                }

                dialog.setContentView(sheet);
                Window w = dialog.getWindow();
                if (w != null) {
                    w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    w.setGravity(Gravity.BOTTOM);
                    w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT);
                    WindowManager.LayoutParams wlp = w.getAttributes();
                    int margin = (int)(12 * dp);
                    wlp.x = margin;
                    wlp.y = margin;
                    w.setAttributes(wlp);
                }
                dialog.show();

            } catch (Throwable t) {
                ModuleLog.line("(IE|Mention) ❌ showMentionsDialog: " + t);
            }
        });
    }

    private static GradientDrawable roundRect(int color, float radiusDp, Context ctx, float dp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * dp);
        return d;
    }

    private static Button makePillButton(Context ctx, String label,
                                          int bgColor, int textColor, float dp) {
        Button btn = new Button(ctx);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackground(roundRect(bgColor, 14, ctx, dp));
        btn.setAllCaps(false);
        btn.setPadding((int)(20 * dp), (int)(14 * dp), (int)(20 * dp), (int)(14 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int)(10 * dp);
        btn.setLayoutParams(lp);
        return btn;
    }

    private static void openMentionProfile(Context ctx, String username, String userId) {
        Context start = resolveStartContext(ctx);
        if (tryStartUserDetailActivity(start, username, userId)) return;
        if (username != null && !username.isEmpty()
                && tryStartView(start, "instagram://user?username=" + Uri.encode(username))) return;
        if (userId != null && !userId.isEmpty()
                && tryStartView(start, "instagram://user?user_id=" + Uri.encode(userId))) return;
        if (tryStartModalProfile(start, username, userId)) return;
        if (username != null && !username.isEmpty()
                && tryStartView(start, "https://instagram.com/_u/" + Uri.encode(username))) return;
        if (username != null && !username.isEmpty()) {
            tryStartHttpsFallback(start, username);
        }
    }

    private static Context resolveStartContext(Context ctx) {
        Activity activity = asActivity(ctx);
        if (activity != null) return activity;
        try {
            Activity cur = UIHookManager.getCurrentActivity();
            if (cur != null) return cur;
        } catch (Throwable ignored) {}
        return ctx;
    }

    private static Activity asActivity(Context ctx) {
        Context c = ctx;
        while (c instanceof ContextWrapper cw) {
            if (c instanceof Activity a) return a;
            c = cw.getBaseContext();
        }
        return ctx instanceof Activity a ? a : null;
    }

    private static Bundle profileArgs(String username, String userId) {
        Bundle args = new Bundle();
        putProfileExtras(args, username, userId);
        return args;
    }

    private static void putProfileExtras(Bundle dest, String username, String userId) {
        if (userId != null && !userId.isEmpty()) {
            dest.putString("UserDetailFragment.USER_ID", userId);
            dest.putString("ProfileFragment.ARG_USER_ID", userId);
        }
        if (username != null && !username.isEmpty()) {
            dest.putString("UserDetailFragment.USER_NAME", username);
            dest.putString("UserDetailFragment.EXTRA_PROFILE_USER_NAME", username);
        }
    }

    private static void putProfileExtras(Intent intent, String username, String userId) {
        if (userId != null && !userId.isEmpty()) {
            intent.putExtra("UserDetailFragment.USER_ID", userId);
            intent.putExtra("ProfileFragment.ARG_USER_ID", userId);
        }
        if (username != null && !username.isEmpty()) {
            intent.putExtra("UserDetailFragment.USER_NAME", username);
            intent.putExtra("UserDetailFragment.EXTRA_PROFILE_USER_NAME", username);
        }
    }

    private static boolean tryStartUserDetailActivity(Context ctx, String username, String userId) {
        Class<?> cls = userDetailActivityClass;
        if (cls == null) {
            String[] names = {
                    "com.instagram.profile.activity.UserDetailActivity",
                    "com.instagram.user.userlist.UserDetailActivity"
            };
            ClassLoader cl = ctx.getClassLoader();
            for (String n : names) {
                try {
                    cls = cl.loadClass(n);
                    userDetailActivityClass = cls;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (cls == null) return false;
        try {
            Intent intent = new Intent(ctx, cls);
            putProfileExtras(intent, username, userId);
            intent.putExtra("fragment_arguments", profileArgs(username, userId));
            return launchIntent(ctx, intent, "UserDetailActivity");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) UserDetailActivity: " + t);
            return false;
        }
    }

    private static boolean tryStartModalProfile(Context ctx, String username, String userId) {
        Class<?> cls = modalActivityClass;
        if (cls == null) {
            String[] names = {
                    "com.instagram.modal.TransparentModalActivity",
                    "com.instagram.modal.ModalActivity"
            };
            ClassLoader cl = ctx.getClassLoader();
            for (String n : names) {
                try {
                    cls = cl.loadClass(n);
                    modalActivityClass = cls;
                    break;
                } catch (Throwable ignored) {}
            }
        }
        if (cls == null) return false;
        try {
            Intent intent = new Intent(ctx, cls);
            intent.putExtra("fragment_name", "profile");
            intent.putExtra("fragment_arguments", profileArgs(username, userId));
            putProfileExtras(intent, username, userId);
            return launchIntent(ctx, intent, cls.getName());
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ModalActivity: " + t);
            return false;
        }
    }

    private static boolean tryStartView(Context ctx, String uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            intent.setPackage(ctx.getPackageName());
            return launchIntent(ctx, intent, uri);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryStartHttpsFallback(Context ctx, String username) {
        String url = "https://instagram.com/" + Uri.encode(username);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(ctx.getPackageName());
            if (launchIntent(ctx, intent, url)) return true;
        } catch (Throwable ignored) {}
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            return launchIntent(ctx, intent, url);
        } catch (Throwable t) {
            ModuleLog.line("(IE|Mention) ❌ open profile: " + t);
            return false;
        }
    }

    private static boolean launchIntent(Context ctx, Intent intent, String via) {
        try {
            if (!(ctx instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            ctx.startActivity(intent);
            ModuleLog.line("(IE|Mention) opened profile via " + via);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Context findContext(Object obj) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        if (v instanceof Context c) return c;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

}
