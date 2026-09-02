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
import android.util.TypedValue;
import android.view.View;
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
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class IgThemeHook {

    private static volatile boolean installed;
    private static volatile Field typedArrayAttrsField;
    private static volatile Field typedArrayResourcesField;

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
            installed = true;
            FeatureStatusTracker.setHooked("CustomTheme");
            ModuleLog.line("(InstaEclipse | Theme): hooks installed enabled=" + FeatureFlags.customThemeEnabled);
            if (FeatureFlags.customThemeEnabled) {
                try {
                    Context app = currentApplicationContext();
                    if (app != null) {
                        IgThemeEngine.ensureInitialized(app.getApplicationContext() != null ? app.getApplicationContext() : app);
                        IgColorRemapEngine.ensureBuilt(app);
                    } else {
                        ModuleLog.line("(InstaEclipse | Theme): no Application yet; deferring remap build");
                    }
                } catch (Throwable t) {
                    ModuleLog.line("(InstaEclipse | Theme): early build failed", t);
                }
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                Runnable refresh = () -> {
                    try {
                        Context app = currentApplicationContext();
                        if (app != null) {
                            IgThemeEngine.ensureInitialized(app);
                            IgColorRemapEngine.ensureBuilt(app);
                        }
                        refreshCurrentActivity();
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | Theme): deferred refresh failed", t);
                    }
                };
                handler.post(refresh);
                handler.postDelayed(refresh, 1200L);
                handler.postDelayed(refresh, 3000L);
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Theme): hook failed", t);
        }
    }

    private static Context currentApplicationContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            Object app = at.getMethod("getApplication").invoke(thread);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hookResolveAttribute(final ClassLoader cl) {
        XposedHelpers.findAndHookMethod("android.content.res.Resources$Theme", cl, "resolveAttribute",
                int.class, TypedValue.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (IgColorRemapEngine.isBypassing() || !FeatureFlags.customThemeEnabled) return;
                        int attrId = (Integer) param.args[0];
                        TypedValue out = (TypedValue) param.args[1];
                        if (out == null) return;
                        if (!IgThemeEngine.isInitialized()) {
                            try {
                                Resources res = (Resources) XposedHelpers.getObjectField(param.thisObject, "mResources");
                                if (res != null) IgThemeEngine.ensureInitialized(res, cl);
                            } catch (Throwable ignored) {}
                        }
                        Integer override = IgThemeEngine.colorForAttr(attrId);
                        if (override != null) {
                            IgThemeEngine.applyAttrOverride(attrId, out);
                            param.setResult(true);
                        } else if (IgColorRemapEngine.isReady()) {
                            if ((out.type == TypedValue.TYPE_INT_COLOR_ARGB8 || out.type == TypedValue.TYPE_INT_COLOR_RGB8)) {
                                int remapped = IgColorRemapEngine.remap(out.data);
                                if (remapped != out.data) {
                                    out.data = remapped;
                                    param.setResult(true);
                                }
                            }
                        }
                    }
                });
    }

    private void hookGetColor(final ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.customThemeEnabled || IgColorRemapEngine.isBypassing()) return;
                int resId = (Integer) param.args[0];
                if (IgThemeEngine.looksLikeDirectColor(resId)) {
                    param.setResult(IgColorRemapEngine.remap(resId));
                    return;
                }
                if (IgThemeEngine.looksLikeResourceId(resId)) {
                    if (!IgThemeEngine.isInitialized()) {
                        Resources res = (Resources) param.thisObject;
                        IgThemeEngine.ensureInitialized(res, cl);
                    }
                    Activity activity = UIHookManager.getCurrentActivity();
                    if (activity != null) IgColorRemapEngine.ensureBuilt(activity);
                    Integer override = IgThemeEngine.colorForResource(resId);
                    if (override != null) param.setResult(override);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.customThemeEnabled || IgColorRemapEngine.isBypassing()
                        || param.getThrowable() != null || !IgColorRemapEngine.isReady()) return;
                int resId = (Integer) param.args[0];
                if (!IgThemeEngine.looksLikeDirectColor(resId) && IgThemeEngine.looksLikeResourceId(resId)
                        && IgThemeEngine.colorForResource(resId) == null) {
                    Object result = param.getResult();
                    if (result instanceof Integer) {
                        int resolved = (Integer) result;
                        int remapped = IgColorRemapEngine.remap(resolved);
                        if (remapped != resolved) param.setResult(remapped);
                    }
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, hook);
        } catch (Throwable ignored) {}
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, hook);
        } catch (Throwable ignored) {}
    }

    private void hookContextGetColor() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.customThemeEnabled || IgColorRemapEngine.isBypassing()) return;
                int resId = (Integer) param.args[0];
                if (IgThemeEngine.looksLikeDirectColor(resId)) {
                    param.setResult(IgColorRemapEngine.remap(resId));
                    return;
                }
                if (IgThemeEngine.looksLikeResourceId(resId)) {
                    if (!IgThemeEngine.isInitialized()) {
                        Context ctx = (Context) param.thisObject;
                        IgThemeEngine.ensureInitialized(ctx);
                    }
                    Context ctx = (Context) param.thisObject;
                    if (ctx instanceof Activity) {
                        IgColorRemapEngine.ensureBuilt(ctx);
                    } else {
                        Activity activity = UIHookManager.getCurrentActivity();
                        if (activity != null) IgColorRemapEngine.ensureBuilt(activity);
                    }
                    Integer override = IgThemeEngine.colorForResource(resId);
                    if (override != null) param.setResult(override);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.customThemeEnabled || IgColorRemapEngine.isBypassing()
                        || param.getThrowable() != null || !IgColorRemapEngine.isReady()) return;
                int resId = (Integer) param.args[0];
                if (!IgThemeEngine.looksLikeDirectColor(resId) && IgThemeEngine.looksLikeResourceId(resId)
                        && IgThemeEngine.colorForResource(resId) == null) {
                    Object result = param.getResult();
                    if (result instanceof Integer) {
                        int resolved = (Integer) result;
                        int remapped = IgColorRemapEngine.remap(resolved);
                        if (remapped != resolved) param.setResult(remapped);
                    }
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Context.class, "getColor", int.class, hook);
        } catch (Throwable ignored) {}
    }

    private void hookTypedArrayGetColor(final ClassLoader cl) {
        XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (IgColorRemapEngine.isBypassing() || !FeatureFlags.customThemeEnabled) return;
                try {
                    TypedArray ta = (TypedArray) param.thisObject;
                    int index = (Integer) param.args[0];
                    int[] attrs = typedArrayAttributes(ta);
                    if (attrs == null || index < 0 || index >= attrs.length) return;
                    if (!IgThemeEngine.isInitialized()) {
                        Resources res = typedArrayResources(ta);
                        if (res != null) IgThemeEngine.ensureInitialized(res, cl);
                    }
                    Integer override = IgThemeEngine.colorForAttr(attrs[index]);
                    if (override != null) {
                        param.setResult(override);
                        return;
                    }
                    if (IgColorRemapEngine.isReady()) {
                        Object result = param.getResult();
                        if (result instanceof Integer) {
                            int resolved = (Integer) result;
                            int remapped = IgColorRemapEngine.remap(resolved);
                            if (remapped != resolved) param.setResult(remapped);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private void hookTypedArrayGetColorStateList(final ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
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
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (result instanceof Integer) {
                    int resolved = (Integer) result;
                    int remapped = IgColorRemapEngine.remap(resolved);
                    if (remapped != resolved) param.setResult(remapped);
                }
            }
        };
    }

    private void hookColorStateList() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ColorStateList.class, "valueOf", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
    }

    private void hookViewColors() {
        XC_MethodHook viewColorHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, viewColorHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setForegroundTintList", ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackgroundTintList", ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(View.class, "setBackground", android.graphics.drawable.Drawable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorDrawable) {
                    ColorDrawable cd = (ColorDrawable) param.args[0];
                    int original = cd.getColor();
                    int remapped = IgColorRemapEngine.remap(original);
                    if (remapped != original) {
                        ColorDrawable next = new ColorDrawable(remapped);
                        param.args[0] = next;
                    }
                }
            }
        }));
    }

    private void hookTextViewColors() {
        XC_MethodHook intColor = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        };
        XC_MethodHook cslColor = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, intColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", ColorStateList.class, cslColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHintTextColor", int.class, intColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHintTextColor", ColorStateList.class, cslColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setLinkTextColor", int.class, intColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setLinkTextColor", ColorStateList.class, cslColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setHighlightColor", int.class, intColor));
        tryHook(() -> XposedHelpers.findAndHookMethod(TextView.class, "setShadowLayer", float.class, float.class, float.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, 3);
            }
        }));
    }

    private void hookDrawableColors() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColors", int[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                int[] colors = (int[]) param.args[0];
                int[] remapped = IgColorRemapEngine.remapIntArray(colors);
                if (remapped != colors) param.args[0] = remapped;
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setStroke", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                IgColorRemapEngine.applyRemapArg(param.args, 1);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(GradientDrawable.class, "setStroke", int.class, ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                if (param.args[1] instanceof ColorStateList) {
                    param.args[1] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[1]);
                }
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(RippleDrawable.class, "setColor", ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        }));
    }

    private void hookImageViewTint() {
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter", int.class, PorterDuff.Mode.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setColorFilter", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                IgColorRemapEngine.applyRemapArg(param.args, 0);
            }
        }));
        tryHook(() -> XposedHelpers.findAndHookMethod(ImageView.class, "setImageTintList", ColorStateList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        }));
    }

    private void hookWidgetTints() {
        XC_MethodHook cslHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.shouldSkipRemap(param.thisObject)) return;
                if (param.args[0] instanceof ColorStateList) {
                    param.args[0] = IgColorRemapEngine.remapColorStateList((ColorStateList) param.args[0]);
                }
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(CompoundButton.class, "setButtonTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setProgressTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setIndeterminateTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(ProgressBar.class, "setProgressBackgroundTintList", ColorStateList.class, cslHook));
        tryHook(() -> XposedHelpers.findAndHookMethod("android.widget.AbsSeekBar", null, "setThumbTintList", ColorStateList.class, cslHook));
    }

    private void hookWindowColors() {
        XC_MethodHook statusHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (IgThemeEngine.isActive()) param.args[0] = IgThemeEngine.getActivePalette().statusBar;
            }
        };
        XC_MethodHook navHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (IgThemeEngine.isActive()) param.args[0] = IgThemeEngine.getActivePalette().navigation;
            }
        };
        tryHook(() -> XposedHelpers.findAndHookMethod(Window.class, "setStatusBarColor", int.class, statusHook));
        tryHook(() -> XposedHelpers.findAndHookMethod(Window.class, "setNavigationBarColor", int.class, navHook));
    }

    private void hookComposeColors(ClassLoader cl) {
        try {
            Class<?> colorKt = cl.loadClass("androidx.compose.ui.graphics.ColorKt");
            for (Method method : colorKt.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().contains("toArgb")
                        && method.getReturnType() == int.class
                        && params.length == 1
                        && params[0] == long.class) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
                            Object result = param.getResult();
                            if (!(result instanceof Integer)) return;
                            int resolved = (Integer) result;
                            int remapped = IgColorRemapEngine.remapExact(resolved);
                            if (remapped != resolved) param.setResult(remapped);
                        }
                    });
                    continue;
                }
                if (method.getReturnType() == long.class && params.length == 1 && params[0] == int.class
                        && method.getName().contains("Color")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
                            if (param.args[0] instanceof Integer) {
                                int original = (Integer) param.args[0];
                                int remapped = IgColorRemapEngine.remapExact(original);
                                if (remapped != original) param.args[0] = remapped;
                            }
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
        String[] palettes = IgColorRemapEngine.COMPOSE_PALETTE_CLASSES;
        XC_MethodHook packedHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (!(result instanceof Long)) return;
                long packed = (Long) result;
                long remapped = IgColorRemapEngine.remapComposePacked(packed);
                if (remapped != packed) param.setResult(remapped);
            }
        };
        for (String className : palettes) {
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
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
                Object result = param.getResult();
                if (!(result instanceof Integer)) return;
                int resolved = (Integer) result;
                int remapped = IgColorRemapEngine.remap(resolved);
                if (remapped != resolved) param.setResult(remapped);
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
                List<MethodData> methods = Module.dexKitBridge.findMethod(
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
                if (!IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing() || param.getThrowable() != null) return;
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
                    List<ClassData> classes = Module.dexKitBridge.findClass(
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
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
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

    private void hookPhoneWindowColors(ClassLoader cl) {
        XC_MethodHook statusHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (IgThemeEngine.isActive()) param.args[0] = IgThemeEngine.getActivePalette().statusBar;
            }
        };
        XC_MethodHook navHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (IgThemeEngine.isActive()) param.args[0] = IgThemeEngine.getActivePalette().navigation;
            }
        };
        if (!tryHookPhoneWindow(cl, statusHook, navHook)) tryHookPhoneWindow(null, statusHook, navHook);
    }

    private boolean tryHookPhoneWindow(ClassLoader cl, XC_MethodHook statusHook, XC_MethodHook navHook) {
        try {
            XposedHelpers.findAndHookMethod("com.android.internal.policy.PhoneWindow", cl, "setStatusBarColor", int.class, statusHook);
            XposedHelpers.findAndHookMethod("com.android.internal.policy.PhoneWindow", cl, "setNavigationBarColor", int.class, navHook);
            ModuleLog.line("(InstaEclipse | Theme): PhoneWindow color hooks installed");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    
    public static void refreshCurrentActivity() {
        Activity activity = UIHookManager.getCurrentActivity();
        if (activity == null || activity.isFinishing()) return;
        IgThemeEngine.ensureInitialized(activity);
        if (!FeatureFlags.customThemeEnabled) return;
        IgColorRemapEngine.ensureBuilt(activity);
        applyWindowColors(activity);
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.post(() -> {
                try {
                    if (!activity.isFinishing()) activity.recreate();
                } catch (Throwable ignored) {}
            });
        }
    }

    static void applyWindowColors(Activity activity) {
        if (activity == null || !IgThemeEngine.isActive()) return;
        try {
            IgThemePalette palette = IgThemeEngine.getActivePalette();
            Window window = activity.getWindow();
            if (window == null) return;
            window.setStatusBarColor(palette.statusBar);
            window.setNavigationBarColor(palette.navigation);
            View decor = window.getDecorView();
            if (decor != null) decor.setBackgroundColor(palette.background);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    boolean lightBg = Color.red(palette.background) + Color.green(palette.background) + Color.blue(palette.background) > 382;
                    controller.setSystemBarsAppearance(lightBg ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else {
                applyLegacySystemUiVisibility(window, palette.background);
            }
            IgThemeViewSweep.attach(activity);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacySystemUiVisibility(Window window, int backgroundColor) {
        int flags = window.getDecorView().getSystemUiVisibility();
        boolean lightBg = Color.red(backgroundColor) + Color.green(backgroundColor) + Color.blue(backgroundColor) > 382;
        if (lightBg) {
            flags = flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags = flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private static int[] typedArrayAttributes(TypedArray ta) {
        try {
            Field field = typedArrayAttrsField;
            if (field == null) {
                field = TypedArray.class.getDeclaredField("mAttributes");
                field.setAccessible(true);
                typedArrayAttrsField = field;
            }
            return (int[]) field.get(ta);
        } catch (Throwable ignored) {
            return null;
        }
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
