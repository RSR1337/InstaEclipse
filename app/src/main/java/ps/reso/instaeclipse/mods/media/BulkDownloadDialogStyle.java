package ps.reso.instaeclipse.mods.media;

import android.content.Context;
import android.content.res.Configuration;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BulkDownloadDialogStyle {

    private static final int ACCENT = 0xFF3797EF;

    private static final int DARK_SURFACE = 0xFF1C1E21;
    private static final int DARK_PRIMARY_TEXT = 0xFFF5F6F7;
    private static final int DARK_SECONDARY_TEXT = 0xFF9DA3AE;

    private static final int LIGHT_SURFACE = 0xFFFFFFFF;
    private static final int LIGHT_PRIMARY_TEXT = 0xFF1C1E21;
    private static final int LIGHT_SECONDARY_TEXT = 0xFF65676B;

    private BulkDownloadDialogStyle() {}

    static boolean isDarkMode(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int surfaceColor(boolean dark) {
        return dark ? DARK_SURFACE : LIGHT_SURFACE;
    }

    static int primaryTextColor(boolean dark) {
        return dark ? DARK_PRIMARY_TEXT : LIGHT_PRIMARY_TEXT;
    }

    static int secondaryTextColor(boolean dark) {
        return dark ? DARK_SECONDARY_TEXT : LIGHT_SECONDARY_TEXT;
    }

    static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static void applyCardWindow(AlertDialog dialog, Context ctx) {
        try {
            GradientDrawable card = new GradientDrawable();
            card.setColor(surfaceColor(isDarkMode(ctx)));
            card.setCornerRadius(dp(ctx, 24f));
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(card);
            }
        } catch (Throwable ignored) {}
    }

    static View buildTitleView(Context ctx, String text) {
        boolean dark = isDarkMode(ctx);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(ctx, 20f);
        row.setPadding(padH, dp(ctx, 20f), padH, dp(ctx, 8f));

        int badgeSize = dp(ctx, 32f);
        ImageView badge = new ImageView(ctx);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(withAlpha(ACCENT, 0x33));
        badge.setBackground(badgeBg);
        badge.setImageResource(android.R.drawable.stat_sys_download);
        badge.setColorFilter(ACCENT);
        int iconPad = dp(ctx, 6f);
        badge.setPadding(iconPad, iconPad, iconPad, iconPad);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.setMarginEnd(dp(ctx, 12f));
        row.addView(badge, badgeLp);

        TextView title = new TextView(ctx);
        title.setText(text);
        title.setTextColor(primaryTextColor(dark));
        title.setTextSize(18f);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        row.addView(title);

        return row;
    }

    static View buildFilledButton(Context ctx, String text, Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(ctx, 24f));
        btn.setBackground(bg);
        finalizeButton(ctx, btn, onClick, true);
        return btn;
    }

    static View buildOutlinedButton(Context ctx, String text, Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(ACCENT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(ctx, 1.5f), ACCENT);
        bg.setCornerRadius(dp(ctx, 24f));
        btn.setBackground(bg);
        finalizeButton(ctx, btn, onClick, true);
        return btn;
    }

    static View buildTextButton(Context ctx, String text, boolean dark, Runnable onClick) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(secondaryTextColor(dark));
        finalizeButton(ctx, btn, onClick, false);
        return btn;
    }

    private static void finalizeButton(Context ctx, TextView btn, Runnable onClick, boolean bold) {
        btn.setTextSize(15f);
        btn.setGravity(Gravity.CENTER);
        btn.setAllCaps(false);
        if (bold) btn.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        int vPad = dp(ctx, 13f);
        btn.setPadding(0, vPad, 0, vPad);
        btn.setClickable(true);
        btn.setFocusable(true);
        addRipple(ctx, btn);
        btn.setOnClickListener(v -> onClick.run());
    }

    private static void addRipple(Context ctx, View v) {
        try {
            TypedValue tv = new TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            if (tv.resourceId != 0) v.setForeground(ctx.getResources().getDrawable(tv.resourceId, ctx.getTheme()));
        } catch (Throwable ignored) {}
    }
}
