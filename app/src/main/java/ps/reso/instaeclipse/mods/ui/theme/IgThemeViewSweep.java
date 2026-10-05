package ps.reso.instaeclipse.mods.ui.theme;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ps.reso.instaeclipse.R;

public final class IgThemeViewSweep {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_VISITED = 6000;
    private static final int MAX_APPLIED = 1500;
    private static final long MIN_INTERVAL_MS = 280L;
    private static final String[] SKIP_CLASS_MARKERS = {
            "surfaceview", "textureview", "videoview", "webview", "exoplayer", "drawee", "mediaview",
            "zoomable", "roundedcornermedia", "photoview", "igimageview", "constrainedimageview",
            "circularimageview", "refreshableimage", "animatedimage", "showreels", "reelviewer"
    };

    private static final Map<Class<?>, Boolean> SKIP_CACHE = new HashMap<>();

    private IgThemeViewSweep() {}

    public static void attach(Activity activity) {
        if (activity == null || !IgThemeEngine.isActive()) return;
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) return;
        Object existing = decor.getTag(R.id.tag_theme_layout_listener);
        if (existing instanceof SweepListener) {
            ((SweepListener) existing).request();
            return;
        }
        SweepListener listener = new SweepListener(decor);
        decor.setTag(R.id.tag_theme_layout_listener, listener);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        listener.request();
    }

    private static final class SweepListener implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        private final View decor;
        private long lastSweepAt;
        private boolean pending;

        SweepListener(View decor) {
            this.decor = decor;
        }

        @Override
        public void onGlobalLayout() {
            request();
        }

        void request() {
            long elapsed = SystemClock.uptimeMillis() - lastSweepAt;
            if (elapsed >= MIN_INTERVAL_MS) {
                run();
            } else if (!pending) {
                pending = true;
                decor.postDelayed(this, MIN_INTERVAL_MS - elapsed);
            }
        }

        @Override
        public void run() {
            pending = false;
            lastSweepAt = SystemClock.uptimeMillis();
            sweep(decor);
        }
    }

    private static void sweep(View root) {
        if (root == null || !IgThemeEngine.isActive() || !IgColorRemapEngine.isReady()) return;
        walk(root, 0, IgColorRemapEngine.generation(), new int[]{0, 0});
    }

    private static void walk(View view, int depth, int generation, int[] counts) {
        if (view == null || depth > MAX_DEPTH || counts[0] >= MAX_VISITED || counts[1] >= MAX_APPLIED) return;
        if (shouldSkip(view) || IgColorRemapEngine.isModuleUiView(view)) return;
        counts[0]++;
        Object seen = view.getTag(R.id.tag_theme_sweep_gen);
        if (!(seen instanceof Integer) || (Integer) seen != generation) {
            apply(view);
            view.setTag(R.id.tag_theme_sweep_gen, generation);
            counts[1]++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int n = group.getChildCount();
            for (int i = 0; i < n; i++) {
                walk(group.getChildAt(i), depth + 1, generation, counts);
            }
        }
    }

    private static boolean shouldSkip(View view) {
        if (view instanceof SurfaceView || view instanceof TextureView) return true;
        Class<?> cls = view.getClass();
        Boolean cached = SKIP_CACHE.get(cls);
        if (cached != null) return cached;
        String lower = cls.getName().toLowerCase(Locale.US);
        boolean skip = false;
        for (String marker : SKIP_CLASS_MARKERS) {
            if (lower.contains(marker)) {
                skip = true;
                break;
            }
        }
        SKIP_CACHE.put(cls, skip);
        return skip;
    }

    private static void apply(View view) {
        try {
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                ColorDrawable cd = (ColorDrawable) bg;
                int original = cd.getColor();
                int remapped = IgColorRemapEngine.remap(original);
                if (remapped != original) {
                    IgColorRemapEngine.withBypass(() -> ((ColorDrawable) cd.mutate()).setColor(remapped));
                }
            }
            ColorStateList bgTint = view.getBackgroundTintList();
            if (bgTint != null) {
                ColorStateList remapped = IgColorRemapEngine.remapColorStateList(bgTint);
                if (remapped != bgTint) IgColorRemapEngine.withBypass(() -> view.setBackgroundTintList(remapped));
            }
            ColorStateList fgTint = view.getForegroundTintList();
            if (fgTint != null) {
                ColorStateList remapped = IgColorRemapEngine.remapColorStateList(fgTint);
                if (remapped != fgTint) IgColorRemapEngine.withBypass(() -> view.setForegroundTintList(remapped));
            }

            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                ColorStateList text = tv.getTextColors();
                ColorStateList hint = tv.getHintTextColors();
                ColorStateList link = tv.getLinkTextColors();
                ColorStateList newText = IgColorRemapEngine.remapColorStateList(text);
                ColorStateList newHint = IgColorRemapEngine.remapColorStateList(hint);
                ColorStateList newLink = IgColorRemapEngine.remapColorStateList(link);
                int highlight = tv.getHighlightColor();
                int newHighlight = IgColorRemapEngine.remap(highlight);
                IgColorRemapEngine.withBypass(() -> {
                    if (newText != text && newText != null) tv.setTextColor(newText);
                    if (newHint != hint && newHint != null) tv.setHintTextColor(newHint);
                    if (newLink != link && newLink != null) tv.setLinkTextColor(newLink);
                    if (newHighlight != highlight) tv.setHighlightColor(newHighlight);
                });
            }
            if (view instanceof ImageView) {
                ImageView iv = (ImageView) view;
                ColorStateList tint = iv.getImageTintList();
                if (!(iv.getDrawable() instanceof BitmapDrawable) && tint != null) {
                    ColorStateList remapped = IgColorRemapEngine.remapColorStateList(tint);
                    if (remapped != tint) IgColorRemapEngine.withBypass(() -> iv.setImageTintList(remapped));
                }
            }
            if (view instanceof CompoundButton) {
                CompoundButton cb = (CompoundButton) view;
                ColorStateList tint = cb.getButtonTintList();
                if (tint != null) {
                    ColorStateList remapped = IgColorRemapEngine.remapColorStateList(tint);
                    if (remapped != tint) IgColorRemapEngine.withBypass(() -> cb.setButtonTintList(remapped));
                }
            }
            if (view instanceof ProgressBar) {
                ProgressBar pb = (ProgressBar) view;
                ColorStateList progress = pb.getProgressTintList();
                if (progress != null) {
                    ColorStateList remapped = IgColorRemapEngine.remapColorStateList(progress);
                    if (remapped != progress) IgColorRemapEngine.withBypass(() -> pb.setProgressTintList(remapped));
                }
                ColorStateList indeterminate = pb.getIndeterminateTintList();
                if (indeterminate != null) {
                    ColorStateList remapped = IgColorRemapEngine.remapColorStateList(indeterminate);
                    if (remapped != indeterminate) IgColorRemapEngine.withBypass(() -> pb.setIndeterminateTintList(remapped));
                }
            }
        } catch (Throwable ignored) {}
    }
}
