package ps.reso.instaeclipse.mods.ui.theme;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class IgThemeViewSweep {

    private static final int MAX_NODES = 720;
    private static final long MIN_INTERVAL_MS = 280L;
    private static final int TAG_THEME_LISTENER = "ie_theme_layout_listener".hashCode();
    private static volatile long lastSweepAt;

    private IgThemeViewSweep() {}

    public static void attach(Activity activity) {
        if (activity == null || !IgThemeEngine.isActive()) return;
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) return;
        if (decor.getTag(TAG_THEME_LISTENER) instanceof ViewTreeObserver.OnGlobalLayoutListener) {
            sweep(decor);
            return;
        }
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            long now = SystemClock.uptimeMillis();
            if (now - lastSweepAt < MIN_INTERVAL_MS) return;
            lastSweepAt = now;
            sweep(decor);
        };
        decor.setTag(TAG_THEME_LISTENER, listener);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        sweep(decor);
    }

    private static void sweep(View root) {
        if (root == null || !IgThemeEngine.isActive() || IgColorRemapEngine.isBypassing()) return;
        walk(root, 0, new int[]{0});
    }

    private static void walk(View view, int depth, int[] count) {
        if (view == null || depth > 22 || count[0] >= MAX_NODES) return;
        if (IgColorRemapEngine.isModuleUiView(view) || shouldSkip(view)) return;
        count[0]++;
        apply(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int n = group.getChildCount();
            for (int i = 0; i < n; i++) {
                walk(group.getChildAt(i), depth + 1, count);
            }
        }
    }

    private static boolean shouldSkip(View view) {
        String lower = view.getClass().getName().toLowerCase();
        if (lower.contains("surfaceview") || lower.contains("textureview") || lower.contains("videoview")
                || lower.contains("webview") || lower.contains("exoplayer") || lower.contains("drawee")
                || lower.contains("mediaview") || lower.contains("zoomable") || lower.contains("roundedcornermedia")
                || lower.contains("photoview") || lower.contains("igimageview") || lower.contains("constrainedimageview")
                || lower.contains("circularimageview") || lower.contains("refreshableimage")
                || lower.contains("animatedimage") || lower.contains("showreels") || lower.contains("reelviewer")) {
            return true;
        }
        return view instanceof android.view.SurfaceView || view instanceof android.view.TextureView;
    }

    private static void apply(View view) {
        try {
            Drawable bg = view.getBackground();
            Drawable remappedBg = remapDrawable(bg);
            if (remappedBg != bg && remappedBg != null) {
                IgColorRemapEngine.withBypass(() -> view.setBackground(remappedBg));
            }
            if (view.getBackgroundTintList() != null) {
                android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(view.getBackgroundTintList());
                if (remapped != view.getBackgroundTintList()) {
                    IgColorRemapEngine.withBypass(() -> view.setBackgroundTintList(remapped));
                }
            }
            if (Build.VERSION.SDK_INT >= 23 && view.getForegroundTintList() != null) {
                android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(view.getForegroundTintList());
                if (remapped != view.getForegroundTintList()) {
                    IgColorRemapEngine.withBypass(() -> view.setForegroundTintList(remapped));
                }
            }

            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                android.content.res.ColorStateList text = IgColorRemapEngine.remapColorStateList(tv.getTextColors());
                android.content.res.ColorStateList hint = IgColorRemapEngine.remapColorStateList(tv.getHintTextColors());
                android.content.res.ColorStateList link = IgColorRemapEngine.remapColorStateList(tv.getLinkTextColors());
                int highlight = IgColorRemapEngine.remap(tv.getHighlightColor());
                IgColorRemapEngine.withBypass(() -> {
                    tv.setTextColor(text);
                    tv.setHintTextColor(hint);
                    tv.setLinkTextColor(link);
                    tv.setHighlightColor(highlight);
                });
            }
            if (view instanceof ImageView) {
                ImageView iv = (ImageView) view;
                Drawable d = iv.getDrawable();
                if (!(d instanceof BitmapDrawable) && iv.getImageTintList() != null) {
                    android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(iv.getImageTintList());
                    if (remapped != iv.getImageTintList()) {
                        IgColorRemapEngine.withBypass(() -> iv.setImageTintList(remapped));
                    }
                }
            }
            if (view instanceof CompoundButton) {
                CompoundButton cb = (CompoundButton) view;
                if (cb.getButtonTintList() != null) {
                    android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(cb.getButtonTintList());
                    if (remapped != cb.getButtonTintList()) {
                        IgColorRemapEngine.withBypass(() -> cb.setButtonTintList(remapped));
                    }
                }
            }
            if (view instanceof ProgressBar) {
                ProgressBar pb = (ProgressBar) view;
                if (pb.getProgressTintList() != null) {
                    android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(pb.getProgressTintList());
                    if (remapped != pb.getProgressTintList()) {
                        IgColorRemapEngine.withBypass(() -> pb.setProgressTintList(remapped));
                    }
                }
                if (pb.getIndeterminateTintList() != null) {
                    android.content.res.ColorStateList remapped = IgColorRemapEngine.remapColorStateList(pb.getIndeterminateTintList());
                    if (remapped != pb.getIndeterminateTintList()) {
                        IgColorRemapEngine.withBypass(() -> pb.setIndeterminateTintList(remapped));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static Drawable remapDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            if (drawable instanceof ColorDrawable) {
                ColorDrawable cd = (ColorDrawable) drawable.mutate();
                int original = cd.getColor();
                int remapped = IgColorRemapEngine.remap(original);
                if (remapped != original) {
                    IgColorRemapEngine.withBypass(() -> cd.setColor(remapped));
                }
                return cd;
            }
        } catch (Throwable ignored) {}
        return drawable;
    }
}
