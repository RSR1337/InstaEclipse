package ps.reso.instaeclipse.mods.media;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

import static ps.reso.instaeclipse.mods.media.BulkDownloadDialogStyle.dp;
import static ps.reso.instaeclipse.mods.media.BulkDownloadDialogStyle.primaryTextColor;
import static ps.reso.instaeclipse.mods.media.BulkDownloadDialogStyle.secondaryTextColor;
import static ps.reso.instaeclipse.mods.media.BulkDownloadDialogStyle.withAlpha;

final class BulkDownloadProgressDialog {

    private static final int ACCENT = 0xFF3797EF;

    private final Context appCtx;
    private final Handler mainHandler;
    private final AlertDialog dialog;
    private final View fillView;
    private final View spacerView;
    private final LinearLayout track;
    private final TextView countText;
    private final TextView statusText;
    private final TextView actionButton;
    private final int total;
    private volatile boolean cancelled;

    private BulkDownloadProgressDialog(Context appCtx, Handler mainHandler, int total, AlertDialog dialog,
                                        LinearLayout track, View fillView, View spacerView,
                                        TextView countText, TextView statusText, TextView actionButton) {
        this.appCtx = appCtx;
        this.mainHandler = mainHandler;
        this.total = total;
        this.dialog = dialog;
        this.track = track;
        this.fillView = fillView;
        this.spacerView = spacerView;
        this.countText = countText;
        this.statusText = statusText;
        this.actionButton = actionButton;
    }

    /** Builds and shows the dialog; falls back to a headless no-op controller if the host window can't host it. */
    static BulkDownloadProgressDialog show(Context ctx, Handler mainHandler, int total) {
        try {
            Context dialogCtx = FeedVideoDownloadHook.resolveDialogContext(ctx);
            boolean dark = BulkDownloadDialogStyle.isDarkMode(dialogCtx);
            int primaryText = primaryTextColor(dark);
            int secondaryText = secondaryTextColor(dark);

            LinearLayout container = new LinearLayout(dialogCtx);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(dialogCtx, 20f);
            container.setPadding(pad, 0, pad, dp(dialogCtx, 20f));

            TextView countText = new TextView(dialogCtx);
            countText.setText(I18n.t(ctx, R.string.ig_dl_progress_count, 0, total));
            countText.setTextColor(primaryText);
            countText.setTextSize(20f);
            countText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            countText.setGravity(Gravity.CENTER);
            container.addView(countText);

            int trackHeight = dp(dialogCtx, 8f);
            LinearLayout track = new LinearLayout(dialogCtx);
            track.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setCornerRadius(trackHeight / 2f);
            trackBg.setColor(withAlpha(secondaryText, 0x30));
            track.setBackground(trackBg);
            track.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            track.setClipToOutline(true);

            View fillView = new View(dialogCtx);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(ACCENT);
            fillView.setBackground(fillBg);
            track.addView(fillView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f));

            View spacerView = new View(dialogCtx);
            track.addView(spacerView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, trackHeight);
            trackParams.topMargin = dp(dialogCtx, 16f);
            container.addView(track, trackParams);

            TextView statusText = new TextView(dialogCtx);
            statusText.setText(I18n.t(ctx, R.string.ig_dl_progress_status, 0, 0));
            statusText.setTextColor(secondaryText);
            statusText.setTextSize(13f);
            statusText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            statusParams.topMargin = dp(dialogCtx, 14f);
            statusParams.bottomMargin = dp(dialogCtx, 16f);
            container.addView(statusText, statusParams);

            AlertDialog[] dialogHolder = new AlertDialog[1];
            BulkDownloadProgressDialog[] controllerHolder = new BulkDownloadProgressDialog[1];

            TextView actionButton = (TextView) BulkDownloadDialogStyle.buildOutlinedButton(
                    dialogCtx, I18n.t(ctx, R.string.ig_dialog_cancel), () -> {
                        if (controllerHolder[0] != null) controllerHolder[0].cancelled = true;
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    });
            container.addView(actionButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            AlertDialog dialog = new AlertDialog.Builder(dialogCtx)
                    .setCustomTitle(BulkDownloadDialogStyle.buildTitleView(dialogCtx, I18n.t(ctx, R.string.ig_dl_title)))
                    .setView(container)
                    .setCancelable(false)
                    .create();
            dialogHolder[0] = dialog;
            BulkDownloadDialogStyle.applyCardWindow(dialog, dialogCtx);
            dialog.show();

            BulkDownloadProgressDialog controller = new BulkDownloadProgressDialog(
                    ctx, mainHandler, total, dialog, track, fillView, spacerView, countText, statusText, actionButton);
            controllerHolder[0] = controller;
            return controller;
        } catch (Throwable t) {
            ModuleLog.line("(IE|DL) progress dialog unavailable: " + t);
            return new BulkDownloadProgressDialog(ctx, mainHandler, total, null, null, null, null, null, null, null);
        }
    }

    boolean isCancelled() {
        return cancelled;
    }

    void updateProgress(int completed, int saved, int failed) {
        if (dialog == null) return;
        mainHandler.post(() -> {
            if (!dialog.isShowing()) return;
            setFillFraction(total > 0 ? completed / (float) total : 0f);
            countText.setText(I18n.t(appCtx, R.string.ig_dl_progress_count, completed, total));
            statusText.setText(I18n.t(appCtx, R.string.ig_dl_progress_status, saved, failed));
        });
    }

    void finish(int saved, int failed) {
        if (dialog == null) return;
        mainHandler.post(() -> {
            if (!dialog.isShowing()) return;
            setFillFraction(1f);
            countText.setText(failed == 0
                    ? I18n.t(appCtx, R.string.ig_toast_all_items_saved, total)
                    : I18n.t(appCtx, R.string.ig_toast_items_partial_saved, saved, total, failed));
            statusText.setText("");
            actionButton.setText(android.R.string.ok);
        });
    }

    void dismissIfShowing() {
        if (dialog == null) return;
        mainHandler.post(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        });
    }

    private void setFillFraction(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        ((LinearLayout.LayoutParams) fillView.getLayoutParams()).weight = f;
        ((LinearLayout.LayoutParams) spacerView.getLayoutParams()).weight = 1f - f;
        track.requestLayout();
    }
}
