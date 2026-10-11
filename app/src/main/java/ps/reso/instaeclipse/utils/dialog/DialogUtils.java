package ps.reso.instaeclipse.utils.dialog;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.mods.devops.config.ConfigManager;
import ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager;
import ps.reso.instaeclipse.mods.location.LocationPickerActivity;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.mods.ui.theme.IgColorRemapEngine;
import ps.reso.instaeclipse.utils.core.ModuleActivityLauncher;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.ghost.GhostModeUtils;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.ui.EclipseAccents;
import ps.reso.instaeclipse.utils.ui.EclipseRingDrawable;
import ps.reso.instaeclipse.utils.ui.EclipseUiPalette;
import ps.reso.instaeclipse.utils.ui.FeatureAccents;

public class DialogUtils {

    private static AlertDialog currentDialog;
    private static FrameLayout sheetHost;
    private static Runnable currentScreen;
    private static int pendingDirection;
    private static final ArrayDeque<Runnable> backStack = new ArrayDeque<>();
    private static int sectionAccent = FeatureAccents.color(EclipseAccents.PRIMARY);
    private static int sectionIcon = R.drawable.ic_ie_sliders;
    private static android.content.res.Resources moduleRes;
    private static Typeface sansFace;
    private static Typeface displayFace;

    private static final Pattern LEADING_EMOJI = Pattern.compile("^[\\p{So}\\p{Sk}\\u200D\\uFE0F]+\\s*");
    private static final Pattern TRAILING_EMOJI = Pattern.compile("\\s*[\\p{So}\\p{Sk}\\u200D\\uFE0F]+$");
    private static final Pattern TRAILING_COLON = Pattern.compile("\\s*[:：]\\s*$");

    private static EclipseUiPalette pal(Context context) {
        return EclipseUiPalette.resolveModuleSheet(context);
    }

    private static int accentColor() {
        return sectionAccent;
    }

    private static int dangerColor() {
        return Color.parseColor(EclipseAccents.DANGER);
    }

    public static void showEclipseOptionsDialog(Context context) {
        SettingsManager.init(context);
        SettingsManager.syncFromCompanion(context, false);
        SettingsManager.loadAllFlags(context);

        dismissSheet();
        openScreen(() -> showMainMenu(context));
    }

    public static void showSimpleDialog(Context context, String title, String message) {
        showConfirmDialog(context, title, message, I18n.t(context, R.string.ig_dialog_ok), null, false, null, null);
    }

    private static void openScreen(Runnable screen) {
        if (currentScreen != null) backStack.push(currentScreen);
        currentScreen = screen;
        pendingDirection = currentDialog != null ? 1 : 0;
        IgColorRemapEngine.withBypass(screen);
    }

    private static Runnable navTo(Runnable screen) {
        return () -> openScreen(screen);
    }

    private static void navigateBack() {
        SettingsManager.saveAllFlags();
        if (backStack.isEmpty()) {
            dismissSheet();
            return;
        }
        currentScreen = backStack.pop();
        pendingDirection = -1;
        IgColorRemapEngine.withBypass(currentScreen);
    }

    private static void dismissSheet() {
        AlertDialog dialog = currentDialog;
        resetNavigation();
        if (dialog != null) {
            try { dialog.dismiss(); } catch (Exception ignored) {}
        }
    }

    private static void resetNavigation() {
        currentDialog = null;
        sheetHost = null;
        currentScreen = null;
        pendingDirection = 0;
        backStack.clear();
    }

    private static void presentScreen(Context context, View screen) {
        int direction = pendingDirection;
        pendingDirection = 0;

        if (currentDialog != null && currentDialog.isShowing() && sheetHost != null) {
            FrameLayout host = sheetHost;
            host.setTranslationY(0f);
            host.removeAllViews();
            host.addView(screen);
            IgColorRemapEngine.markModuleTree(host);
            animateScreenIn(context, host, screen, direction);
            return;
        }

        EclipseUiPalette p = pal(context);
        FrameLayout host = new FrameLayout(context);
        float radius = dp(context, 28);
        float[] radii = {radius, radius, radius, radius, 0, 0, 0, 0};
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(p.border);
        edge.setCornerRadii(radii);
        GradientDrawable background = new GradientDrawable();
        background.setColor(p.sheetBg);
        background.setCornerRadii(radii);
        LayerDrawable layered = new LayerDrawable(new Drawable[]{edge, background});
        layered.setLayerInset(1, 0, Math.max(1, dp(context, 1)), 0, 0);
        host.setBackground(layered);
        host.addView(screen);
        sheetHost = host;

        AlertDialog dialog = createBottomSheetDialog(context, host);
        currentDialog = dialog;
        installBackHandling(dialog);
        showSheet(dialog);
    }

    private static void animateScreenIn(Context context, View host, View screen, int direction) {
        if (direction == 0) return;
        boolean rtl = host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float shift = dp(context, 28) * direction * (rtl ? -1 : 1);
        screen.setAlpha(0f);
        screen.setTranslationX(shift);
        screen.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private static void installBackHandling(AlertDialog dialog) {
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) navigateBack();
            return true;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, DialogUtils::navigateBack);
            } catch (Throwable ignored) {}
        }
    }

    private static void showSheet(AlertDialog dialog) {
        IgColorRemapEngine.enterModuleUi();
        dialog.setOnDismissListener(d -> {
            IgColorRemapEngine.leaveModuleUi();
            SettingsManager.saveAllFlags();
            if (currentDialog == d) resetNavigation();
        });
        dialog.setOnShowListener(d -> markDialogDecor(dialog));
        IgColorRemapEngine.withBypass(dialog::show);
    }

    private static void showOverlay(AlertDialog dialog, Runnable afterShow) {
        IgColorRemapEngine.enterModuleUi();
        dialog.setOnDismissListener(d -> IgColorRemapEngine.leaveModuleUi());
        dialog.setOnShowListener(d -> markDialogDecor(dialog));
        IgColorRemapEngine.withBypass(() -> {
            dialog.show();
            if (afterShow != null) afterShow.run();
        });
    }

    private static void markDialogDecor(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) IgColorRemapEngine.markModuleTree(window.getDecorView());
        View decor = dialog.findViewById(android.R.id.content);
        if (decor != null) IgColorRemapEngine.markModuleTree(decor);
    }

    private static void showConfirmDialog(Context context, String title, String message, String confirmLabel,
                                          String cancelLabel, boolean destructive, Runnable onConfirm, Runnable onCancel) {
        showConfirmDialog(context, destructive ? R.drawable.ic_ie_trash : 0, title, message, confirmLabel,
                cancelLabel, destructive, onConfirm, onCancel);
    }

    private static void showConfirmDialog(Context context, int iconRes, String title, String message, String confirmLabel,
                                          String cancelLabel, boolean destructive, Runnable onConfirm, Runnable onCancel) {
        try {
            final AlertDialog[] holder = new AlertDialog[1];
            IgColorRemapEngine.withBypass(() -> {
                EclipseUiPalette p = pal(context);
                int tone = destructive ? p.danger : p.accent;
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPaddingRelative(dp(context, 20), dp(context, 24), dp(context, 20), dp(context, 16));
                GradientDrawable cardBg = roundedColor(p.groupBg, dp(context, 26));
                cardBg.setStroke(dp(context, 1), p.border);
                card.setBackground(cardBg);

                if (iconRes != 0) {
                    ImageView icon = new ImageView(context);
                    Drawable drawable = loadModuleIcon(iconRes, destructive ? EclipseUiPalette.DANGER_TEXT : p.accent);
                    if (drawable != null) icon.setImageDrawable(drawable);
                    int pad = dp(context, 12);
                    icon.setPadding(pad, pad, pad, pad);
                    icon.setBackground(roundedColor(EclipseUiPalette.withAlpha(tone, 0x29), dp(context, 16)));
                    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48));
                    iconLp.bottomMargin = dp(context, 14);
                    icon.setLayoutParams(iconLp);
                    card.addView(icon);
                }

                TextView titleView = new TextView(context);
                titleView.setText(stripEdgeEmoji(title));
                titleView.setTextColor(p.primaryText);
                titleView.setTextSize(19);
                titleView.setTypeface(displayFont());
                titleView.setAccessibilityHeading(true);
                card.addView(titleView);

                TextView messageView = new TextView(context);
                messageView.setText(stripEdgeEmoji(message));
                messageView.setTextColor(p.secondaryText);
                messageView.setTextSize(14);
                messageView.setTypeface(font(400));
                messageView.setLineSpacing(0f, 1.3f);
                messageView.setPaddingRelative(0, dp(context, 12), 0, 0);
                card.addView(messageView);

                LinearLayout buttons = new LinearLayout(context);
                buttons.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                buttonsLp.topMargin = dp(context, 20);
                buttons.setLayoutParams(buttonsLp);

                buttons.addView(createDialogButton(context, p, confirmLabel, tone,
                        destructive ? EclipseUiPalette.ON_DANGER : EclipseUiPalette.ON_ACCENT, 0, v -> {
                            holder[0].dismiss();
                            if (onConfirm != null) onConfirm.run();
                        }));
                if (cancelLabel != null) {
                    View cancel = createDialogButton(context, p, cancelLabel, Color.TRANSPARENT, p.primaryText,
                            p.handle, v -> holder[0].cancel());
                    ((LinearLayout.LayoutParams) cancel.getLayoutParams()).topMargin = dp(context, 8);
                    buttons.addView(cancel);
                }
                card.addView(buttons);

                IgColorRemapEngine.markModuleTree(card);
                AlertDialog dialog = new AlertDialog.Builder(context).setView(card).create();
                Window window = dialog.getWindow();
                if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.setOnCancelListener(d -> {
                    if (onCancel != null) onCancel.run();
                });
                holder[0] = dialog;
            });
            AlertDialog dialog = holder[0];
            showOverlay(dialog, () -> {
                Window window = dialog.getWindow();
                if (window == null) return;
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int width = Math.min(screenWidth - dp(context, 48), dp(context, 360));
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            });
        } catch (Exception e) {
            ModuleLog.line("InstaEclipse | ❌ Failed to show dialog: " + e.getMessage());
        }
    }

    private static View createDialogButton(Context context, EclipseUiPalette p, String label, int bgColor, int textColor,
                                           int strokeColor, View.OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(font(strokeColor == 0 ? 600 : 500));
        button.setGravity(Gravity.CENTER);
        button.setPaddingRelative(dp(context, 14), 0, dp(context, 14), 0);
        GradientDrawable bg = roundedColor(bgColor, dp(context, 14));
        if (strokeColor != 0) bg.setStroke(dp(context, 1), strokeColor);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(EclipseUiPalette.withAlpha(0xFFFFFF, 0x20)), bg, null));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50)));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(onClick);
        setButtonRole(button);
        return button;
    }

    private static void showMainMenu(Context context) {
        sectionAccent = FeatureAccents.primary();
        sectionIcon = R.drawable.ic_ie_sliders;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        addSpacer(context, body, 4);
        body.addView(createGhostQuickCard(context));

        body.addView(sectionHeader(context, p, I18n.t(context, R.string.feat_categories)));
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        addTileRow(context, grid,
                createCategoryTile(context, p, R.drawable.ic_ie_code, I18n.t(context, R.string.ig_dialog_menu_dev_options), null,
                        new boolean[]{FeatureFlags.isDevEnabled, FeatureFlags.removeBuildExpiredPopup},
                        FeatureAccents.color(FeatureAccents.DEV), navTo(() -> showDevOptions(context))),
                createCategoryTile(context, p, R.drawable.ic_ie_ghost, I18n.t(context, R.string.ig_dialog_menu_ghost_settings), null,
                        ghostStates(), FeatureAccents.color(FeatureAccents.GHOST), navTo(() -> showGhostOptions(context))));
        addTileRow(context, grid,
                createCategoryTile(context, p, R.drawable.ic_ie_shield_x, I18n.t(context, R.string.ig_dialog_menu_ad_analytics), null,
                        new boolean[]{FeatureFlags.isAdBlockEnabled, FeatureFlags.isAnalyticsBlocked, FeatureFlags.disableTrackingLinks},
                        FeatureAccents.color(FeatureAccents.ADS), navTo(() -> showAdOptions(context))),
                createCategoryTile(context, p, R.drawable.ic_ie_lines, I18n.t(context, R.string.ig_dialog_menu_clean_feed), null,
                        new boolean[]{FeatureFlags.hideSuggestionsInFeed, FeatureFlags.hideThreadsSuggestions},
                        FeatureAccents.color(FeatureAccents.CLEAN_FEED), navTo(() -> showCleanFeedOptions(context))));
        addTileRow(context, grid,
                createCategoryTile(context, p, R.drawable.ic_ie_target, I18n.t(context, R.string.ig_dialog_menu_distraction_free), null,
                        new boolean[]{FeatureFlags.disableStories, FeatureFlags.disableFeed, FeatureFlags.disableReels,
                                FeatureFlags.disableReelsExceptDM, FeatureFlags.disableExplore, FeatureFlags.disableComments},
                        FeatureAccents.color(FeatureAccents.DISTRACTION), navTo(() -> showDistractionOptions(context))),
                createCategoryTile(context, p, R.drawable.ic_ie_sliders, I18n.t(context, R.string.ig_dialog_menu_misc), null,
                        miscStates(), FeatureAccents.color(FeatureAccents.MISC), navTo(() -> showMiscOptions(context))));
        addTileRow(context, grid,
                createCategoryTile(context, p, R.drawable.ic_ie_download, I18n.t(context, R.string.ig_dialog_menu_downloader), null,
                        new boolean[]{FeatureFlags.enablePostDownload, FeatureFlags.enableStoryDownload, FeatureFlags.enableReelDownload,
                                FeatureFlags.enableProfileDownload, FeatureFlags.enableVoiceDownload},
                        FeatureAccents.color(FeatureAccents.DOWNLOADER), navTo(() -> showDownloaderOptions(context))),
                createCategoryTile(context, p, R.drawable.ic_ie_pin, I18n.t(context, R.string.ig_dialog_menu_location), null,
                        new boolean[]{FeatureFlags.spoofLocation}, FeatureAccents.color(FeatureAccents.LOCATION),
                        navTo(() -> showLocationOptions(context))));
        addTileRow(context, grid,
                createCategoryTile(context, p, R.drawable.ic_ie_film, I18n.t(context, R.string.ig_dialog_menu_quality),
                        qualityLabel(context, FeatureFlags.forceReelQuality), new boolean[]{FeatureFlags.forceReelQuality != 0},
                        FeatureAccents.color(FeatureAccents.QUALITY), navTo(() -> showQualityOptions(context))),
                createCategoryTile(context, p, R.drawable.ic_ie_palette, I18n.t(context, R.string.theme_customize), null,
                        new boolean[]{FeatureFlags.customThemeEnabled}, FeatureAccents.color(FeatureAccents.THEME),
                        () -> openThemeCustomizer(context)));
        body.addView(grid);

        String iconStyle = I18n.t(context, FeatureFlags.colorfulFeatureIcons
                ? R.string.ig_dialog_icon_style_colorful : R.string.ig_dialog_icon_style_yellow);
        LinearLayout tools = addCard(context, p, body, I18n.t(context, R.string.feat_tools));
        tools.addView(createMenuRow(context, p, R.drawable.ic_ie_brush, I18n.t(context, R.string.ig_dialog_icon_style), iconStyle,
                FeatureAccents.color(FeatureAccents.THEME), navTo(() -> showIconStyleOptions(context))));
        tools.addView(createMenuRow(context, p, R.drawable.ic_ie_archive, I18n.t(context, R.string.ig_dialog_menu_backup_restore), null,
                FeatureAccents.color(FeatureAccents.TOOLS), navTo(() -> showBackupRestoreOptions(context))));
        tools.addView(createMenuRow(context, p, R.drawable.ic_ie_info, I18n.t(context, R.string.ig_dialog_menu_about), null,
                FeatureAccents.color(FeatureAccents.NEUTRAL), navTo(() -> showAboutDialog(context))));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(context, 8);
        actions.setLayoutParams(actionsLp);
        actions.addView(createSheetButton(context, R.drawable.ic_ie_restart, I18n.t(context, R.string.ig_dialog_menu_restart),
                p.groupBg, p.primaryText, () -> confirmRestart(context)));
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 8), 1));
        actions.addView(gap);
        actions.addView(createSheetButton(context, R.drawable.ic_ie_trash, I18n.t(context, R.string.ig_dialog_clear_cache),
                EclipseUiPalette.withAlpha(p.danger, 0x1F), p.danger, () -> confirmClearCache(context)));
        body.addView(actions);

        TextView footer = new TextView(context);
        footer.setText("@reso7200");
        footer.setTextColor(p.muted);
        footer.setTextSize(12);
        footer.setTypeface(font(400));
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, dp(context, 16), 0, 0);
        body.addView(footer);

        presentScreen(context, buildScreen(context, p, I18n.t(context, R.string.ig_dialog_title), true,
                body, null, 0.7f));

        SettingsManager.saveAllFlags();
        refreshGhostEmoji();
    }

    private static boolean[] ghostStates() {
        return new boolean[]{FeatureFlags.isGhostSeen, FeatureFlags.isGhostTyping, FeatureFlags.isGhostStory,
                FeatureFlags.isGhostLive, FeatureFlags.allowScreenshots, FeatureFlags.isGhostScreenshot,
                FeatureFlags.isGhostViewOnce, FeatureFlags.enableUnlimitedReplays,
                FeatureFlags.permanentViewMode, FeatureFlags.keepEphemeralMessages};
    }

    private static boolean[] miscStates() {
        return new boolean[]{FeatureFlags.disableStoryFlipping, FeatureFlags.disableVideoAutoPlay, FeatureFlags.enableStoryMentions,
                FeatureFlags.enablePhotoZoom, FeatureFlags.disableRepost, FeatureFlags.disableDoubleTapLike,
                FeatureFlags.enableCopyComment, FeatureFlags.enableCaptionCopy, FeatureFlags.disableDiscoverPeople,
                FeatureFlags.showFeatureToasts, FeatureFlags.showFollowerToast, FeatureFlags.spoofLastSeen};
    }

    private static View createGhostQuickCard(Context context) {
        boolean active = GhostModeUtils.isGhostModeActive();
        ToggleRow card = new ToggleRow(context, R.drawable.ic_ie_ghost,
                I18n.t(context, R.string.ig_dialog_section_ghost_mode), active, FeatureAccents.primary());
        card.asMasterCard();
        card.setSummary(I18n.t(context, active ? R.string.sheet_ghost_on : R.string.sheet_ghost_off));
        card.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GhostModeUtils.toggleSelectedGhostOptions(context);
            IgColorRemapEngine.withBypass(() -> showMainMenu(context));
        });
        return card;
    }

    private static void addTileRow(Context context, LinearLayout grid, View first, View second) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (grid.getChildCount() > 0) rowLp.topMargin = dp(context, 8);
        row.setLayoutParams(rowLp);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        a.setMarginEnd(dp(context, 4));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        b.setMarginStart(dp(context, 4));
        row.addView(first, a);
        row.addView(second, b);
        grid.addView(row);
    }

    private static View createCategoryTile(Context context, EclipseUiPalette p, int iconRes, String label, String value,
                                           boolean[] states, int accent, Runnable onClick) {
        int onCount = 0;
        for (boolean state : states) {
            if (state) onCount++;
        }
        boolean active = onCount > 0;
        String count = value == null ? onCount + "/" + states.length : null;

        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setMinimumHeight(dp(context, 58));
        tile.setPaddingRelative(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(p.pressed),
                roundedColor(p.groupBg, dp(context, 18)), null));
        tile.setClickable(true);
        tile.setFocusable(true);

        ImageView chip = buildIconChip(context, iconRes, accent, active ? p.accent : p.secondaryText);
        chip.setBackground(roundedColor(FeatureAccents.isColorful() ? EclipseUiPalette.withAlpha(accent, 0x33) : p.chipBg, dp(context, 12)));
        LinearLayout.LayoutParams chipLp = (LinearLayout.LayoutParams) chip.getLayoutParams();
        chipLp.width = dp(context, 36);
        chipLp.height = dp(context, 36);
        chipLp.setMarginEnd(dp(context, 10));
        chip.setLayoutParams(chipLp);
        tile.addView(chip);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(context);
        name.setText(stripEdgeEmoji(label));
        name.setTextColor(p.primaryText);
        name.setTextSize(13.5f);
        name.setTypeface(font(500));
        name.setLineSpacing(0f, 1.05f);
        texts.addView(name);
        if (value != null) {
            TextView valueView = new TextView(context);
            valueView.setText(value);
            valueView.setTextColor(p.sectionLabel);
            valueView.setTextSize(11.5f);
            valueView.setTypeface(font(400));
            valueView.setMaxLines(1);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(valueView);
        }
        tile.addView(texts);

        if (count != null) {
            TextView countView = new TextView(context);
            countView.setText(count);
            countView.setTextColor(active ? p.accent : p.sectionLabel);
            countView.setTextSize(12);
            countView.setTypeface(font(600));
            countView.setTextDirection(View.TEXT_DIRECTION_LTR);
            countView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            countView.setPaddingRelative(dp(context, 6), 0, 0, 0);
            tile.addView(countView);
        }

        String description = value != null ? label + ", " + value : label;
        if (count != null) description += ", " + count;
        tile.setContentDescription(description);
        tile.setOnClickListener(v -> onClick.run());
        setButtonRole(tile);
        return tile;
    }

    private static View createSheetButton(Context context, int iconRes, String label, int bgColor, int fgColor, Runnable onClick) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPaddingRelative(dp(context, 10), 0, dp(context, 10), 0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(pal(context).pressed),
                roundedColor(bgColor, dp(context, 16)), null));
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dp(context, 52), 1f));
        button.setClickable(true);
        button.setFocusable(true);

        Drawable icon = loadModuleIcon(iconRes, fgColor);
        if (icon != null) {
            ImageView iconView = new ImageView(context);
            iconView.setImageDrawable(icon);
            iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
            lp.setMarginEnd(dp(context, 8));
            iconView.setLayoutParams(lp);
            button.addView(iconView);
        }
        TextView text = new TextView(context);
        text.setText(stripEdgeEmoji(label));
        text.setTextColor(fgColor);
        text.setTextSize(14);
        text.setTypeface(font(600));
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        button.addView(text);

        button.setContentDescription(label);
        button.setOnClickListener(v -> onClick.run());
        setButtonRole(button);
        return button;
    }

    private static void showDevOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.DEV);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow devModeSwitch = createSwitch(context, R.drawable.ic_tune, I18n.t(context, R.string.ig_dialog_dev_enable), FeatureFlags.isDevEnabled);
        devModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.isDevEnabled = isChecked;
            SettingsManager.saveAllFlags();
        });
        addCard(context, p, body, I18n.t(context, R.string.feat_features)).addView(devModeSwitch);

        LinearLayout config = addCard(context, p, body, I18n.t(context, R.string.feat_config));
        config.addView(createActionRow(context, R.drawable.ic_download, I18n.t(context, R.string.ig_dialog_dev_import), null,
                FeatureAccents.color(FeatureAccents.SUCCESS), false, () -> {
                    Activity instagramActivity = UIHookManager.getCurrentActivity();
                    if (instagramActivity == null || instagramActivity.isFinishing()) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
                        return;
                    }
                    Intent importIntent = new Intent();
                    importIntent.setComponent(new ComponentName("ps.reso.instaeclipse", "ps.reso.instaeclipse.mods.devops.config.JsonImportActivity"));
                    importIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    importIntent.putExtra("target_package", context.getPackageName());
                    try {
                        instagramActivity.startActivity(importIntent);
                    } catch (Exception e) {
                        ModuleLog.line("InstaEclipse | ❌ Failed to start JsonImportActivity: " + e.getMessage());
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_unable_open_ui));
                    }
                }));
        config.addView(createActionRow(context, R.drawable.ic_upload, I18n.t(context, R.string.ig_dialog_dev_export), null,
                FeatureAccents.color(FeatureAccents.INFO), false, () -> {
                    Activity instagramActivity = UIHookManager.getCurrentActivity();
                    if (instagramActivity == null || instagramActivity.isFinishing()) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
                        return;
                    }
                    try {
                        File source = new File(context.getFilesDir(), "mobileconfig/mc_overrides.json");
                        if (!source.exists()) {
                            showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_mc_overrides_not_found));
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new FileReader(source))) {
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                        }
                        Intent exportIntent = new Intent();
                        exportIntent.setComponent(new ComponentName("ps.reso.instaeclipse", "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                        exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        exportIntent.putExtra("json_content", sb.toString().trim());
                        instagramActivity.startActivity(exportIntent);
                    } catch (Exception e) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_failed_read_config, e.getMessage()));
                    }
                }));
        config.addView(createActionRow(context, R.drawable.ic_restart, I18n.t(context, R.string.ig_dialog_dev_restore_default_config), null,
                FeatureAccents.color(FeatureAccents.DOWNLOADER), false, () -> showConfirmDialog(context,
                        I18n.t(context, R.string.ig_dialog_dev_restore_default_config),
                        I18n.t(context, R.string.ig_dialog_dev_restore_default_config_confirm),
                        I18n.t(context, R.string.ig_dialog_yes),
                        I18n.t(context, R.string.ig_dialog_cancel),
                        true,
                        () -> ConfigManager.restoreDefaultConfig(context, Module.moduleSourceDir),
                        null)));

        ToggleRow buildExpiredSwitch = createSwitch(context, R.drawable.ic_block, I18n.t(context, R.string.ig_dialog_dev_remove_build_expired), FeatureFlags.removeBuildExpiredPopup);
        buildExpiredSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.removeBuildExpiredPopup = isChecked;
            SettingsManager.saveAllFlags();
        });
        addCard(context, p, body, I18n.t(context, R.string.feat_options)).addView(buildExpiredSwitch);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_dev_options), body);
    }

    private static void showGhostOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.GHOST);
        sectionIcon = R.drawable.ic_ie_ghost;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow[] rows = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_ie_eye_off, I18n.t(context, R.string.ig_dialog_ghost_hide_dm_seen), FeatureFlags.isGhostSeen),
                createSwitch(context, R.drawable.ic_ie_typing, I18n.t(context, R.string.ig_dialog_ghost_hide_typing), FeatureFlags.isGhostTyping),
                createSwitch(context, R.drawable.ic_ie_story, I18n.t(context, R.string.ig_dialog_ghost_hide_story_views), FeatureFlags.isGhostStory),
                createSwitch(context, R.drawable.ic_ie_live, I18n.t(context, R.string.ig_dialog_ghost_hide_live_presence), FeatureFlags.isGhostLive),
                createSwitch(context, R.drawable.ic_ie_camera, I18n.t(context, R.string.ig_dialog_ghost_allow_screenshots_dms), FeatureFlags.allowScreenshots),
                createSwitch(context, R.drawable.ic_ie_smartphone, I18n.t(context, R.string.ig_dialog_ghost_bypass_screenshot), FeatureFlags.isGhostScreenshot),
                createSwitch(context, R.drawable.ic_ie_image, I18n.t(context, R.string.ig_dialog_ghost_hide_view_once), FeatureFlags.isGhostViewOnce),
                createSwitch(context, R.drawable.ic_ie_repeat, I18n.t(context, R.string.ig_dialog_ghost_unlimited_replays), FeatureFlags.enableUnlimitedReplays),
                createSwitch(context, R.drawable.ic_ie_eye, I18n.t(context, R.string.ig_dialog_ghost_permanent_view_once), FeatureFlags.permanentViewMode),
                createSwitch(context, R.drawable.ic_ie_timer, I18n.t(context, R.string.ig_dialog_ghost_keep_disappearing), FeatureFlags.keepEphemeralMessages)};
        rows[8].markUnstable();

        ToggleRow master = wireToggleGroup(context, rows, (index, value) -> {
            switch (index) {
                case 0 -> FeatureFlags.isGhostSeen = value;
                case 1 -> FeatureFlags.isGhostTyping = value;
                case 2 -> FeatureFlags.isGhostStory = value;
                case 3 -> FeatureFlags.isGhostLive = value;
                case 4 -> FeatureFlags.allowScreenshots = value;
                case 5 -> FeatureFlags.isGhostScreenshot = value;
                case 6 -> FeatureFlags.isGhostViewOnce = value;
                case 7 -> FeatureFlags.enableUnlimitedReplays = value;
                case 8 -> FeatureFlags.permanentViewMode = value;
                case 9 -> FeatureFlags.keepEphemeralMessages = value;
            }
        }, DialogUtils::refreshGhostEmoji);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_options), master, rows);

        addCard(context, p, body, null).addView(
                createActionRow(context, R.drawable.ic_ie_zap, I18n.t(context, R.string.ig_dialog_customize_quick_toggle),
                        I18n.t(context, R.string.feat_quick_toggle_desc), accentColor(), true,
                        navTo(() -> showGhostQuickToggleOptions(context))));

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_ghost_mode), body);
    }

    private static void showGhostQuickToggleOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.GHOST);
        sectionIcon = R.drawable.ic_ie_zap;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow[] rows = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_ie_eye_off, I18n.t(context, R.string.ig_dialog_quick_hide_seen), FeatureFlags.quickToggleSeen),
                createSwitch(context, R.drawable.ic_ie_typing, I18n.t(context, R.string.ig_dialog_quick_hide_typing), FeatureFlags.quickToggleTyping),
                createSwitch(context, R.drawable.ic_ie_smartphone, I18n.t(context, R.string.ig_dialog_quick_disable_screenshot), FeatureFlags.quickToggleScreenshot),
                createSwitch(context, R.drawable.ic_ie_image, I18n.t(context, R.string.ig_dialog_quick_hide_view_once), FeatureFlags.quickToggleViewOnce),
                createSwitch(context, R.drawable.ic_ie_story, I18n.t(context, R.string.ig_dialog_quick_hide_story_seen), FeatureFlags.quickToggleStory),
                createSwitch(context, R.drawable.ic_ie_live, I18n.t(context, R.string.ig_dialog_quick_hide_live_seen), FeatureFlags.quickToggleLive),
                createSwitch(context, R.drawable.ic_ie_timer, I18n.t(context, R.string.ig_dialog_quick_keep_ephemeral), FeatureFlags.quickToggleEphemeral),
                createSwitch(context, R.drawable.ic_ie_repeat, I18n.t(context, R.string.ig_dialog_quick_unlimited_replays), FeatureFlags.quickToggleReplays),
                createSwitch(context, R.drawable.ic_ie_eye, I18n.t(context, R.string.ig_dialog_quick_permanent_view), FeatureFlags.quickTogglePermanentView),
                createSwitch(context, R.drawable.ic_ie_camera, I18n.t(context, R.string.ig_dialog_quick_allow_screenshots), FeatureFlags.quickToggleAllowScreenshots)};
        rows[8].markUnstable();

        ToggleRow master = wireToggleGroup(context, rows, (index, value) -> {
            switch (index) {
                case 0 -> FeatureFlags.quickToggleSeen = value;
                case 1 -> FeatureFlags.quickToggleTyping = value;
                case 2 -> FeatureFlags.quickToggleScreenshot = value;
                case 3 -> FeatureFlags.quickToggleViewOnce = value;
                case 4 -> FeatureFlags.quickToggleStory = value;
                case 5 -> FeatureFlags.quickToggleLive = value;
                case 6 -> FeatureFlags.quickToggleEphemeral = value;
                case 7 -> FeatureFlags.quickToggleReplays = value;
                case 8 -> FeatureFlags.quickTogglePermanentView = value;
                case 9 -> FeatureFlags.quickToggleAllowScreenshots = value;
            }
        }, DialogUtils::refreshGhostEmoji);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_options), master, rows);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_quick_toggle), body);
    }

    private static void showAdOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.ADS);
        sectionIcon = R.drawable.ic_ie_shield_x;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow[] rows = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_shield, I18n.t(context, R.string.ig_dialog_ad_block_ads), FeatureFlags.isAdBlockEnabled),
                createSwitch(context, R.drawable.ic_analytics, I18n.t(context, R.string.ig_dialog_ad_block_analytics), FeatureFlags.isAnalyticsBlocked),
                createSwitch(context, R.drawable.ic_link, I18n.t(context, R.string.ig_dialog_ad_disable_tracking), FeatureFlags.disableTrackingLinks)};

        ToggleRow master = wireToggleGroup(context, rows, (index, value) -> {
            switch (index) {
                case 0 -> FeatureFlags.isAdBlockEnabled = value;
                case 1 -> FeatureFlags.isAnalyticsBlocked = value;
                case 2 -> FeatureFlags.disableTrackingLinks = value;
            }
        }, null);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_features), master, rows);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_ad_analytics), body);
    }

    private static void showCleanFeedOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.CLEAN_FEED);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow hideSuggested = createSwitch(context, R.drawable.ic_sparkle, I18n.t(context, R.string.ig_dialog_clean_feed_hide_suggested), FeatureFlags.hideSuggestionsInFeed);
        hideSuggested.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.hideSuggestionsInFeed = isChecked;
            SettingsManager.saveAllFlags();
        });

        ToggleRow hideThreads = createSwitch(context, R.drawable.ic_at, I18n.t(context, R.string.ig_dialog_clean_feed_hide_threads), FeatureFlags.hideThreadsSuggestions);
        hideThreads.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.hideThreadsSuggestions = isChecked;
            SettingsManager.saveAllFlags();
        });

        addToggleCard(context, p, body, I18n.t(context, R.string.feat_features), null, hideSuggested, hideThreads);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_clean_feed), body);
    }

    private static void showDistractionOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.DISTRACTION);
        sectionIcon = R.drawable.ic_ie_target;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow extreme = createSwitch(context, R.drawable.ic_ie_warning, I18n.t(context, R.string.extreme_mode_title), FeatureFlags.isExtremeMode, dangerColor());
        extreme.asDangerCard();
        ToggleRow stories = createSwitch(context, R.drawable.ic_ie_story, I18n.t(context, R.string.ig_dialog_distraction_disable_stories), FeatureFlags.disableStories);
        ToggleRow feed = createSwitch(context, R.drawable.ic_ie_lines, I18n.t(context, R.string.ig_dialog_distraction_disable_feed), FeatureFlags.disableFeed);
        ToggleRow reels = createSwitch(context, R.drawable.ic_ie_reels, I18n.t(context, R.string.ig_dialog_distraction_disable_reels), FeatureFlags.disableReels);
        ToggleRow reelsExceptDm = createSwitch(context, R.drawable.ic_ie_bubble, I18n.t(context, R.string.ig_dialog_distraction_disable_reels_except_dm), FeatureFlags.disableReelsExceptDM);
        reelsExceptDm.setIndentStart(40);
        ToggleRow explore = createSwitch(context, R.drawable.ic_ie_compass, I18n.t(context, R.string.ig_dialog_distraction_disable_explore), FeatureFlags.disableExplore);
        ToggleRow comments = createSwitch(context, R.drawable.ic_ie_comment, I18n.t(context, R.string.ig_dialog_distraction_disable_comments), FeatureFlags.disableComments);
        ToggleRow[] rows = new ToggleRow[]{stories, feed, reels, reelsExceptDm, explore, comments};
        ToggleRow master = createMasterSwitch(context, anyEnabled(rows));

        Runnable refresh = () -> {
            FeatureFlags.disableStories = stories.isChecked();
            FeatureFlags.disableFeed = feed.isChecked();
            FeatureFlags.disableReels = reels.isChecked();
            FeatureFlags.disableReelsExceptDM = reelsExceptDm.isChecked();
            FeatureFlags.disableExplore = explore.isChecked();
            FeatureFlags.disableComments = comments.isChecked();
            SettingsManager.saveAllFlags();

            master.setCheckedSilently(anyEnabled(rows));
            master.setSummary(masterSummary(context, rows));
            if (FeatureFlags.isExtremeMode) {
                for (ToggleRow row : rows) {
                    boolean dm = row == reelsExceptDm;
                    row.setEnabled(true);
                    row.setLocked(dm ? !row.isChecked() : row.isChecked(),
                            I18n.t(context, dm ? R.string.extreme_dm_locked_hint : R.string.extreme_locked_hint));
                }
                master.setEnabled(false);
                extreme.setCheckedSilently(true);
                extreme.setEnabled(true);
                extreme.setLocked(true, null);
                extreme.setSubtitle(I18n.t(context, R.string.extreme_mode_active_hint));
            } else {
                for (ToggleRow row : rows) row.setLocked(false, null);
                reelsExceptDm.setEnabled(reels.isChecked());
                boolean anyEnabled = anyEnabled(rows);
                extreme.setEnabled(anyEnabled);
                extreme.setSubtitle(I18n.t(context, anyEnabled ? R.string.extreme_mode_desc : R.string.extreme_mode_requires_hint));
            }
        };

        master.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow row : rows) row.setCheckedSilently(isChecked);
            refresh.run();
        });
        reels.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) reelsExceptDm.setCheckedSilently(false);
            refresh.run();
        });
        reelsExceptDm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !reels.isChecked()) reels.setCheckedSilently(true);
            refresh.run();
        });
        for (ToggleRow row : new ToggleRow[]{stories, feed, explore, comments}) {
            row.setOnCheckedChangeListener((buttonView, isChecked) -> refresh.run());
        }
        extreme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked || FeatureFlags.isExtremeMode) return;
            showConfirmDialog(context, R.drawable.ic_ie_lock,
                    I18n.t(context, R.string.ig_dialog_distraction_extreme_title),
                    I18n.t(context, R.string.ig_dialog_distraction_extreme_message),
                    I18n.t(context, R.string.extreme_confirm_button),
                    I18n.t(context, R.string.ig_dialog_cancel),
                    true,
                    () -> {
                        FeatureFlags.isExtremeMode = true;
                        FeatureFlags.isDistractionFree = true;
                        refresh.run();
                    },
                    () -> extreme.setCheckedSilently(false));
        });

        refresh.run();

        addSpacer(context, body, 12);
        body.addView(extreme);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_features), master, rows);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_distraction_free), body);
    }

    private static void showMiscOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.MISC);
        sectionIcon = R.drawable.ic_ie_sliders;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow[] rows = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_story_ring, I18n.t(context, R.string.ig_dialog_misc_disable_story_autoswipe), FeatureFlags.disableStoryFlipping),
                createSwitch(context, R.drawable.ic_play_circle, I18n.t(context, R.string.ig_dialog_misc_disable_video_autoplay), FeatureFlags.disableVideoAutoPlay),
                createSwitch(context, R.drawable.ic_at, I18n.t(context, R.string.ig_dialog_misc_view_story_mentions), FeatureFlags.enableStoryMentions),
                createSwitch(context, R.drawable.ic_zoom_in, I18n.t(context, R.string.ig_dialog_misc_photo_zoom), FeatureFlags.enablePhotoZoom),
                createSwitch(context, R.drawable.ic_repeat, I18n.t(context, R.string.ig_dialog_misc_disable_repost), FeatureFlags.disableRepost),
                createSwitch(context, R.drawable.ic_heart, I18n.t(context, R.string.ig_dialog_misc_disable_double_tap_like), FeatureFlags.disableDoubleTapLike),
                createSwitch(context, R.drawable.ic_content_copy, I18n.t(context, R.string.ig_dialog_misc_copy_comment), FeatureFlags.enableCopyComment),
                createSwitch(context, R.drawable.ic_subject, I18n.t(context, R.string.ig_dialog_misc_copy_caption), FeatureFlags.enableCaptionCopy),
                createSwitch(context, R.drawable.ic_group, I18n.t(context, R.string.ig_dialog_misc_disable_discover_people), FeatureFlags.disableDiscoverPeople),
                createSwitch(context, R.drawable.ic_notification, I18n.t(context, R.string.ig_dialog_misc_show_feature_toasts), FeatureFlags.showFeatureToasts),
                createSwitch(context, R.drawable.ic_person_add, I18n.t(context, R.string.ig_dialog_misc_show_follower_toast), FeatureFlags.showFollowerToast),
                createSwitch(context, R.drawable.ic_timer, I18n.t(context, R.string.ig_dialog_misc_spoof_last_seen), FeatureFlags.spoofLastSeen)
        };

        ToggleRow master = wireToggleGroup(context, rows, (index, value) -> {
            switch (index) {
                case 0 -> FeatureFlags.disableStoryFlipping = value;
                case 1 -> FeatureFlags.disableVideoAutoPlay = value;
                case 2 -> FeatureFlags.enableStoryMentions = value;
                case 3 -> FeatureFlags.enablePhotoZoom = value;
                case 4 -> FeatureFlags.disableRepost = value;
                case 5 -> FeatureFlags.disableDoubleTapLike = value;
                case 6 -> FeatureFlags.enableCopyComment = value;
                case 7 -> FeatureFlags.enableCaptionCopy = value;
                case 8 -> FeatureFlags.disableDiscoverPeople = value;
                case 9 -> FeatureFlags.showFeatureToasts = value;
                case 10 -> FeatureFlags.showFollowerToast = value;
                case 11 -> FeatureFlags.spoofLastSeen = value;
            }
        }, null);

        addSpacer(context, body, 12);
        body.addView(master);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_group_stories_video), null, rows[0], rows[1], rows[2], rows[3]);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_group_interactions), null, rows[4], rows[5], rows[6], rows[7], rows[8]);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_group_notifications), null, rows[9], rows[10]);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_group_privacy), null, rows[11]);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_misc), body);
    }

    private static void showLocationOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.LOCATION);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        ToggleRow spoofSwitch = createSwitch(context, R.drawable.ic_pin, I18n.t(context, R.string.ig_dialog_location_spoof_enable), FeatureFlags.spoofLocation);
        spoofSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.spoofLocation = isChecked;
            SettingsManager.saveAllFlags();
        });
        addCard(context, p, body, I18n.t(context, R.string.feat_features)).addView(spoofSwitch);

        String coordLabel = (FeatureFlags.spoofLat == 0.0 && FeatureFlags.spoofLng == 0.0)
                ? I18n.t(context, R.string.ig_dialog_location_unset)
                : I18n.t(context, R.string.ig_dialog_location_current, FeatureFlags.spoofLat, FeatureFlags.spoofLng);

        addCard(context, p, body, I18n.t(context, R.string.feat_options)).addView(createActionRow(context, R.drawable.ic_explore,
                I18n.t(context, R.string.ig_dialog_location_pick), coordLabel, accentColor(), true, () -> {
                    Bundle extras = new Bundle();
                    extras.putDouble(LocationPickerActivity.EXTRA_LAT, FeatureFlags.spoofLat);
                    extras.putDouble(LocationPickerActivity.EXTRA_LNG, FeatureFlags.spoofLng);
                    if (ModuleActivityLauncher.launch(context,
                            "ps.reso.instaeclipse.mods.location.LocationPickerActivity", extras)) {
                        dismissSheet();
                    }
                }));

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_location), body);
    }

    private static void openThemeCustomizer(Context context) {
        if (ModuleActivityLauncher.launch(context,
                "ps.reso.instaeclipse.ui.theme.ThemeCustomizerActivity", null)) {
            dismissSheet();
        }
    }

    private static String qualityLabel(Context context, int h) {
        if (h == 360) return I18n.t(context, R.string.ig_dialog_quality_360);
        if (h == 480) return I18n.t(context, R.string.ig_dialog_quality_480);
        if (h == 720) return I18n.t(context, R.string.ig_dialog_quality_720);
        if (h == 1080) return I18n.t(context, R.string.ig_dialog_quality_1080);
        if (h == Integer.MAX_VALUE) return I18n.t(context, R.string.ig_dialog_quality_max);
        return I18n.t(context, R.string.ig_dialog_quality_auto);
    }

    private static void showQualityOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.QUALITY);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        int[] values = {0, 360, 480, 720, 1080, Integer.MAX_VALUE};
        RadioRow[] rows = new RadioRow[values.length];
        LinearLayout card = addCard(context, p, body, I18n.t(context, R.string.ig_dialog_quality_force_reels));
        for (int i = 0; i < values.length; i++) {
            rows[i] = new RadioRow(context, qualityLabel(context, values[i]), values[i] == FeatureFlags.forceReelQuality);
            int value = values[i];
            RadioRow selected = rows[i];
            rows[i].setOnClickListener(v -> {
                FeatureFlags.forceReelQuality = value;
                SettingsManager.saveAllFlags();
                for (RadioRow r : rows) r.setChecked(r == selected);
            });
            card.addView(rows[i]);
        }

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_quality), body);
    }

    private static void showIconStyleOptions(Context context) {
        sectionAccent = FeatureAccents.primary();
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        boolean colorful = FeatureFlags.colorfulFeatureIcons;
        RadioRow yellowRow = new RadioRow(context, I18n.t(context, R.string.ig_dialog_icon_style_yellow), !colorful);
        RadioRow colorfulRow = new RadioRow(context, I18n.t(context, R.string.ig_dialog_icon_style_colorful), colorful);
        yellowRow.setOnClickListener(v -> {
            FeatureFlags.colorfulFeatureIcons = false;
            SettingsManager.saveAllFlags();
            yellowRow.setChecked(true);
            colorfulRow.setChecked(false);
        });
        colorfulRow.setOnClickListener(v -> {
            FeatureFlags.colorfulFeatureIcons = true;
            SettingsManager.saveAllFlags();
            yellowRow.setChecked(false);
            colorfulRow.setChecked(true);
        });

        LinearLayout card = addCard(context, p, body, I18n.t(context, R.string.feat_options));
        card.addView(yellowRow);
        card.addView(colorfulRow);

        presentSection(context, I18n.t(context, R.string.ig_dialog_icon_style), body);
    }

    private static void showDownloaderOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.DOWNLOADER);
        sectionIcon = R.drawable.ic_ie_download;
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        addCard(context, p, body, null).addView(createActionRow(context, R.drawable.ic_folder,
                I18n.t(context, R.string.ig_dialog_downloader_settings), null, accentColor(), true,
                navTo(() -> showDownloaderSettings(context))));

        ToggleRow[] rows = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_image, I18n.t(context, R.string.ig_dialog_downloader_posts), FeatureFlags.enablePostDownload),
                createSwitch(context, R.drawable.ic_story_ring, I18n.t(context, R.string.ig_dialog_downloader_stories), FeatureFlags.enableStoryDownload),
                createSwitch(context, R.drawable.ic_movie, I18n.t(context, R.string.ig_dialog_downloader_reels), FeatureFlags.enableReelDownload),
                createSwitch(context, R.drawable.ic_person, I18n.t(context, R.string.ig_dialog_downloader_profiles), FeatureFlags.enableProfileDownload),
                createSwitch(context, R.drawable.ic_mic, I18n.t(context, R.string.ig_dialog_downloader_voice), FeatureFlags.enableVoiceDownload)};

        ToggleRow master = wireToggleGroup(context, rows, (index, value) -> {
            switch (index) {
                case 0 -> FeatureFlags.enablePostDownload = value;
                case 1 -> FeatureFlags.enableStoryDownload = value;
                case 2 -> FeatureFlags.enableReelDownload = value;
                case 3 -> FeatureFlags.enableProfileDownload = value;
                case 4 -> FeatureFlags.enableVoiceDownload = value;
            }
        }, null);
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_features), master, rows);

        ToggleRow batchSwitch = createSwitch(context, R.drawable.ic_collections, I18n.t(context, R.string.ig_dialog_downloader_batch), FeatureFlags.enableBatchDownload);
        batchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.enableBatchDownload = isChecked;
            SettingsManager.saveAllFlags();
        });
        addCard(context, p, body, I18n.t(context, R.string.feat_options)).addView(batchSwitch);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_downloader), body);
    }

    private static void showDownloaderSettings(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.DOWNLOADER);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        String folderRaw = FeatureFlags.downloaderCustomPath.isEmpty()
                ? android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/InstaEclipse"
                : FeatureFlags.downloaderCustomPath;
        String folderDisplay = folderRaw.replaceFirst("^.*/0/", "");
        addCard(context, p, body, I18n.t(context, R.string.feat_download_folder)).addView(
                createInfoRow(context, p, I18n.t(context, R.string.ig_dialog_downloader_folder), folderDisplay));

        ToggleRow usernameFolderSwitch = createSwitch(context, R.drawable.ic_create_new_folder, I18n.t(context, R.string.ig_dialog_downloader_username_subfolder), FeatureFlags.downloaderUsernameFolder);
        usernameFolderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.downloaderUsernameFolder = isChecked;
            SettingsManager.saveAllFlags();
        });
        ToggleRow timestampSwitch = createSwitch(context, R.drawable.ic_timer, I18n.t(context, R.string.ig_dialog_downloader_add_timestamp), FeatureFlags.downloaderAddTimestamp);
        timestampSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.downloaderAddTimestamp = isChecked;
            SettingsManager.saveAllFlags();
        });
        addToggleCard(context, p, body, I18n.t(context, R.string.feat_options), null, usernameFolderSwitch, timestampSwitch);

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_downloader_settings), body);
    }

    private static void showBackupRestoreOptions(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.TOOLS);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        LinearLayout card = addCard(context, p, body, null);
        card.addView(createActionRow(context, R.drawable.ic_save, I18n.t(context, R.string.ig_dialog_backup_settings), null,
                FeatureAccents.color(FeatureAccents.SUCCESS), false, () -> {
                    Activity instagramActivity = UIHookManager.getCurrentActivity();
                    if (instagramActivity == null || instagramActivity.isFinishing()) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
                        return;
                    }
                    try {
                        String json = ps.reso.instaeclipse.utils.backup.SettingsBackupManager.toJson();
                        Intent exportIntent = new Intent();
                        exportIntent.setComponent(new ComponentName("ps.reso.instaeclipse",
                                "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                        exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        exportIntent.putExtra("json_content", json);
                        exportIntent.putExtra("file_name", "instaeclipse_settings.json");
                        instagramActivity.startActivity(exportIntent);
                    } catch (Exception e) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_backup_failed, e.getMessage()));
                    }
                }));
        card.addView(createActionRow(context, R.drawable.ic_folder, I18n.t(context, R.string.ig_dialog_restore_settings), null,
                FeatureAccents.color(FeatureAccents.INFO), false, () -> {
                    Activity instagramActivity = UIHookManager.getCurrentActivity();
                    if (instagramActivity == null || instagramActivity.isFinishing()) {
                        showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
                        return;
                    }
                    Intent importIntent = new Intent();
                    importIntent.setComponent(new ComponentName("ps.reso.instaeclipse",
                            "ps.reso.instaeclipse.mods.devops.config.JsonImportActivity"));
                    importIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    importIntent.putExtra("target_package", context.getPackageName());
                    importIntent.putExtra("broadcast_action", "ps.reso.instaeclipse.ACTION_RESTORE_SETTINGS");
                    instagramActivity.startActivity(importIntent);
                }));

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_backup_restore), body);
    }

    private static void showAboutDialog(Context context) {
        sectionAccent = FeatureAccents.color(FeatureAccents.NEUTRAL);
        EclipseUiPalette p = pal(context);
        LinearLayout body = screenBody(context);

        TextView title = new TextView(context);
        title.setText(I18n.t(context, R.string.ig_dialog_title));
        title.setTextColor(p.primaryText);
        title.setTextSize(22f);
        title.setTypeface(displayFont());
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(context, 16), 0, dp(context, 4));
        body.addView(title);

        TextView creator = new TextView(context);
        creator.setText(I18n.t(context, R.string.ig_dialog_about_created_by));
        creator.setTextColor(p.secondaryText);
        creator.setTextSize(14f);
        creator.setGravity(Gravity.CENTER);
        creator.setPadding(0, 0, 0, dp(context, 8));
        body.addView(creator);

        LinearLayout links = addCard(context, p, body, null);
        links.addView(createActionRow(context, R.drawable.ic_github_logo, I18n.t(context, R.string.ig_dialog_about_github), null,
                FeatureAccents.color(FeatureAccents.NEUTRAL), false, () -> openUrl(context, "https://github.com/ReSo7200/InstaEclipse")));
        links.addView(createActionRow(context, R.drawable.ic_telegram_logo, I18n.t(context, R.string.ig_dialog_about_telegram), null,
                FeatureAccents.color(FeatureAccents.TELEGRAM), false, () -> openUrl(context, "https://t.me/InstaEclipse")));

        presentSection(context, I18n.t(context, R.string.ig_dialog_section_about), body);
    }

    private static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            ModuleLog.line("InstaEclipse | ❌ Failed to open " + url + ": " + e.getMessage());
        }
    }

    private static void confirmRestart(Context context) {
        showConfirmDialog(context, R.drawable.ic_ie_restart,
                I18n.t(context, R.string.ig_dialog_section_restart),
                I18n.t(context, R.string.ig_dialog_restart_message),
                I18n.t(context, R.string.ig_dialog_restart_now),
                I18n.t(context, R.string.ig_dialog_cancel),
                false,
                () -> restartApp(context),
                null);
    }

    private static void confirmClearCache(Context context) {
        showConfirmDialog(context,
                I18n.t(context, R.string.ig_dialog_section_clear_cache),
                I18n.t(context, R.string.ig_dialog_clear_cache_message),
                I18n.t(context, R.string.ig_dialog_clear_cache_now),
                I18n.t(context, R.string.ig_dialog_cancel),
                true,
                () -> {
                    ps.reso.instaeclipse.utils.core.DexKitCache.clearCache();
                    restartApp(context);
                },
                null);
    }

    private static void restartApp(Context context) {
        try {
            String packageName = context.getPackageName();
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);

            if (launchIntent != null) {
                clearAppCache(context);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                Runtime.getRuntime().exit(0);
            } else {
                Toast.makeText(context, I18n.t(context, R.string.ig_dialog_restart_not_found), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            String packageName = context.getPackageName();
            ModuleLog.line("InstaEclipse: Restart failed for " + packageName + " - " + e.getMessage());
            Toast.makeText(context, I18n.t(context, R.string.ig_dialog_restart_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static void clearAppCache(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteRecursive(cacheDir);
                ModuleLog.line("InstaEclipse: Cache cleared for " + context.getPackageName());
            } else {
                ModuleLog.line("InstaEclipse: Cache directory not found for " + context.getPackageName());
            }
        } catch (Exception e) {
            ModuleLog.line("InstaEclipse: Failed to clear cache for " + context.getPackageName() + " - " + e.getMessage());
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private static void refreshGhostEmoji() {
        Activity activity = UIHookManager.getCurrentActivity();
        if (activity != null) {
            GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
        }
    }

    private static ToggleRow wireToggleGroup(Context context, ToggleRow[] rows, BiConsumer<Integer, Boolean> setter, Runnable afterChange) {
        ToggleRow master = createMasterSwitch(context, anyEnabled(rows));
        master.setSummary(masterSummary(context, rows));
        master.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < rows.length; i++) {
                rows[i].setCheckedSilently(isChecked);
                setter.accept(i, isChecked);
            }
            master.setSummary(masterSummary(context, rows));
            SettingsManager.saveAllFlags();
            if (afterChange != null) afterChange.run();
        });
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            rows[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                setter.accept(index, isChecked);
                master.setCheckedSilently(anyEnabled(rows));
                master.setSummary(masterSummary(context, rows));
                SettingsManager.saveAllFlags();
                if (afterChange != null) afterChange.run();
            });
        }
        return master;
    }

    private static boolean anyEnabled(ToggleRow[] rows) {
        for (ToggleRow r : rows) {
            if (r.isChecked()) return true;
        }
        return false;
    }

    private static String masterSummary(Context context, ToggleRow[] rows) {
        int on = 0;
        for (ToggleRow r : rows) {
            if (r.isChecked()) on++;
        }
        return on == 0
                ? I18n.t(context, R.string.feat_master_all_off)
                : I18n.t(context, R.string.feat_master_summary, on, rows.length);
    }

    private static void presentSection(Context context, String title, LinearLayout body) {
        presentScreen(context, buildScreen(context, pal(context), title, false, body, null, 0.72f));
    }

    private static View buildScreen(Context context, EclipseUiPalette p, String title, boolean root,
                                    View body, View footer, float maxHeightFraction) {
        LinearLayout screen = new LinearLayout(context);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(createDragHandle(context, p));
        top.addView(root ? createRootHeader(context, p, title) : createSectionHeaderBar(context, p, title));
        attachDragToDismiss(context, top);
        screen.addView(top);

        if (!root) {
            View divider = new View(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1)));
            lp.setMarginStart(dp(context, 16));
            lp.setMarginEnd(dp(context, 16));
            divider.setLayoutParams(lp);
            divider.setBackgroundColor(p.divider);
            screen.addView(divider);
        }

        screen.addView(createScrollableContainer(context, body, maxHeightFraction));
        if (footer != null) screen.addView(footer);
        return screen;
    }

    private static View createRootHeader(Context context, EclipseUiPalette p, String title) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(context, 20), dp(context, 6), dp(context, 12), dp(context, 12));

        ImageView ring = new ImageView(context);
        ring.setImageDrawable(new EclipseRingDrawable(p.sheetBg, p.accent, dp(context, 2), dp(context, 8), true)
                .setLogo(loadModuleIcon(R.drawable.ic_logo, p.accent)));
        ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(dp(context, 40), dp(context, 40));
        ringLp.setMarginStart(-dp(context, 8));
        ringLp.setMarginEnd(dp(context, 4));
        ring.setLayoutParams(ringLp);
        header.addView(ring);

        TextView titleView = new TextView(context);
        titleView.setText(stripEdgeEmoji(title));
        titleView.setTextColor(p.primaryText);
        titleView.setTextSize(19);
        titleView.setTypeface(displayFont());
        titleView.setTextDirection(View.TEXT_DIRECTION_LTR);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        titleView.setAccessibilityHeading(true);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(titleView);

        header.addView(createCloseButton(context, p));
        return header;
    }

    private static View createSectionHeaderBar(Context context, EclipseUiPalette p, String title) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPaddingRelative(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 10));

        TextView titleView = new TextView(context);
        titleView.setText(stripEdgeEmoji(title));
        titleView.setTextColor(p.primaryText);
        titleView.setTextSize(18);
        titleView.setTypeface(displayFont());
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setPaddingRelative(dp(context, 4), 0, dp(context, 8), 0);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleView.setAccessibilityHeading(true);

        TextView note = new TextView(context);
        note.setText(I18n.t(context, R.string.sheet_saved_instantly));
        note.setTextColor(p.sectionLabel);
        note.setTextSize(12);
        note.setTypeface(font(400));
        note.setPaddingRelative(0, 0, dp(context, 8), 0);

        header.addView(createBackButton(context, p));
        header.addView(titleView);
        header.addView(note);
        return header;
    }

    private static View createBackButton(Context context, EclipseUiPalette p) {
        Drawable icon = loadModuleIcon(R.drawable.ic_ie_arrow_back, p.primaryText);
        View button;
        if (icon != null) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = dp(context, 13);
            image.setPadding(pad, pad, pad, pad);
            button = image;
        } else {
            TextView text = new TextView(context);
            text.setText("‹");
            text.setTextSize(30);
            text.setTextColor(p.primaryText);
            text.setGravity(Gravity.CENTER);
            text.setIncludeFontPadding(false);
            button = text;
        }
        int size = dp(context, 48);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(p.pressed), null, mask));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(I18n.t(context, R.string.back));
        button.setOnClickListener(v -> navigateBack());
        setButtonRole(button);
        return button;
    }

    private static View createCloseButton(Context context, EclipseUiPalette p) {
        Drawable icon = loadModuleIcon(R.drawable.ic_ie_close, p.secondaryText);
        View button;
        if (icon != null) {
            ImageView image = new ImageView(context);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = dp(context, 12);
            image.setPadding(pad, pad, pad, pad);
            button = image;
        } else {
            TextView text = new TextView(context);
            text.setText("×");
            text.setTextSize(22);
            text.setTextColor(p.secondaryText);
            text.setGravity(Gravity.CENTER);
            button = text;
        }
        int size = dp(context, 44);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(p.chipBg);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(p.border), bg, null));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(I18n.t(context, R.string.ig_dialog_close));
        button.setOnClickListener(v -> dismissSheet());
        setButtonRole(button);
        return button;
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void attachDragToDismiss(Context context, View dragArea) {
        final float[] downY = {0f};
        final boolean[] dragging = {false};
        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        dragArea.setOnTouchListener((v, event) -> {
            View sheet = sheetHost;
            if (sheet == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY[0] = event.getRawY();
                    dragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = event.getRawY() - downY[0];
                    if (!dragging[0] && dy > slop) dragging[0] = true;
                    if (dragging[0]) sheet.setTranslationY(Math.max(0f, dy));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!dragging[0]) return true;
                    dragging[0] = false;
                    boolean release = event.getActionMasked() == MotionEvent.ACTION_UP
                            && sheet.getTranslationY() > sheet.getHeight() * 0.2f;
                    if (release) {
                        sheet.animate().translationY(sheet.getHeight()).setDuration(160)
                                .withEndAction(DialogUtils::dismissSheet).start();
                    } else {
                        sheet.animate().translationY(0f).setDuration(160).start();
                    }
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    private static LinearLayout screenBody(Context context) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 22));
        return body;
    }

    private static LinearLayout addCard(Context context, EclipseUiPalette p, LinearLayout body, String header) {
        if (header != null) {
            body.addView(sectionHeader(context, p, header));
        } else {
            addSpacer(context, body, 14);
        }
        LinearLayout card = createGroupCard(context, p);
        body.addView(card);
        return card;
    }

    private static void addSpacer(Context context, LinearLayout body, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        body.addView(spacer);
    }

    private static void addToggleCard(Context context, EclipseUiPalette p, LinearLayout body, String header,
                                      ToggleRow master, View... rows) {
        if (master != null) {
            addSpacer(context, body, 12);
            body.addView(master);
        }
        LinearLayout card = addCard(context, p, body, header);
        for (View row : rows) card.addView(row);
    }

    private static TextView sectionHeader(Context context, EclipseUiPalette p, String text) {
        TextView header = new TextView(context);
        header.setText(TRAILING_COLON.matcher(stripEdgeEmoji(text)).replaceFirst(""));
        header.setTextColor(p.sectionLabel);
        header.setTextSize(13);
        header.setTypeface(font(600));
        header.setPaddingRelative(dp(context, 4), dp(context, 18), dp(context, 4), dp(context, 10));
        header.setAccessibilityHeading(true);
        return header;
    }

    private static LinearLayout createGroupCard(Context context, EclipseUiPalette p) {
        return new SegmentGroup(context, p);
    }

    private static final class SegmentGroup extends LinearLayout {
        private final EclipseUiPalette palette;

        SegmentGroup(Context context, EclipseUiPalette palette) {
            super(context);
            this.palette = palette;
            setOrientation(VERTICAL);
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            restyle();
        }

        @Override
        public void onViewRemoved(View child) {
            super.onViewRemoved(child);
            restyle();
        }

        private void restyle() {
            Context context = getContext();
            float big = dp(context, 18);
            float small = dp(context, 4);
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                float top = i == 0 ? big : small;
                float bottom = i == count - 1 ? big : small;
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(palette.groupBg);
                bg.setCornerRadii(new float[]{top, top, top, top, bottom, bottom, bottom, bottom});
                child.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.pressed), bg, null));
                if (child.getLayoutParams() instanceof MarginLayoutParams lp) {
                    int margin = i == 0 ? 0 : dp(context, 2);
                    if (lp.topMargin != margin) {
                        lp.topMargin = margin;
                        child.setLayoutParams(lp);
                    }
                }
            }
        }
    }

    private static LinearLayout createBaseRow(Context context, EclipseUiPalette p) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        row.setMinimumHeight(dp(context, 58));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(createRowRipple(context, p));
        return row;
    }

    private static Drawable createRowRipple(Context context, EclipseUiPalette p) {
        return new RippleDrawable(ColorStateList.valueOf(p.pressed), null, roundedColor(Color.WHITE, dp(context, 18)));
    }

    private static LinearLayout createTextColumn(Context context, EclipseUiPalette p, String label, String subtitle) {
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPaddingRelative(0, dp(context, 2), dp(context, 8), dp(context, 2));
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView labelView = new TextView(context);
        labelView.setText(stripEdgeEmoji(label));
        labelView.setTextColor(p.primaryText);
        labelView.setTextSize(14.5f);
        labelView.setTypeface(font(400));
        texts.addView(labelView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(p.sectionLabel);
            subtitleView.setTextSize(12.5f);
            subtitleView.setTypeface(font(400));
            subtitleView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(subtitleView);
        }
        return texts;
    }

    private static View createChevron(Context context, EclipseUiPalette p) {
        Drawable icon = loadModuleIcon(R.drawable.ic_ie_chevron, p.sectionLabel);
        if (icon != null) {
            ImageView chevron = new ImageView(context);
            chevron.setImageDrawable(icon);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            chevron.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 17), dp(context, 17)));
            return chevron;
        }
        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextSize(22);
        chevron.setTextColor(p.secondaryText);
        chevron.setIncludeFontPadding(false);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return chevron;
    }

    private static View createMenuRow(Context context, EclipseUiPalette p, int iconRes, String label, String value,
                                      int accent, Runnable onClick) {
        LinearLayout row = createBaseRow(context, p);
        row.addView(buildIconChip(context, iconRes, accent, p.primaryText));
        row.addView(createTextColumn(context, p, label, null));
        if (value != null) {
            TextView valueView = new TextView(context);
            valueView.setText(value);
            valueView.setTextSize(13);
            valueView.setTextColor(p.sectionLabel);
            valueView.setTypeface(font(400));
            valueView.setMaxLines(1);
            valueView.setPaddingRelative(0, 0, dp(context, 8), 0);
            row.addView(valueView);
        }
        row.addView(createChevron(context, p));
        row.setOnClickListener(v -> onClick.run());
        setButtonRole(row);
        return row;
    }

    private static View createActionRow(Context context, int iconRes, String label, String subtitle, int accent,
                                        boolean opensScreen, Runnable onClick) {
        EclipseUiPalette p = pal(context);
        LinearLayout row = createBaseRow(context, p);
        row.addView(buildIconChip(context, iconRes, accent, p.primaryText));
        row.addView(createTextColumn(context, p, label, subtitle));
        if (opensScreen) row.addView(createChevron(context, p));
        row.setOnClickListener(v -> onClick.run());
        setButtonRole(row);
        return row;
    }

    private static View createInfoRow(Context context, EclipseUiPalette p, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        row.setMinimumHeight(dp(context, 56));
        row.setFocusable(true);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(14.5f);
        labelView.setTextColor(p.primaryText);
        labelView.setTypeface(font(400));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(p.sectionLabel);
        valueView.setTypeface(font(400));
        valueView.setMaxLines(1);
        valueView.setEllipsize(TextUtils.TruncateAt.START);
        valueView.setTextDirection(View.TEXT_DIRECTION_LTR);
        valueView.setGravity(Gravity.END);
        valueView.setPaddingRelative(dp(context, 16), 0, 0, 0);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(labelView);
        row.addView(valueView);
        return row;
    }

    private static ImageView buildIconChip(Context context, int iconRes, int accent, int neutralTint) {
        EclipseUiPalette p = pal(context);
        boolean colorful = FeatureAccents.isColorful();
        ImageView iconView = new ImageView(context);
        Drawable icon = loadModuleIcon(iconRes, colorful ? accent : neutralTint);
        if (icon != null) iconView.setImageDrawable(icon);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPad = dp(context, 8);
        iconView.setPadding(iconPad, iconPad, iconPad, iconPad);
        iconView.setBackground(roundedColor(colorful ? EclipseUiPalette.withAlpha(accent, 0x33) : p.chipBg, dp(context, 11)));
        iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34));
        iconLp.setMarginEnd(dp(context, 12));
        iconView.setLayoutParams(iconLp);
        return iconView;
    }

    private static void tintIconChip(ImageView iconView, int iconRes, int tint) {
        Drawable icon = loadModuleIcon(iconRes, tint);
        if (icon != null) iconView.setImageDrawable(icon);
    }

    private static android.content.res.Resources moduleResources() {
        android.content.res.Resources res = moduleRes;
        if (res == null) {
            res = android.content.res.XModuleResources.createInstance(Module.moduleSourceDir, null);
            moduleRes = res;
        }
        return res;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private static Drawable loadModuleIcon(int iconRes, int tintColor) {
        try {
            Drawable icon = moduleResources().getDrawable(iconRes, null);
            icon = icon.mutate();
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN));
            return icon;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Typeface font(int weight) {
        try {
            if (sansFace == null) sansFace = moduleResources().getFont(R.font.ie_sans);
            return Typeface.create(sansFace, weight, false);
        } catch (Throwable ignored) {
            return Typeface.create(Typeface.DEFAULT, weight, false);
        }
    }

    private static Typeface displayFont() {
        try {
            if (displayFace == null) displayFace = moduleResources().getFont(R.font.ie_display);
            return displayFace;
        } catch (Throwable ignored) {
            return font(600);
        }
    }

    private static void setButtonRole(View view) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }

    private static void setNodeChecked(AccessibilityNodeInfo info, boolean checked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            info.setChecked(checked ? AccessibilityNodeInfo.CHECKED_STATE_TRUE : AccessibilityNodeInfo.CHECKED_STATE_FALSE);
        } else {
            setNodeCheckedLegacy(info, checked);
        }
    }

    @SuppressWarnings("deprecation")
    private static void setNodeCheckedLegacy(AccessibilityNodeInfo info, boolean checked) {
        info.setChecked(checked);
    }

    private static GradientDrawable roundedColor(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private static String stripEdgeEmoji(String text) {
        if (text == null) return "";
        String stripped = LEADING_EMOJI.matcher(text).replaceFirst("");
        stripped = TRAILING_EMOJI.matcher(stripped).replaceFirst("");
        return stripped.trim();
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static ToggleRow createSwitch(Context context, int iconRes, String label, boolean defaultState) {
        return new ToggleRow(context, iconRes, label, defaultState, accentColor());
    }

    private static ToggleRow createSwitch(Context context, int iconRes, String label, boolean defaultState, int accent) {
        return new ToggleRow(context, iconRes, label, defaultState, accent);
    }

    private static ToggleRow createMasterSwitch(Context context, boolean checked) {
        ToggleRow master = new ToggleRow(context, sectionIcon, I18n.t(context, R.string.ig_dialog_enable_disable_all), checked, accentColor());
        master.asMasterCard();
        return master;
    }

    private static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final ArgbEvaluator evaluator = new ArgbEvaluator();
        private final int onColor;
        private boolean checked;
        private float progress;
        private ValueAnimator animator;

        SwitchView(Context context, int onColor, boolean checked) {
            super(context);
            this.onColor = onColor;
            this.checked = checked;
            this.progress = checked ? 1f : 0f;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 52), dp(context, 32));
            lp.setMarginStart(dp(context, 12));
            setLayoutParams(lp);
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value, boolean animate) {
            if (checked == value) return;
            checked = value;
            if (animator != null) animator.cancel();
            float target = value ? 1f : 0f;
            if (!animate || !isAttachedToWindow()) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(160);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            float stroke = dp(getContext(), 2);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor((int) evaluator.evaluate(progress, EclipseUiPalette.SWITCH_OFF_TRACK, onColor));
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, paint);

            if (progress < 1f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setColor(EclipseUiPalette.withAlpha(EclipseUiPalette.SWITCH_OFF_BORDER, Math.round(255 * (1f - progress))));
                rect.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f);
                canvas.drawRoundRect(rect, r - stroke / 2f, r - stroke / 2f, paint);
            }

            float thumb = dp(getContext(), 8) + (dp(getContext(), 12) - dp(getContext(), 8)) * progress;
            float cx = r + (w - 2 * r) * progress;
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) cx = w - cx;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((int) evaluator.evaluate(progress, EclipseUiPalette.SWITCH_OFF_THUMB, EclipseUiPalette.ON_ACCENT));
            canvas.drawCircle(cx, h / 2f, thumb, paint);
        }
    }

    private static class ToggleRow extends LinearLayout {
        private static final Pattern TRAILING_PAREN = Pattern.compile("\\s*[(（][^()（）]*[)）]\\s*$");

        private final EclipseUiPalette p;
        private final SwitchView toggle;
        private final ImageView iconView;
        private final int iconRes;
        private final int accent;
        private final TextView labelView;
        private final TextView badgeView;
        private final TextView subtitleView;
        private final ImageView lockView;
        private CompoundButton.OnCheckedChangeListener listener;
        private boolean master;
        private boolean danger;
        private boolean locked;

        ToggleRow(Context context, int iconRes, String label, boolean checked, int accent) {
            super(context);
            p = pal(context);
            this.iconRes = iconRes;
            this.accent = accent;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
            setMinimumHeight(dp(context, 58));
            setClickable(true);
            setFocusable(true);
            setBackground(createRowRipple(context, p));

            iconView = iconRes != 0 ? buildIconChip(context, iconRes, accent, p.secondaryText) : null;
            if (iconView != null) addView(iconView);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            texts.setGravity(Gravity.START);
            texts.setPaddingRelative(0, dp(context, 2), dp(context, 8), dp(context, 2));
            texts.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            labelView = new TextView(context);
            labelView.setText(stripEdgeEmoji(label));
            labelView.setTextColor(p.primaryText);
            labelView.setTextSize(14);
            labelView.setTypeface(font(400));
            labelView.setLineSpacing(0f, 1.1f);
            texts.addView(labelView);

            badgeView = new TextView(context);
            badgeView.setText(I18n.t(context, R.string.feat_unstable));
            badgeView.setTextColor(EclipseUiPalette.UNSTABLE_TEXT);
            badgeView.setTextSize(11);
            badgeView.setTypeface(font(600));
            badgeView.setBackground(roundedColor(EclipseUiPalette.UNSTABLE_BG, dp(context, 10)));
            badgeView.setPaddingRelative(dp(context, 8), dp(context, 1), dp(context, 8), dp(context, 2));
            LayoutParams badgeLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            badgeLp.topMargin = dp(context, 3);
            badgeView.setLayoutParams(badgeLp);
            badgeView.setVisibility(GONE);
            texts.addView(badgeView);

            subtitleView = new TextView(context);
            subtitleView.setTextColor(p.sectionLabel);
            subtitleView.setTextSize(12);
            subtitleView.setTypeface(font(400));
            subtitleView.setPadding(0, dp(context, 2), 0, 0);
            subtitleView.setVisibility(GONE);
            texts.addView(subtitleView);

            lockView = new ImageView(context);
            Drawable lock = loadModuleIcon(R.drawable.ic_ie_lock, p.accent);
            if (lock != null) lockView.setImageDrawable(lock);
            lockView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lockView.setPadding(0, dp(context, 7), 0, dp(context, 7));
            lockView.setBackground(roundedColor(EclipseUiPalette.withAlpha(p.accent, 0x29), dp(context, 15)));
            LayoutParams lockLp = new LayoutParams(dp(context, 48), dp(context, 30));
            lockLp.setMarginStart(dp(context, 12));
            lockView.setLayoutParams(lockLp);
            lockView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            lockView.setVisibility(GONE);

            toggle = new SwitchView(context, p.accent, checked);

            addView(texts);
            addView(lockView);
            addView(toggle);
            setOnClickListener(v -> {
                if (!isEnabled() || locked) return;
                boolean next = !toggle.isChecked();
                toggle.setChecked(next, true);
                refreshVisuals();
                if (listener != null) listener.onCheckedChanged(null, next);
            });
            refreshVisuals();
        }

        boolean isChecked() {
            return toggle.isChecked();
        }

        void setCheckedSilently(boolean checked) {
            toggle.setChecked(checked, true);
            refreshVisuals();
        }

        void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
            listener = l;
        }

        void setSubtitle(String text) {
            subtitleView.setText(text);
            subtitleView.setVisibility(text == null ? GONE : VISIBLE);
        }

        void markUnstable() {
            labelView.setText(TRAILING_PAREN.matcher(labelView.getText()).replaceFirst(""));
            badgeView.setVisibility(VISIBLE);
        }

        void setIndentStart(int indentDp) {
            setPaddingRelative(dp(getContext(), indentDp), getPaddingTop(), getPaddingEnd(), getPaddingBottom());
        }

        void setLocked(boolean value, String hint) {
            locked = value;
            lockView.setVisibility(value ? VISIBLE : GONE);
            toggle.setVisibility(value ? GONE : VISIBLE);
            if (!danger) setSubtitle(value ? hint : null);
            if (iconView != null) iconView.setAlpha(value && !danger ? 0.55f : 1f);
        }

        void asMasterCard() {
            master = true;
            setMinimumHeight(dp(getContext(), 72));
            setPaddingRelative(dp(getContext(), 16), dp(getContext(), 12), dp(getContext(), 16), dp(getContext(), 12));
            labelView.setTextSize(15.5f);
            labelView.setTypeface(font(600));
            if (iconView != null) {
                LayoutParams lp = (LayoutParams) iconView.getLayoutParams();
                lp.width = dp(getContext(), 44);
                lp.height = dp(getContext(), 44);
                lp.setMarginEnd(dp(getContext(), 14));
                iconView.setLayoutParams(lp);
                int pad = dp(getContext(), 11);
                iconView.setPadding(pad, pad, pad, pad);
            }
            refreshVisuals();
        }

        void asDangerCard() {
            danger = true;
            setMinimumHeight(dp(getContext(), 76));
            setPaddingRelative(dp(getContext(), 14), dp(getContext(), 12), dp(getContext(), 14), dp(getContext(), 12));
            labelView.setTextSize(15);
            labelView.setTypeface(font(600));
            labelView.setTextColor(EclipseUiPalette.DANGER_TEXT);
            subtitleView.setTextColor(p.secondaryText);
            if (iconView != null) {
                tintIconChip(iconView, iconRes, p.danger);
                iconView.setBackground(roundedColor(EclipseUiPalette.withAlpha(p.danger, 0x29), dp(getContext(), 13)));
            }
            Drawable lock = loadModuleIcon(R.drawable.ic_ie_lock, p.danger);
            if (lock != null) lockView.setImageDrawable(lock);
            lockView.setBackground(null);
            GradientDrawable bg = roundedColor(EclipseUiPalette.withAlpha(p.danger, 0x12), dp(getContext(), 20));
            bg.setStroke(dp(getContext(), 1), EclipseUiPalette.withAlpha(p.danger, 0x59));
            setBackground(new RippleDrawable(ColorStateList.valueOf(p.pressed), bg, null));
        }

        void setSummary(String text) {
            setSubtitle(text);
        }

        private void refreshVisuals() {
            boolean on = toggle.isChecked();
            if (iconView != null && !danger && !FeatureAccents.isColorful()) {
                tintIconChip(iconView, iconRes, on ? p.accent : p.secondaryText);
            }
            if (master) {
                GradientDrawable bg = roundedColor(on ? EclipseUiPalette.withAlpha(p.accent, 0x14) : p.groupBg, dp(getContext(), 22));
                bg.setStroke(dp(getContext(), 1), on ? EclipseUiPalette.withAlpha(p.accent, 0x59) : p.border);
                setBackground(new RippleDrawable(ColorStateList.valueOf(p.pressed), bg, null));
                if (iconView != null && !FeatureAccents.isColorful()) {
                    iconView.setBackground(roundedColor(on ? EclipseUiPalette.withAlpha(p.accent, 0x29) : p.chipBg, dp(getContext(), 14)));
                }
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            float alpha = enabled ? 1f : 0.45f;
            if (iconView != null && !locked) iconView.setAlpha(alpha);
            labelView.setAlpha(alpha);
            toggle.setAlpha(enabled ? 1f : 0.5f);
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return Switch.class.getName();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setCheckable(true);
            setNodeChecked(info, toggle.isChecked());
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            event.setChecked(toggle.isChecked());
        }
    }

    private static class RadioRow extends LinearLayout {
        private final View checkmark;
        private boolean checked;

        RadioRow(Context context, String label, boolean checked) {
            super(context);
            EclipseUiPalette p = pal(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPaddingRelative(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
            setMinimumHeight(dp(context, 56));
            setClickable(true);
            setFocusable(true);
            setBackground(createRowRipple(context, p));

            TextView labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(p.primaryText);
            labelView.setTextSize(14.5f);
            labelView.setTypeface(font(400));
            labelView.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            Drawable icon = loadModuleIcon(R.drawable.ic_ie_check, p.accent);
            if (icon != null) {
                ImageView image = new ImageView(context);
                image.setImageDrawable(icon);
                image.setLayoutParams(new LayoutParams(dp(context, 20), dp(context, 20)));
                checkmark = image;
            } else {
                TextView text = new TextView(context);
                text.setText("✓");
                text.setTextColor(p.accent);
                text.setTextSize(18);
                checkmark = text;
            }
            checkmark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            addView(labelView);
            addView(checkmark);
            setChecked(checked);
        }

        void setChecked(boolean checked) {
            this.checked = checked;
            checkmark.setVisibility(checked ? VISIBLE : INVISIBLE);
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return RadioButton.class.getName();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setCheckable(true);
            setNodeChecked(info, checked);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            event.setChecked(checked);
        }
    }

    private static View createDragHandle(Context context, EclipseUiPalette p) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setPadding(0, dp(context, 10), 0, dp(context, 6));
        wrapper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        View handle = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        handle.setLayoutParams(lp);
        handle.setBackground(roundedColor(p.handle, dp(context, 2)));

        wrapper.addView(handle);
        return wrapper;
    }

    private static class MaxHeightScrollView extends ScrollView {
        private final int maxHeightPx;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedSpec);
        }
    }

    private static ScrollView createScrollableContainer(Context context, View content, float heightFraction) {
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        MaxHeightScrollView scrollView = new MaxHeightScrollView(context, Math.round(screenHeight * heightFraction));
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scrollView.addView(content);
        return scrollView;
    }

    private static AlertDialog createBottomSheetDialog(Context context, View contentView) {
        IgColorRemapEngine.markModuleTree(contentView);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(contentView).setCancelable(true).create();
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screenWidth, dp(context, 640));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
            IgColorRemapEngine.markModuleTree(window.getDecorView());
        }
        return dialog;
    }
}
