package ps.reso.instaeclipse.mods.misc;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

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

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    currentActivity = (Activity) p.thisObject;
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
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
                List<ClassData> found = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings("handleCommentUiEffects")));
                if (!found.isEmpty()) {
                    effectHandlerClass = classLoader.loadClass(found.get(0).getName());
                }
            }
            if (effectHandlerClass == null) {
                List<ClassData> found = bridge.findClass(FindClass.create()
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
            for (java.lang.reflect.Field f : effectHandlerClass.getDeclaredFields()) {
                Class<?> t = f.getType();
                for (java.lang.reflect.Method cand : t.getDeclaredMethods()) {
                    Class<?>[] p = cand.getParameterTypes();
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

            List<MethodData> callees = bridge.findMethod(FindMethod.create()
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
        List<MethodData> found = bridge.findMethod(FindMethod.create()
                .matcher(MethodMatcher.create()
                        .name("onLongPress")
                        .usingStrings("fb_comment_long_press")
                )
        );

        if (found.isEmpty()) {
            found = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .name("onLongPress")
                            .usingStrings("comment_row_component")
                    )
            );
        }

        if (found.isEmpty()) {
            List<ClassData> classes = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings("fb_comment_long_press")
                    )
            );
            for (ClassData cd : classes) {
                found.addAll(bridge.findMethod(FindMethod.create()
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
        protected void beforeHookedMethod(MethodHookParam param) {
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
            for (Field f1 : gestureListener.getClass().getDeclaredFields()) {
                if (!isObfuscatedObject(f1)) continue;
                f1.setAccessible(true);
                Object holder = f1.get(gestureListener);
                if (holder == null) continue;
                for (Field f2 : holder.getClass().getDeclaredFields()) {
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

            for (Field f1 : gestureListener.getClass().getDeclaredFields()) {
                if (!isObfuscatedObject(f1)) continue;
                f1.setAccessible(true);
                Object holder = f1.get(gestureListener);
                if (holder == null) continue;

                for (Field f2 : holder.getClass().getDeclaredFields()) {
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
        for (Field f : item.getClass().getDeclaredFields()) {
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
        for (Field f : clazz.getDeclaredFields()) {
            if (target.isAssignableFrom(f.getType())) return true;
        }
        return false;
    }

    private static boolean resolveRepoField(Class<?> dcqClass) {
        try {
            Class<?> repoClass = dcqClass.getClassLoader().loadClass(REPO_CLASS);
            for (java.lang.reflect.Field f : dcqClass.getDeclaredFields()) {
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
        for (java.lang.reflect.Field f : repo.getClass().getDeclaredFields()) {
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
        protected void beforeHookedMethod(MethodHookParam param) {
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
                if (text == null || text.trim().isEmpty()) return;

                showCopyPopup(ctx, text.trim());
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ hook body – " + t);
            }
        }
    };

    private static String findLongestTextField(Object model) {
        String best = null;
        for (java.lang.reflect.Field f : model.getClass().getDeclaredFields()) {
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

    private static boolean isDarkTheme(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static GradientDrawable roundRect(int color, float radiusDp, Context ctx) {
        float r = radiusDp * ctx.getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(r);
        return d;
    }

    private static Button makeButton(Context ctx, String label,
                                     int bgColor, int textColor, float dp) {
        Button btn = new Button(ctx);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackground(roundRect(bgColor, 14, ctx));
        btn.setAllCaps(false);
        btn.setPadding((int)(20 * dp), (int)(14 * dp), (int)(20 * dp), (int)(14 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int)(10 * dp);
        btn.setLayoutParams(lp);
        return btn;
    }

    private static void showCopyPopup(final Context ctx, final String text) {
        MAIN.post(() -> {
            try {
                float dp = ctx.getResources().getDisplayMetrics().density;
                boolean dark = isDarkTheme(ctx);

                int sheetBg    = dark ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
                int cardBg     = dark ? Color.parseColor("#2C2C2E") : Color.parseColor("#FFFFFF");
                int textPrim   = dark ? Color.WHITE                 : Color.parseColor("#1C1C1E");
                int accentBg   = Color.parseColor("#0A84FF");
                int secondBg   = dark ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
                int secondText = dark ? Color.WHITE                 : Color.parseColor("#1C1C1E");
                int handleClr  = dark ? Color.parseColor("#48484A") : Color.parseColor("#C7C7CC");

                LinearLayout sheet = new LinearLayout(ctx);
                sheet.setOrientation(LinearLayout.VERTICAL);
                sheet.setBackground(roundRect(sheetBg, 20, ctx));
                int hPad = (int)(20 * dp);
                sheet.setPadding(hPad, (int)(12 * dp), hPad, (int)(28 * dp));

                View handle = new View(ctx);
                LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                        (int)(40 * dp), (int)(4 * dp));
                handleLp.gravity = Gravity.CENTER_HORIZONTAL;
                handleLp.bottomMargin = (int)(16 * dp);
                handle.setLayoutParams(handleLp);
                handle.setBackground(roundRect(handleClr, 2, ctx));
                sheet.addView(handle);

                TextView titleTv = new TextView(ctx);
                titleTv.setText(I18n.t(ctx, R.string.ig_comment_copy_title));
                titleTv.setTextColor(textPrim);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                titleTv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = (int)(14 * dp);
                titleTv.setLayoutParams(titleLp);
                sheet.addView(titleTv);

                TextView commentTv = new TextView(ctx);
                commentTv.setText(text);
                commentTv.setTextColor(textPrim);
                commentTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                commentTv.setMaxLines(5);
                commentTv.setEllipsize(TextUtils.TruncateAt.END);
                int cardPad = (int)(14 * dp);
                commentTv.setPadding(cardPad, cardPad, cardPad, cardPad);
                commentTv.setBackground(roundRect(cardBg, 12, ctx));
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = (int)(6 * dp);
                commentTv.setLayoutParams(cardLp);
                sheet.addView(commentTv);

                Dialog dialog = new Dialog(ctx);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                Button btnCopy = makeButton(ctx,
                        I18n.t(ctx, R.string.ig_comment_copy_full), accentBg, Color.WHITE, dp);
                btnCopy.setOnClickListener(v -> {
                    dialog.dismiss();
                    copyToClipboard(ctx, text);
                });
                sheet.addView(btnCopy);

                Button btnSelect = makeButton(ctx,
                        I18n.t(ctx, R.string.ig_comment_select_part), secondBg, secondText, dp);
                btnSelect.setOnClickListener(v -> {
                    dialog.dismiss();
                    showSelectDialog(ctx, text);
                });
                sheet.addView(btnSelect);

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
                ModuleLog.line("(InstaEclipse | CopyComment): ❌ Popup – " + t.getMessage());
            }
        });
    }

    private static void showSelectDialog(final Context ctx, final String text) {
        MAIN.post(() -> {
            try {
                float dp = ctx.getResources().getDisplayMetrics().density;
                boolean dark = isDarkTheme(ctx);

                int sheetBg  = dark ? Color.parseColor("#1C1C1E") : Color.parseColor("#F2F2F7");
                int cardBg   = dark ? Color.parseColor("#2C2C2E") : Color.parseColor("#FFFFFF");
                int textPrim = dark ? Color.WHITE                 : Color.parseColor("#1C1C1E");
                int accentBg = Color.parseColor("#0A84FF");
                int secondBg = dark ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA");
                int handleClr= dark ? Color.parseColor("#48484A") : Color.parseColor("#C7C7CC");

                LinearLayout sheet = new LinearLayout(ctx);
                sheet.setOrientation(LinearLayout.VERTICAL);
                sheet.setBackground(roundRect(sheetBg, 20, ctx));
                int hPad = (int)(20 * dp);
                sheet.setPadding(hPad, (int)(12 * dp), hPad, (int)(28 * dp));

                View handle = new View(ctx);
                LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                        (int)(40 * dp), (int)(4 * dp));
                handleLp.gravity = Gravity.CENTER_HORIZONTAL;
                handleLp.bottomMargin = (int)(16 * dp);
                handle.setLayoutParams(handleLp);
                handle.setBackground(roundRect(handleClr, 2, ctx));
                sheet.addView(handle);

                TextView titleTv = new TextView(ctx);
                titleTv.setText(I18n.t(ctx, R.string.ig_comment_select_title));
                titleTv.setTextColor(textPrim);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                titleTv.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                titleLp.bottomMargin = (int)(14 * dp);
                titleTv.setLayoutParams(titleLp);
                sheet.addView(titleTv);

                EditText et = new EditText(ctx);
                et.setText(text);
                et.setTextColor(textPrim);
                et.setTextIsSelectable(true);
                et.setFocusableInTouchMode(true);
                et.setInputType(android.text.InputType.TYPE_NULL);
                et.setKeyListener(null);
                et.setHorizontallyScrolling(false);
                et.setMaxLines(8);
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                int cardPad = (int)(14 * dp);
                et.setPadding(cardPad, cardPad, cardPad, cardPad);
                et.setBackground(roundRect(cardBg, 12, ctx));
                et.setSelection(0, text.length());
                LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                etLp.bottomMargin = (int)(6 * dp);
                et.setLayoutParams(etLp);
                sheet.addView(et);

                Dialog dialog = new Dialog(ctx);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                Button btnCopySel = makeButton(ctx,
                        I18n.t(ctx, R.string.ig_comment_copy_selected), accentBg, Color.WHITE, dp);
                btnCopySel.setOnClickListener(v -> {
                    int s = et.getSelectionStart(), e = et.getSelectionEnd();
                    String sel = (s >= 0 && e > s) ? text.substring(s, e) : text;
                    dialog.dismiss();
                    copyToClipboard(ctx, sel);
                });
                sheet.addView(btnCopySel);

                Button btnCopyAll = makeButton(ctx,
                        I18n.t(ctx, R.string.ig_mention_copy_all), secondBg,
                        dark ? Color.WHITE : Color.parseColor("#1C1C1E"), dp);
                btnCopyAll.setOnClickListener(v -> {
                    dialog.dismiss();
                    copyToClipboard(ctx, text);
                });
                sheet.addView(btnCopyAll);

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
                        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_comment_copied),
                                Toast.LENGTH_SHORT).show());
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ Copy – " + t.getMessage());
        }
    }
}
