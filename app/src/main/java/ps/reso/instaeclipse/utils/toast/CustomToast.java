package ps.reso.instaeclipse.utils.toast;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.mods.ui.theme.IgColorRemapEngine;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class CustomToast {

    public static boolean toastShown = false;

    private static final int DURATION_MS = 2500;
    private static final int FEATURE_LIST_MAX_DP = 280;
    private static final int FEATURE_LIST_MIN_DP = 96;
    private static final int FEATURE_CARD_CHROME_DP = 140;
    private static final int TAG_OVERLAY = 0x49454354;
    private static final Pattern LEADING_EMOJI =
            Pattern.compile("^[\\p{So}\\p{Sk}\\u200D\\uFE0F]+\\s*");
    private static final Pattern TRAILING_EMOJI =
            Pattern.compile("\\s*[\\p{So}\\p{Sk}\\u200D\\uFE0F]+$");

    private static WeakReference<View> overlayRef;

    public static void showCustomToast(Context context, String message) {
        if (context == null) {
            ModuleLog.line("❌ CustomToast: Context is null!");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                showInternal(context, () -> buildMessageCard(context, Palette.from(context), message),
                        message == null ? "" : message);
            } catch (Throwable t) {
                ModuleLog.line("❌ Failed to show custom toast: " + Log.getStackTraceString(t));
            }
        });
    }

    public static void showFeatureStatusToast(Context context) {
        if (context == null) {
            ModuleLog.line("❌ CustomToast: Context is null!");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Palette palette = Palette.from(context);
                List<FeatureRow> rows = collectRows(context);
                int hookedCount = 0;
                int pendingCount = 0;
                int offCount = 0;
                for (FeatureRow row : rows) {
                    if (row.state == FeatureStatusTracker.State.HOOKED) hookedCount++;
                    else if (row.state == FeatureStatusTracker.State.PENDING) pendingCount++;
                    else offCount++;
                }
                final int hooked = hookedCount;
                final int pending = pendingCount;
                final int off = offCount;
                String fallback = stripEdgeEmoji(I18n.t(context, R.string.ig_toast_features_loaded));
                String status = statusLine(context, hooked, pending, off);
                if (!status.isEmpty()) fallback = fallback + "\n" + status;
                showInternal(context, () -> buildFeatureCard(context, palette, rows, hooked, pending, off), fallback);
            } catch (Throwable t) {
                ModuleLog.line("❌ Failed to show custom toast: " + Log.getStackTraceString(t));
            }
        });
    }

    private interface CardFactory {
        View create();
    }

    private static void showInternal(Context context, CardFactory factory, String fallbackText) {
        IgColorRemapEngine.enterModuleUi();
        try {
            View card = factory.create();
            IgColorRemapEngine.markModuleDialogView(card);
            Activity activity = resolveActivity(context);
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                if (attachOverlay(activity, card)) return;
            }
            showViaToast(context, card, fallbackText);
        } finally {
            IgColorRemapEngine.leaveModuleUi();
        }
    }

    private static boolean attachOverlay(Activity activity, View card) {
        ViewGroup parent = findOverlayParent(activity);
        if (parent == null) return false;
        dismissOverlay(false);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int side = dp(activity, 16);
        int maxW = dp(activity, 400);
        int available = parent.getWidth() > 0 ? parent.getWidth() : dm.widthPixels;
        int width = Math.min(Math.max(0, available - side * 2), maxW);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        card.setTag(TAG_OVERLAY);
        card.setAlpha(0f);
        card.setTranslationY(dp(activity, 12));
        parent.addView(card, lp);
        overlayRef = new WeakReference<>(card);
        card.animate().alpha(1f).translationY(0f).setDuration(180).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> dismissOverlay(true), DURATION_MS);
        card.setOnClickListener(v -> dismissOverlay(true));
        return true;
    }

    private static ViewGroup findOverlayParent(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof FrameLayout) return (FrameLayout) content;
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor instanceof FrameLayout) return (FrameLayout) decor;
        if (content instanceof ViewGroup) return (ViewGroup) content;
        return null;
    }

    private static void dismissOverlay(boolean animate) {
        View card = overlayRef != null ? overlayRef.get() : null;
        overlayRef = null;
        if (card == null) return;
        Runnable remove = () -> {
            try {
                ViewGroup parent = (ViewGroup) card.getParent();
                if (parent != null) parent.removeView(card);
            } catch (Throwable ignored) {
            }
        };
        if (!animate || card.getParent() == null) {
            remove.run();
            return;
        }
        card.animate().alpha(0f).translationY(dp(card.getContext(), 8)).setDuration(160)
                .withEndAction(remove).start();
    }

    private static void showViaToast(Context context, View card, String fallbackText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Toast.makeText(context, fallbackText, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast toast = new Toast(context);
        toast.setView(card);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
        new Handler(Looper.getMainLooper()).postDelayed(toast::cancel, DURATION_MS);
    }

    private static View buildMessageCard(Context context, Palette p, String message) {
        LinearLayout card = createCardShell(context, p);
        TextView body = new TextView(context);
        applyLocaleDirection(body);
        body.setText(message);
        body.setTextColor(p.body);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setLineSpacing(dp(context, 2), 1f);
        body.setMaxLines(4);
        body.setEllipsize(TextUtils.TruncateAt.END);
        card.setContentDescription(message);
        card.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static View buildFeatureCard(Context context, Palette p, List<FeatureRow> rows,
                                         int hooked, int pending, int off) {
        LinearLayout card = createCardShell(context, p);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        applyLocaleDirection(header);

        View accent = new View(context);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(p.accent);
        accentBg.setCornerRadius(dp(context, 2));
        accent.setBackground(accentBg);
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(context, 3), dp(context, 28));
        accentLp.setMarginEnd(dp(context, 12));
        header.addView(accent, accentLp);

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        applyLocaleDirection(titles);

        TextView title = new TextView(context);
        applyLocaleDirection(title);
        title.setText(stripEdgeEmoji(I18n.t(context, R.string.ig_toast_features_loaded)));
        title.setTextColor(p.title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(title);

        String status = statusLine(context, hooked, pending, off);
        if (!status.isEmpty()) {
            TextView subtitle = new TextView(context);
            applyLocaleDirection(subtitle);
            subtitle.setText(status);
            subtitle.setTextColor(p.subtitle);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subtitle.setPadding(0, dp(context, 2), 0, 0);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            titles.addView(subtitle);
        }

        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String description = stripEdgeEmoji(I18n.t(context, R.string.ig_toast_features_loaded));
        if (!status.isEmpty()) description = description + " " + status;
        card.setContentDescription(description);

        if (rows.isEmpty()) return card;

        View divider = new View(context);
        divider.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(context, 10);
        divLp.bottomMargin = dp(context, 6);
        card.addView(divider, divLp);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        applyLocaleDirection(list);

        boolean twoCol = rows.size() >= 4;
        if (twoCol) {
            for (int i = 0; i < rows.size(); i += 2) {
                LinearLayout pair = new LinearLayout(context);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                pair.setGravity(Gravity.CENTER_VERTICAL);
                applyLocaleDirection(pair);
                pair.addView(buildFeatureCell(context, p, rows.get(i)),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if (i + 1 < rows.size()) {
                    LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    gap.setMarginStart(dp(context, 8));
                    pair.addView(buildFeatureCell(context, p, rows.get(i + 1)), gap);
                } else {
                    pair.addView(new View(context),
                            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                list.addView(pair);
            }
        } else {
            for (FeatureRow row : rows) {
                list.addView(buildFeatureCell(context, p, row),
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        final int listMax = featureListMaxHeight(context);
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(listMax, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        applyLocaleDirection(scroll);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(true);
        scroll.setScrollBarFadeDuration(400);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static View buildFeatureCell(Context context, Palette p, FeatureRow row) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        applyLocaleDirection(cell);
        cell.setPaddingRelative(0, dp(context, 5), 0, dp(context, 5));

        View dot = new View(context);
        int size = dp(context, 8);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(dotColor(p, row.state));
        dot.setBackground(oval);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
        dotLp.setMarginEnd(dp(context, 8));
        cell.addView(dot, dotLp);

        TextView name = new TextView(context);
        applyLocaleDirection(name);
        name.setText(row.label);
        name.setTextColor(row.state == FeatureStatusTracker.State.OFF ? p.subtitle : p.body);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return cell;
    }

    private static LinearLayout createCardShell(Context context, Palette p) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        applyLocaleDirection(card);
        int padH = dp(context, 16);
        int padV = dp(context, 12);
        card.setPaddingRelative(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.card);
        bg.setCornerRadius(dp(context, 16));
        bg.setStroke(1, p.stroke);
        card.setBackground(bg);
        card.setElevation(dp(context, 10));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setFocusable(true);
        return card;
    }

    private static List<FeatureRow> collectRows(Context context) {
        List<FeatureRow> rows = new ArrayList<>();
        for (Map.Entry<String, FeatureStatusTracker.State> entry : FeatureStatusTracker.getStatus().entrySet()) {
            FeatureStatusTracker.State state = entry.getValue();
            if (state == null) continue;
            String label = stripEdgeEmoji(FeatureStatusTracker.getLabel(context, entry.getKey()));
            if (label == null || label.isEmpty()) label = entry.getKey();
            rows.add(new FeatureRow(label, state));
        }
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        Collator collator = Collator.getInstance(locale != null ? locale : Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        rows.sort((a, b) -> {
            int rank = stateRank(a.state) - stateRank(b.state);
            if (rank != 0) return rank;
            return collator.compare(a.label, b.label);
        });
        return rows;
    }

    private static int stateRank(FeatureStatusTracker.State state) {
        if (state == FeatureStatusTracker.State.HOOKED) return 0;
        if (state == FeatureStatusTracker.State.PENDING) return 1;
        return 2;
    }

    private static int dotColor(Palette p, FeatureStatusTracker.State state) {
        if (state == FeatureStatusTracker.State.HOOKED) return p.on;
        if (state == FeatureStatusTracker.State.PENDING) return p.pending;
        return p.off;
    }

    private static String statusLine(Context context, int hooked, int pending, int off) {
        String text;
        if (pending > 0 && off > 0) {
            text = I18n.t(context, R.string.ig_toast_features_status_full, hooked, pending, off);
        } else if (pending > 0) {
            text = I18n.t(context, R.string.ig_toast_features_status_count, hooked, pending);
        } else if (off > 0) {
            text = I18n.t(context, R.string.ig_toast_features_on_off_count, hooked, off);
        } else {
            text = I18n.t(context, R.string.ig_toast_features_active_count, hooked);
        }
        return text == null ? "" : text;
    }

    private static void applyLocaleDirection(View view) {
        view.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        if (view instanceof TextView) {
            ((TextView) view).setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            ((TextView) view).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
    }

    private static String stripEdgeEmoji(String text) {
        if (text == null) return "";
        String stripped = LEADING_EMOJI.matcher(text).replaceFirst("");
        stripped = TRAILING_EMOJI.matcher(stripped).replaceFirst("");
        return stripped.trim();
    }

    private static Activity resolveActivity(Context context) {
        Context c = context;
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        try {
            return UIHookManager.getCurrentActivity();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int featureListMaxHeight(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int budget = (int) (dm.heightPixels * 0.52f) - dp(context, FEATURE_CARD_CHROME_DP);
        int cap = dp(context, FEATURE_LIST_MAX_DP);
        int floor = dp(context, FEATURE_LIST_MIN_DP);
        return Math.max(floor, Math.min(cap, budget));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class FeatureRow {
        final String label;
        final FeatureStatusTracker.State state;

        FeatureRow(String label, FeatureStatusTracker.State state) {
            this.label = label;
            this.state = state;
        }
    }

    private static final class Palette {
        final int card;
        final int title;
        final int subtitle;
        final int body;
        final int accent;
        final int on;
        final int pending;
        final int off;
        final int divider;
        final int stroke;

        private Palette(int card, int title, int subtitle, int body, int accent,
                        int on, int pending, int off, int divider, int stroke) {
            this.card = card;
            this.title = title;
            this.subtitle = subtitle;
            this.body = body;
            this.accent = accent;
            this.on = on;
            this.pending = pending;
            this.off = off;
            this.divider = divider;
            this.stroke = stroke;
        }

        static Palette from(Context context) {
            int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean dark = night != Configuration.UI_MODE_NIGHT_NO;
            if (dark) {
                return new Palette(
                        0xF21C1C1E,
                        0xFFFFFFFF,
                        0xFF8E8E93,
                        0xFFF2F2F7,
                        0xFF0A84FF,
                        0xFF30D158,
                        0xFFFF9F0A,
                        0xFFFF453A,
                        0x3D3A3A3C,
                        0x333A3A3C
                );
            }
            return new Palette(
                    0xF5FFFFFF,
                    0xFF1C1C1E,
                    0xFF8E8E93,
                    0xFF1C1C1E,
                    0xFF0A84FF,
                    0xFF34C759,
                    0xFFFF9F0A,
                    0xFFFF3B30,
                    0x1A000000,
                    0x14000000
                );
        }
    }
}
