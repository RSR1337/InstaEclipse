package ps.reso.instaeclipse.mods.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;
import ps.reso.instaeclipse.xposedcompat.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.dialog.IeDialog;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;

public class CommentCopyHook {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Activity currentActivity = null;

    private static final String CACHE_KEY = "CommentCopy_ShowMenu";
    private static final String CACHE_RESOLVER_KEY = "CommentCopy_Resolver";

    private static final String EFFECT_HANDLER_CLASS =
            "com.instagram.comments.mvvm.view.fragment.CommentViewUiEffectHandler$handleCommentUiEffects$1";
    private static final String REPO_CLASS =
            "com.instagram.comments.mvvm.data.MediaCommentListRepository";

    private static java.lang.reflect.Field repoField;
    private static java.lang.reflect.Method resolverMethod;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        ps.reso.instaeclipse.mods.media.CommentMedia.trackCommentSheet(bridge, classLoader);
        // Track current Activity for showing dialogs
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { ps.reso.instaeclipse.utils.core.JitGuard.tick(p.method);
                    currentActivity = (Activity) p.thisObject;
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { ps.reso.instaeclipse.utils.core.JitGuard.tick(p.method);
                    if (currentActivity == p.thisObject) currentActivity = null;
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ⚠️ Activity tracker – " + t);
        }

        if (DexKitCache.isCacheValid()) {
            java.lang.reflect.Method cachedFmj = DexKitCache.loadMethod(CACHE_KEY, classLoader);
            java.lang.reflect.Method cachedResolver = DexKitCache.loadMethod(CACHE_RESOLVER_KEY, classLoader);
            if (cachedFmj != null && cachedResolver != null
                    && resolveRepoField(cachedFmj.getDeclaringClass())) {
                resolverMethod = cachedResolver;
                resolverMethod.setAccessible(true);
                XposedBridge.hookMethod(cachedFmj, SHOW_MENU_HOOK);
                ModuleLog.line("(InstaEclipse | CopyComment): ✅ Hooked (cached, new) "
                        + cachedFmj.getDeclaringClass().getName() + "." + cachedFmj.getName());
                FeatureStatusTracker.setHooked("CopyComment");
                return;
            }

            List<java.lang.reflect.Method> cachedLongPress =
                    DexKitCache.loadMethods(CACHE_KEY_OLD, classLoader);
            if (cachedLongPress != null && !cachedLongPress.isEmpty()) {
                for (java.lang.reflect.Method m : cachedLongPress) {
                    XposedBridge.hookMethod(m, LONG_PRESS_HOOK);
                }
                ModuleLog.line("(InstaEclipse | CopyComment): ✅ Hooked (cached, legacy) – "
                        + cachedLongPress.size() + " method(s)");
                FeatureStatusTracker.setHooked("CopyComment");
                return;
            }
        }

        try {
            if (findAndHookNew(bridge, classLoader)) return;
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ⚠️ new-architecture path failed, "
                    + "falling back to legacy – " + t);
        }

        try {
            findAndHookOld(bridge, classLoader);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ install – " + t.getMessage());
        }
    }

    private boolean findAndHookNew(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            Class<?> effectHandlerClass = null;
            try {
                effectHandlerClass = classLoader.loadClass(EFFECT_HANDLER_CLASS);
            } catch (ClassNotFoundException ignored) {}
            if (effectHandlerClass == null) {
                List<ClassData> found = IgDex.findClass(bridge, FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings("handleCommentUiEffects")));
                if (!found.isEmpty()) {
                    effectHandlerClass = classLoader.loadClass(found.get(0).getName());
                }
            }
            if (effectHandlerClass == null) {
                List<ClassData> found = IgDex.findClass(bridge, FindClass.create()
                        .matcher(ClassMatcher.create()
                                .usingStrings("com.instagram.comments.mvvm.data.MediaCommentListRepository")));
                if (!found.isEmpty()) {
                    effectHandlerClass = classLoader.loadClass(found.get(0).getName());
                }
            }
            if (effectHandlerClass == null) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ comment effect handler class not found");
                return false;
            }

            java.lang.reflect.Method fmj = null;
            for (java.lang.reflect.Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(effectHandlerClass)) {
                Class<?> t = f.getType();
                for (java.lang.reflect.Method cand : ps.reso.instaeclipse.utils.core.SafeReflect.methods(t)) {
                    Class<?>[] p = cand.getParameterTypes();
                    if (java.lang.reflect.Modifier.isAbstract(cand.getModifiers())) continue;
                    if (cand.getReturnType() == void.class && p.length == 4
                            && p[0] == String.class && p[1] == String.class
                            && p[2] == float.class && p[3] == boolean.class) {
                        fmj = cand;
                        break;
                    }
                }
                if (fmj != null) break;
            }

            if (fmj == null) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ Dcq.FmJ-equivalent not found "
                        + "(older Instagram version? falling back to legacy)");
                return false;
            }
            fmj.setAccessible(true);

            if (!resolveRepoField(fmj.getDeclaringClass())) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ repository field not found on "
                        + fmj.getDeclaringClass().getName());
                return false;
            }

            List<MethodData> callees = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramCount(3)
                            .addCaller(MethodMatcher.create(fmj))));

            for (MethodData md : callees) {
                try {
                    java.lang.reflect.Method cm = md.getMethodInstance(classLoader);
                    if (!java.lang.reflect.Modifier.isStatic(cm.getModifiers())) continue;
                    Class<?>[] p = cm.getParameterTypes();
                    if (p[1] != String.class || p[2] != String.class) continue;
                    if (cm.getReturnType() == void.class || cm.getReturnType().isPrimitive()) continue;
                    resolverMethod = cm;
                    resolverMethod.setAccessible(true);
                    break;
                } catch (Throwable ignored) {}
            }

            if (resolverMethod == null) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ resolver method not found among FmJ's callees");
                return false;
            }

            XposedBridge.hookMethod(fmj, SHOW_MENU_HOOK);
            DexKitCache.saveMethod(CACHE_KEY, fmj);
            DexKitCache.saveMethod(CACHE_RESOLVER_KEY, resolverMethod);
            FeatureStatusTracker.setHooked("CopyComment");
            ModuleLog.line("(InstaEclipse | CopyComment): ✅ Hooked " + fmj.getDeclaringClass().getName()
                    + "." + fmj.getName() + " (resolver=" + resolverMethod.getDeclaringClass().getName()
                    + "." + resolverMethod.getName() + ")");
            return true;
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ findAndHookNew – " + t);
            return false;
        }
    }

    private static final String CACHE_KEY_OLD = "CommentCopy_LongPress";

    private void findAndHookOld(DexKitBridge bridge, ClassLoader classLoader) {
        List<MethodData> found = IgDex.findMethod(bridge, FindMethod.create()
                .matcher(MethodMatcher.create()
                        .name("onLongPress")
                        .usingStrings("fb_comment_long_press")
                )
        );

        if (found.isEmpty()) {
            found = IgDex.findMethod(bridge, FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .name("onLongPress")
                            .usingStrings("comment_row_component")
                    )
            );
        }

        if (found.isEmpty()) {
            List<ClassData> classes = IgDex.findClass(bridge, FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings("fb_comment_long_press")
                    )
            );
            for (ClassData cd : classes) {
                found.addAll(IgDex.findMethod(bridge, FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .declaredClass(cd.getName())
                                .name("onLongPress")
                        )
                ));
            }
        }

        if (found.isEmpty()) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ legacy onLongPress not found via DexKit either");
            return;
        }

        List<java.lang.reflect.Method> hooked = new ArrayList<>();
        for (MethodData md : found) {
            try {
                java.lang.reflect.Method m = md.getMethodInstance(classLoader);
                XposedBridge.hookMethod(m, LONG_PRESS_HOOK);
                hooked.add(m);
                ModuleLog.line("(InstaEclipse | CopyComment): ✅ Hooked (legacy) "
                        + md.getClassName() + ".onLongPress");
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ legacy hook – " + t.getMessage());
            }
        }

        if (!hooked.isEmpty()) {
            DexKitCache.saveMethods(CACHE_KEY_OLD, hooked);
            FeatureStatusTracker.setHooked("CopyComment");
        }
    }

    private static final XC_MethodHook LONG_PRESS_HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
            if (!FeatureFlags.enableCopyComment) return;
            try {
                String text = extractCommentTextLegacy(param.thisObject);
                if (text == null || text.trim().isEmpty()) return;

                Context ctx = currentActivity;
                if (ctx == null) return;

                showCopyPopup(ctx, text.trim());
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ legacy hook body – " + t);
            }
        }
    };

    private static final String CACHE_ITEM_CLASS = "CommentCopy_ItemClass";
    private static final String CACHE_TEXT_FIELD = "CommentCopy_TextField";

    private static String extractCommentTextLegacy(Object gestureListener) {
        if (DexKitCache.isCacheValid()) {
            String itemClass = DexKitCache.loadString(CACHE_ITEM_CLASS);
            String textField = DexKitCache.loadString(CACHE_TEXT_FIELD);
            if (itemClass != null && textField != null) {
                try {
                    Object item = findItemByClass(gestureListener, itemClass);
                    if (item == null) return null;
                    Field tf = item.getClass().getDeclaredField(textField);
                    tf.setAccessible(true);
                    String val = (String) tf.get(item);
                    return (val != null && !val.isEmpty()) ? val : null;
                } catch (Throwable ignored) {
                }
            }
        }

        return discoverAndCacheLegacy(gestureListener);
    }

    private static Object findItemByClass(Object gestureListener, String targetClassName) {
        try {
            for (Field f1 : ps.reso.instaeclipse.utils.core.SafeReflect.fields(gestureListener.getClass())) {
                if (!isObfuscatedObject(f1)) continue;
                f1.setAccessible(true);
                Object holder = f1.get(gestureListener);
                if (holder == null) continue;
                for (Field f2 : ps.reso.instaeclipse.utils.core.SafeReflect.fields(holder.getClass())) {
                    if (!isObfuscatedObject(f2)) continue;
                    f2.setAccessible(true);
                    Object item = f2.get(holder);
                    if (item != null && item.getClass().getName().equals(targetClassName))
                        return item;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String discoverAndCacheLegacy(Object gestureListener) {
        try {
            ClassLoader cl = gestureListener.getClass().getClassLoader();
            Class<?> userClass = Class.forName(
                    "com.instagram.user.model.User", false, cl);

            for (Field f1 : ps.reso.instaeclipse.utils.core.SafeReflect.fields(gestureListener.getClass())) {
                if (!isObfuscatedObject(f1)) continue;
                f1.setAccessible(true);
                Object holder = f1.get(gestureListener);
                if (holder == null) continue;

                for (Field f2 : ps.reso.instaeclipse.utils.core.SafeReflect.fields(holder.getClass())) {
                    if (!isObfuscatedObject(f2)) continue;
                    f2.setAccessible(true);
                    Object item = f2.get(holder);
                    if (item == null) continue;
                    if (!hasFieldOfType(item.getClass(), userClass)) continue;

                    String[] found = findTextFieldLegacy(item);
                    if (found != null) {
                        DexKitCache.saveString(CACHE_ITEM_CLASS, item.getClass().getName());
                        DexKitCache.saveString(CACHE_TEXT_FIELD, found[0]);
                        ModuleLog.line("(InstaEclipse | CopyComment): cached (legacy) "
                                + item.getClass().getName() + "." + found[0]);
                        return found[1];
                    }
                    return null;
                }
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ legacy discover – " + t);
        }
        return null;
    }

    private static String[] findTextFieldLegacy(Object item) {
        String bestName = null;
        String bestVal  = null;
        for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(item.getClass())) {
            if (f.getType() != String.class) continue;
            try {
                f.setAccessible(true);
                String val = (String) f.get(item);
                if (val == null || val.isEmpty()) continue;
                if (val.matches("\\d+")) continue;
                if (val.matches("\\d+_\\d+")) continue;
                if (val.startsWith("http")) continue;
                if (bestVal == null || val.length() > bestVal.length()) {
                    bestName = f.getName();
                    bestVal  = val;
                }
            } catch (Throwable ignored) {}
        }
        return (bestName != null) ? new String[]{bestName, bestVal} : null;
    }

    private static boolean isObfuscatedObject(Field f) {
        Class<?> t = f.getType();
        if (t.isPrimitive() || t == String.class) return false;
        String n = t.getName();
        return !n.startsWith("android.") && !n.startsWith("java.")
                && !n.startsWith("kotlin.") && !n.startsWith("androidx.");
    }

    private static boolean hasFieldOfType(Class<?> clazz, Class<?> target) {
        for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(clazz)) {
            if (target.isAssignableFrom(f.getType())) return true;
        }
        return false;
    }

    private static boolean resolveRepoField(Class<?> dcqClass) {
        try {
            Class<?> repoClass = dcqClass.getClassLoader().loadClass(REPO_CLASS);
            for (java.lang.reflect.Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(dcqClass)) {
                if (f.getType() == repoClass) {
                    f.setAccessible(true);
                    repoField = f;
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Object resolveHeldValue(Object repo, Class<?> targetType) {
        for (java.lang.reflect.Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(repo.getClass())) {
            try {
                java.lang.reflect.Method getValue = f.getType().getMethod("getValue");
                f.setAccessible(true);
                Object holder = f.get(repo);
                if (holder == null) continue;
                Object value = getValue.invoke(holder);
                if (targetType.isInstance(value)) return value;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static final XC_MethodHook SHOW_MENU_HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
            if (!FeatureFlags.enableCopyComment) return;
            try {
                Activity ctx = currentActivity;
                if (ctx == null) return;

                String commentId = (String) param.args[0];
                String mediaId = (String) param.args[1];

                Object repo = repoField.get(param.thisObject);
                if (repo == null) return;
                Object heldValue = resolveHeldValue(repo, resolverMethod.getParameterTypes()[0]);
                if (heldValue == null) return;

                Object comment = resolverMethod.invoke(null, heldValue, commentId, mediaId);
                if (comment == null) return;

                String text = findLongestTextField(comment);
                // #242: image / GIF comments get a Download button (GIF-only comments have no text)
                java.util.List<String> media = ps.reso.instaeclipse.mods.media.CommentMedia.urls(ctx, comment);
                if ((text == null || text.trim().isEmpty()) && media.isEmpty()) return;

                String[] names = media.isEmpty() ? new String[2] // {commenter, post author} for the file name
                        : ps.reso.instaeclipse.mods.media.CommentMedia.names(comment, mediaId, heldValue, repo, param.thisObject);
                showCopyPopup(ctx, text == null ? "" : text.trim(), media, commentId, names);
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ hook body – " + t);
            }
        }
    };

    private static String findLongestTextField(Object model) {
        String best = null;
        for (java.lang.reflect.Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(model.getClass())) {
            if (f.getType() != String.class) continue;
            try {
                f.setAccessible(true);
                String val = (String) f.get(model);
                if (val == null || val.isEmpty()) continue;
                if (val.matches("\\d+")) continue;
                if (val.matches("\\d+_\\d+")) continue;
                if (val.startsWith("http")) continue;
                if (best == null || val.length() > best.length()) best = val;
            } catch (Throwable ignored) {}
        }
        return best;
    }

    private static void showCopyPopup(final Context ctx, final String text) {
        showCopyPopup(ctx, text, java.util.Collections.emptyList(), null, new String[2]);
    }

    private static void showCopyPopup(final Context ctx, final String text,
                                      final java.util.List<String> media, final String commentId,
                                      final String[] names) {
        MAIN.post(() -> {
            try {
                IeDialog dlg = new IeDialog(ctx).title(I18n.t(ctx, R.string.ig_comment_copy_title));
                if (!text.isEmpty()) dlg.quote(text, 5);

                java.util.List<CharSequence> labels = new java.util.ArrayList<>();
                java.util.List<Runnable> actions = new java.util.ArrayList<>();
                if (!media.isEmpty()) {
                    labels.add(I18n.t(ctx, R.string.ig_dl_title));
                    actions.add(() -> ps.reso.instaeclipse.mods.media.CommentMedia.download(ctx, media, commentId, names[0], names[1]));
                    // GIF / photo comments: copy the media link (Android can't put an animated GIF on the clipboard)
                    labels.add(I18n.t(ctx, R.string.ig_comment_copy_media_link));
                    actions.add(() -> copyToClipboard(ctx, media.get(0)));
                }
                if (!text.isEmpty()) {
                    labels.add(I18n.t(ctx, R.string.ig_comment_copy_full));
                    actions.add(() -> copyToClipboard(ctx, text));
                    labels.add(I18n.t(ctx, R.string.ig_comment_select_part));
                    actions.add(() -> showSelectDialog(ctx, text));
                    // Translate (#233): hand the text to the user's translation app.
                    labels.add(I18n.t(ctx, R.string.ig_translate_text));
                    actions.add(() -> ps.reso.instaeclipse.utils.core.TextTranslator.translate(ctx, text));
                }
                dlg.choices(labels.toArray(new CharSequence[0]), -1, which -> actions.get(which).run());
                dlg.show();
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ Popup – " + t.getMessage());
            }
        });
    }

    private static void showSelectDialog(final Context ctx, final String text) {
        MAIN.post(() -> {
            try {
                IeDialog dlg = new IeDialog(ctx).title(I18n.t(ctx, R.string.ig_comment_select_title));
                final EditText et = dlg.selectableText(text, 8);
                dlg.secondary(I18n.t(ctx, R.string.ig_mention_copy_all), () -> copyToClipboard(ctx, text));
                dlg.primary(I18n.t(ctx, R.string.ig_comment_copy_selected), () -> {
                    int s = et.getSelectionStart(), e = et.getSelectionEnd();
                    copyToClipboard(ctx, (s >= 0 && e > s) ? text.substring(s, e) : text);
                });
                dlg.show();
                et.requestFocus();
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ SelectDialog – " + t.getMessage());
            }
        });
    }

    private static void copyToClipboard(final Context ctx, final String text) {
        try {
            ClipboardManager cm = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("comment", text));
                MAIN.post(() ->
                        ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_comment_copied),
                                Toast.LENGTH_SHORT).show());
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ Copy – " + t.getMessage());
        }
    }
}
