package ps.reso.instaeclipse.mods.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.core.IgDex;

public class IgThemeHook {

    private static final long REFRESH_DEBOUNCE_MS = 450L;

    private static volatile boolean installed;
    private static volatile boolean loggedZeroColor;
    private static volatile Field typedArrayResourcesField;
    private static volatile Context hostContext;
    private static volatile String appliedSignature;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable REFRESH = IgThemeHook::performRefresh;

    public void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            hookResolveAttribute(classLoader);
            hookGetColor(classLoader);
            hookContextGetColor();
            hookTypedArrayGetColor(classLoader);
            hookTypedArrayGetColorStateList(classLoader);
            hookColorStateList();
            hookViewColors();
            hookTextViewColors();
            hookDrawableColors();
            hookImageViewTint();
            hookWindowColors();
            hookComposeColors(classLoader);
            hookNativeColors(classLoader);
            hookWidgetTints();
            hookIgdsColorProviders(classLoader);
            hookActivityLifecycle();
            hookPhoneWindowColors(classLoader);
            hookTabIconContrast();
            installed = true;
            FeatureStatusTracker.setHooked("CustomTheme");
            ModuleLog.line("(InstaEclipse | Theme): hooks installed enabled=" + FeatureFlags.customThemeEnabled);
            if (FeatureFlags.customThemeEnabled) {
                buildFromApplication();
                MAIN.post(() -> {
                    try {
                        buildFromApplication();
                        Activity activity = UIHookManager.getCurrentActivity();
                        if (activity != null && !activity.isFinishing()) {
                            IgColorRemapEngine.ensureBuilt(activity);
                            applyWindowColors(activity);
                        }
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | Theme): deferred build failed", t);
                    }
                });
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Theme): hook failed", t);
        }
    }

    private static void buildFromApplication() {
        try {
            Context app = hostContext();
            if (app == null) {
                ModuleLog.line("(InstaEclipse | Theme): no Application yet; deferring remap build");
                return;
            }
            IgThemeEngine.ensureInitialized(app);
            IgColorRemapEngine.ensureBuilt(app);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Theme): early build failed", t);
        }
    }

    private static Context hostContext() {
        Context ctx = hostContext;
        if (ctx != null) return ctx;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                hostContext = (Context) app;
                return hostContext;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void ensureReady(Resources res, ClassLoader cl) {
        if (!IgThemeEngine.isInitialized() && res != null) IgThemeEngine.ensureInitialized(res, cl);
        if (!IgColorRemapEngine.isReady()) {
            Activity activity = UIHookManager.getCurrentActivity();
            IgColorRemapEngine.ensureBuilt(activity != null ? activity : hostContext());
        }
    }

    private void hookResolveAttribute(final ClassLoader cl) {
        XposedHelpers.findAndHookMethod("android.content.res.Resources$Theme", cl, "resolveAttribute",
                int.class, TypedValue.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
                        if (!Boolean.TRUE.equals(param.getResult()) || !Boolean.TRUE.equals(param.args[2])) return;
                        int attrId = (Integer) param.args[0];
                        TypedValue out = (TypedValue) param.args[1];
                        if (out == null) return;
                        if (!IgThemeEngine.isInitialized()) {
                            try {
                                Resources res = ((Resources.Theme) param.thisObject).getResources();
                                if (res != null) IgThemeEngine.ensureInitialized(res, cl);
                            } catch (Throwable ignored) {}
                        }
                        if (IgThemeEngine.applyAttrOverride(attrId, out)) return;
                        if (IgColorRemapEngine.isReady()
                                && out.type >= TypedValue.TYPE_FIRST_COLOR_INT && out.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                            out.data = IgColorRemapEngine.remap(out.data);
                        }
                    }
                });
    }

    private void hookGetColor(final ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive()) return;
                int resId = (Integer) param.args[0];
                if (resId == 0) {
                    if (!loggedZeroColor) {
                        loggedZeroColor = true;
                        ModuleLog.line("(InstaEclipse | Theme): getColor(0) intercepted", new Throwable());
                    }
                    param.setResult(IgThemeEngine.getActivePalette().primaryText);
                    return;
                }
                if (!IgThemeEngine.looksLikeResourceId(resId)) return;
                ensureReady((Resources) param.thisObject, cl);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                overrideOrRemapResult(param);
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, hook));
        tryHook(() -> XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, hook));
    }

    private void hookContextGetColor() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || isModuleContext(param.thisObject)) return;
                int resId = (Integer) param.args[0];
                if (!IgThemeEngine.looksLikeResourceId(resId)) return;
                Context ctx = (Context) param.thisObject;
                ensureReady(ctx.getResources(), ctx.getClassLoader());
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isModuleContext(param.thisObject)) return;
                overrideOrRemapResult(param);
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(Context.class, "getColor", int.class, hook));
    }

    private static boolean isModuleContext(Object target) {
        try {
            return target instanceof Context
                    && CommonUtils.MY_PACKAGE_NAME.equals(((Context) target).getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void overrideOrRemapResult(XC_MethodHook.MethodHookParam param) {
        if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
        Object result = param.getResult();
        if (!(result instanceof Integer)) return;
        int resId = (Integer) param.args[0];
        Integer override = IgThemeEngine.looksLikeResourceId(resId) ? IgThemeEngine.colorForResource(resId) : null;
        if (override != null) {
            param.setResult(IgThemeEngine.keepAlpha((Integer) result, override));
            return;
        }
        remapIntResult(param);
    }

    private static void remapIntResult(XC_MethodHook.MethodHookParam param) {
        if (!IgThemeEngine.isActive() || param.getThrowable() != null || !IgColorRemapEngine.isReady()) return;
        Object result = param.getResult();
        if (!(result instanceof Integer)) return;
        int resolved = (Integer) result;
        int remapped = IgColorRemapEngine.remap(resolved);
        if (remapped != resolved) param.setResult(remapped);
    }

    private void hookTypedArrayGetColor(final ClassLoader cl) {
        XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
                try {
                    TypedArray ta = (TypedArray) param.thisObject;
                    int index = (Integer) param.args[0];
                    if (!IgThemeEngine.isInitialized()) {
                        Resources res = typedArrayResources(ta);
                        if (res != null) IgThemeEngine.ensureInitialized(res, cl);
                    }
                    int resId = ta.getResourceId(index, 0);
                    Integer override = resId != 0 ? IgThemeEngine.colorForResource(resId) : null;
                    if (override != null && param.getResult() instanceof Integer) {
                        param.setResult(IgThemeEngine.keepAlpha((Integer) param.getResult(), override));
                        return;
                    }
                    remapIntResult(param);
                } catch (Throwable ignored) {}
            }
        });
    }

    private void hookTypedArrayGetColorStateList(final ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (result instanceof ColorStateList) {
                    ColorStateList remapped = IgColorRemapEngine.remapColorStateList((ColorStateList) result);
                    if (remapped != result) param.setResult(remapped);
                }
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(TypedArray.class, "getColorStateList", int.class, hook));
        tryHook(() -> XposedHelpers.findAndHookMethod(Resources.class, "getColorStateList", int.class, Resources.Theme.class, hook));
        tryHook(() -> XposedHelpers.findAndHookMethod(Resources.class, "getColorStateList", int.class, hook));
        tryHook(() -> XposedHelpers.findAndHookMethod(Context.class, "getColorStateList", int.class, hook));
        tryHook(() -> XposedHelpers.findAndHookMethod("androidx.core.content.ContextCompat", cl, "getColor", Context.class, int.class, colorResultHook()));
        tryHook(() -> XposedHelpers.findAndHookMethod("androidx.core.content.res.ResourcesCompat", cl, "getColor", Resources.class, int.class, Resources.Theme.class, colorResultHook()));
    }

    private XC_MethodHook colorResultHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                remapIntResult(param);
            }
        };
    }

    private void hookColorStateList() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ColorStateList.class, "valueOf", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive()) return;
                if (param.args[0] instanceof Integer && IgColorRemapEngine.isProtectedModuleColor((Integer) param.args[0])) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
    }

    private static XC_MethodHook viewIntArgHook(int index) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, index);
            }
        };
    }

    private static XC_MethodHook viewCslArgHook(int index) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[index] instanceof ColorStateList) {
                    param.args[index] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[index]);
                }
            }
        };
    }

    private static XC_MethodHook drawableIntArgHook(int index) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || !(param.args[index] instanceof Integer)) return;
                if (IgColorRemapEngine.shouldSkipDrawable(param.thisObject, (Integer) param.args[index])) return;
                IgColorRemapEngine.applyRemapArg(param.args, index);
            }
        };
    }

    private static XC_MethodHook drawableCslArgHook(int index) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || !(param.args[index] instanceof ColorStateList)) return;
                ColorStateList csl = (ColorStateList) param.args[index];
                if (IgColorRemapEngine.shouldSkipDrawable(param.thisObject, csl.getDefaultColor())) return;
                param.args[index] = IgColorRemapEngine.remapColorStateList(csl);
            }
        };
    }

    private void hookViewColors() {
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setForegroundTintList", ColorStateList.class, viewCslArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackgroundTintList", ColorStateList.class, viewCslArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackground", android.graphics.drawable.Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorDrawable) {
                    ColorDrawable drawable = (ColorDrawable) param.args[0];
                    int original = drawable.getColor();
                    int remapped = IgColorRemapEngine.remap(original);
                    if (remapped != original) {
                        IgColorRemapEngine.withBypass(() -> ((ColorDrawable) drawable.mutate()).setColor(remapped));
                    }
                }
            }
        }));
    }

    private void hookTextViewColors() {
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", ColorStateList.class, viewCslArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHintTextColor", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHintTextColor", ColorStateList.class, viewCslArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setLinkTextColor", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setLinkTextColor", ColorStateList.class, viewCslArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHighlightColor", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setShadowLayer", float.class, float.class, float.class, int.class, viewIntArgHook(3)));
    }

    private void hookDrawableColors() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, drawableIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, drawableIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColors", int[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || !(param.args[0] instanceof int[])) return;
                int[] colors = (int[]) param.args[0];
                if (colors.length == 0 || IgColorRemapEngine.shouldSkipDrawable(param.thisObject, colors[0])) return;
                int[] remapped = IgColorRemapEngine.remapIntArray(colors);
                if (remapped != colors) param.args[0] = remapped;
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setStroke", int.class, int.class, drawableIntArgHook(1)));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setStroke", int.class, ColorStateList.class, drawableCslArgHook(1)));
        tryHook(() -> XposedHelpers.findAndHookMethod(RippleDrawable.class, "setColor", ColorStateList.class, drawableCslArgHook(0)));
    }

    private void hookImageViewTint() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter", int.class, PorterDuff.Mode.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter", int.class, viewIntArgHook(0)));
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setImageTintList", ColorStateList.class, viewCslArgHook(0)));
    }

    private static volatile int tabIconId;

    private void hookTabIconContrast() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "drawableStateChanged", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive()) return;
                checkTabIcon((ImageView) param.thisObject);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setImageTintList", ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                checkTabIcon((ImageView) param.thisObject);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setSelected", boolean.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || !(param.thisObject instanceof ViewGroup)) return;
                int id = tabIconId;
                if (id == 0) return;
                View icon = ((ViewGroup) param.thisObject).findViewById(id);
                if (icon instanceof ImageView) checkTabIcon((ImageView) icon);
            }
        }));
    }

    private static void checkTabIcon(ImageView icon) {
        if (!isTabIcon(icon) || !(icon.getParent() instanceof View)) return;
        ensureTabIconContrast(icon, (View) icon.getParent());
        icon.post(() -> {
            if (IgThemeEngine.isActive() && icon.getParent() instanceof View) {
                ensureTabIconContrast(icon, (View) icon.getParent());
            }
        });
    }

    private static boolean isTabIcon(ImageView view) {
        int id = view.getId();
        if (id == View.NO_ID) return false;
        int tab = tabIconId;
        if (tab == 0) {
            tab = view.getResources().getIdentifier("tab_icon", "id", CommonUtils.IG_PACKAGE_NAME);
            if (tab == 0) return false;
            tabIconId = tab;
        }
        return id == tab;
    }

    private static void ensureTabIconContrast(ImageView icon, View tab) {
        ColorStateList tint = icon.getImageTintList();
        if (tint == null) return;
        Integer fill = fillColor(tab.getBackground(), tab.getDrawableState());
        if (fill == null || Color.alpha(fill) < 0x80) return;
        int[] state = icon.getDrawableState();
        int current = tint.getColorForState(state, tint.getDefaultColor());
        if (IgThemeContrast.contrastRatio(current | 0xFF000000, fill | 0xFF000000) >= 2.0f) return;
        IgThemePalette palette = IgThemeEngine.getActivePalette();
        int light = isLight(palette.background) ? palette.background : palette.primaryText;
        int dark = isLight(palette.background) ? palette.primaryText : palette.background;
        int wanted = isLight(fill) ? dark : light;
        int[] spec = icon.isSelected() ? new int[]{android.R.attr.state_selected} : state;
        ColorStateList fixed = new ColorStateList(new int[][]{spec, new int[0]},
                new int[]{wanted, tint.getDefaultColor()});
        IgColorRemapEngine.withBypass(() -> icon.setImageTintList(fixed));
    }

    private static Integer fillColor(android.graphics.drawable.Drawable d, int[] state) {
        for (int depth = 0; d != null && depth < 6; depth++) {
            if (d instanceof ColorDrawable) return ((ColorDrawable) d).getColor();
            if (d instanceof GradientDrawable) {
                ColorStateList c = ((GradientDrawable) d).getColor();
                return c != null ? c.getColorForState(state, c.getDefaultColor()) : null;
            }
            if (d instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) d;
                if (ld.getNumberOfLayers() == 0) return null;
                d = ld.getDrawable(0);
                continue;
            }
            android.graphics.drawable.Drawable current = d.getCurrent();
            if (current == d) return null;
            d = current;
        }
        return null;
    }

    private void hookWidgetTints() {
        XC_MethodHook cslHook = viewCslArgHook(0);
        tryHook(() -> XposedHelpers.findAndHookMethod(CompoundButton.class, "setButtonTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setProgressTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setIndeterminateTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setProgressBackgroundTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod("android.widget.AbsSeekBar", null, "setThumbTintList", ColorStateList.class, cslHook));
    }

    private static XC_MethodHook barColorHook(boolean statusBar) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || !(param.args[0] instanceof Integer)) return;
                if (Color.alpha((Integer) param.args[0]) != 0xFF) return;
                IgThemePalette palette = IgThemeEngine.getActivePalette();
                param.args[0] = statusBar ? palette.statusBar : palette.navigation;
            }
        };
    }

    private void hookWindowColors() {
        tryHook(() -> XposedHelpers.findAndHookMethod(Window.class, "setStatusBarColor", int.class, barColorHook(true)));
        tryHook(() -> XposedHelpers.findAndHookMethod(Window.class, "setNavigationBarColor", int.class, barColorHook(false)));
    }

    private void hookPhoneWindowColors(ClassLoader cl) {
        if (!tryHookPhoneWindow(cl)) tryHookPhoneWindow(null);
    }

    private boolean tryHookPhoneWindow(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.android.internal.policy.PhoneWindow", cl, "setStatusBarColor", int.class, barColorHook(true));
            XposedHelpers.findAndHookMethod("com.android.internal.policy.PhoneWindow", cl, "setNavigationBarColor", int.class, barColorHook(false));
            ModuleLog.line("(InstaEclipse | Theme): PhoneWindow color hooks installed");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hookComposeColors(ClassLoader cl) {
        XC_MethodHook packedHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (!(result instanceof Long)) return;
                long packed = (Long) result;
                long remapped = IgColorRemapEngine.remapComposePacked(packed);
                if (remapped != packed) param.setResult(remapped);
            }
        };
        for (String className : IgColorRemapEngine.COMPOSE_PALETTE_CLASSES) {
            try {
                Class<?> cls = cl.loadClass(className);
                for (Method method : cls.getDeclaredMethods()) {
                    if (method.getReturnType() != long.class || method.getParameterTypes().length != 0) continue;
                    XposedBridge.hookMethod(method, packedHook);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void hookIgdsColorProviders(ClassLoader cl) {
        if (Module.dexKitBridge == null) return;
        XC_MethodHook intHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                remapIntResult(param);
            }
        };
        String[] needles = {
                "igds_primary_background", "igds_color_primary_background",
                "igds_primary_text", "igds_color_primary_text",
                "igds_secondary_background", "igds_elevated_background",
                "igds_primary_icon", "igds_color_primary_icon",
                "igds_separator", "igds_color_separator",
                "igds_link", "igds_color_link",
                "igds_error_or_destructive", "igds_color_error_or_destructive",
                "igds_elevated_highlight_background", "igds_secondary_text"
        };
        int hooked = 0;
        for (String needle : needles) {
            if (hooked >= 48) break;
            try {
                List<MethodData> methods = IgDex.findMethod(Module.dexKitBridge, 
                        FindMethod.create().matcher(MethodMatcher.create().usingStrings(needle)));
                for (MethodData methodData : methods) {
                    if (hooked >= 48) break;
                    try {
                        Method method = methodData.getMethodInstance(cl);
                        if (method.getReturnType() != int.class || method.getParameterTypes().length > 1) continue;
                        XposedBridge.hookMethod(method, intHook);
                        hooked++;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        if (hooked > 0) {
            ModuleLog.line("(InstaEclipse | Theme): IGDS color providers hooked=" + hooked);
        }
    }

    private void hookNativeColors(ClassLoader cl) {
        XC_MethodHook mapHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (result instanceof Map) {
                    Map<?, ?> remapped = IgColorRemapEngine.remapNativeColorMap((Map<?, ?>) result);
                    if (remapped != result) param.setResult(remapped);
                }
            }
        };
        try {
            Class<?> spec = cl.loadClass("com.facebook.fbreact.specs.NativeIGNativeColorsSpec");
            tryHook(() -> XposedHelpers.findAndHookMethod(spec, "getTypedExportedConstants", mapHook));
            try {
                if (Module.dexKitBridge != null) {
                    List<ClassData> classes = IgDex.findClass(Module.dexKitBridge, 
                            FindClass.create().matcher(ClassMatcher.create().usingStrings("IGNativeColors")));
                    for (ClassData classData : classes) {
                        try {
                            Class<?> impl = cl.loadClass(classData.getName());
                            if (!spec.isAssignableFrom(impl) && !classData.getName().contains("NativeColors")) continue;
                            tryHook(() -> XposedHelpers.findAndHookMethod(impl, "getTypedExportedConstants", mapHook));
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void tryHook(HookAttempt attempt) {
        try {
            attempt.run();
        } catch (Throwable ignored) {}
    }

    private interface HookAttempt {
        void run() throws Throwable;
    }

    private void hookActivityLifecycle() {
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (hostContext == null) {
                    Context app = activity.getApplicationContext();
                    hostContext = app != null ? app : activity;
                }
                if (appliedSignature == null) appliedSignature = currentSignature();
                if (!IgThemeEngine.isActive()) return;
                IgThemeEngine.ensureInitialized(activity);
                IgColorRemapEngine.checkSourceMode(activity);
                IgColorRemapEngine.ensureBuilt(activity);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (IgThemeEngine.isActive()) applyWindowColors((Activity) param.thisObject);
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive()) return;
                Activity activity = (Activity) param.thisObject;
                IgThemeEngine.ensureInitialized(activity);
                IgColorRemapEngine.ensureBuilt(activity);
                applyWindowColors(activity);
            }
        });
    }

    public static void refreshCurrentActivity() {
        MAIN.removeCallbacks(REFRESH);
        MAIN.postDelayed(REFRESH, REFRESH_DEBOUNCE_MS);
    }

    private static void performRefresh() {
        String signature = currentSignature();
        String previous = appliedSignature;
        if (signature.equals(previous)) return;
        appliedSignature = signature;
        IgThemeEngine.invalidate();
        if (previous == null) return;
        Activity activity = UIHookManager.getCurrentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            if (FeatureFlags.customThemeEnabled) {
                IgThemeEngine.ensureInitialized(activity);
                IgColorRemapEngine.ensureBuilt(activity);
            }
            ModuleLog.line("(InstaEclipse | Theme): theme changed; recreating " + activity.getClass().getSimpleName());
            activity.recreate();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Theme): refresh failed", t);
        }
    }

    private static String currentSignature() {
        if (!FeatureFlags.customThemeEnabled) return "off";
        try {
            return ThemeSettingsHelper.resolveEffectivePalette().toJson();
        } catch (Throwable t) {
            return "on";
        }
    }

    @SuppressWarnings("deprecation")
    static void applyWindowColors(Activity activity) {
        if (activity == null || !IgThemeEngine.isActive()) return;
        try {
            IgThemePalette palette = IgThemeEngine.getActivePalette();
            Window window = activity.getWindow();
            if (window == null) return;
            boolean themeStatus = Color.alpha(window.getStatusBarColor()) == 0xFF;
            boolean themeNav = Color.alpha(window.getNavigationBarColor()) == 0xFF;
            if (themeStatus) window.setStatusBarColor(palette.statusBar);
            if (themeNav) window.setNavigationBarColor(palette.navigation);
            View decor = window.getDecorView();
            if (decor != null) decor.setBackgroundColor(palette.background);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            boolean lightStatus = isLight(themeStatus ? palette.statusBar : palette.background);
            boolean lightNav = isLight(themeNav ? palette.navigation : palette.background);
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int appearance = (lightStatus ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                            | (lightNav ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0);
                    controller.setSystemBarsAppearance(appearance,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else {
                applyLegacySystemUiVisibility(window, lightStatus, lightNav);
            }
            IgThemeViewSweep.attach(activity);
        } catch (Throwable ignored) {}
    }

    private static boolean isLight(int color) {
        return IgThemeContrast.contrastRatio(color, Color.BLACK) > IgThemeContrast.contrastRatio(color, Color.WHITE);
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacySystemUiVisibility(Window window, boolean lightStatus, boolean lightNav) {
        int flags = window.getDecorView().getSystemUiVisibility();
        flags = lightStatus ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags = lightNav ? flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private static Resources typedArrayResources(TypedArray ta) {
        try {
            Field field = typedArrayResourcesField;
            if (field == null) {
                field = TypedArray.class.getDeclaredField("mResources");
                field.setAccessible(true);
                typedArrayResourcesField = field;
            }
            return (Resources) field.get(ta);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
