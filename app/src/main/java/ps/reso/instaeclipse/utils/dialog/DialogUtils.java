package ps.reso.instaeclipse.utils.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import de.robv.android.xposed.XposedBridge;
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

public class DialogUtils {

    private static AlertDialog currentDialog;

    @SuppressLint("UseCompatLoadingForDrawables")
    public static void showEclipseOptionsDialog(Context context) {
        SettingsManager.init(context);
        SettingsManager.syncFromCompanion(context, false);
        SettingsManager.loadAllFlags(context);
        IgColorRemapEngine.enterModuleUi();
        try {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#1C1C1E"));
        background.setCornerRadii(new float[]{40, 40, 40, 40, 0, 0, 0, 0});
        outer.setBackground(background);

        outer.addView(createDragHandle(context));
        TextView title = new TextView(context);
        title.setText(I18n.t(context, R.string.ig_dialog_title));
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(40, 8, 40, 20);
        outer.addView(title);
        outer.addView(createDivider(context));

        LinearLayout mainLayout = buildMainMenuLayout(context);
        ScrollView scrollView = createScrollableContainer(context, mainLayout, 0.62f);
        outer.addView(scrollView);

        TextView closeButton = new TextView(context);
        closeButton.setText(I18n.t(context, R.string.ig_dialog_close));
        closeButton.setTextColor(Color.parseColor("#FF453A"));
        closeButton.setTextSize(16);
        closeButton.setPadding(40, 20, 40, 40);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setTypeface(null, Typeface.BOLD);
        StateListDrawable closeStates = new StateListDrawable();
        closeStates.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#20FF453A")));
        closeStates.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        closeButton.setBackground(closeStates);
        closeButton.setOnClickListener(v -> {
            if (currentDialog != null) { try { currentDialog.dismiss(); } catch (Exception ignored) {} currentDialog = null; }
        });
        outer.addView(closeButton);

        SettingsManager.saveAllFlags();

        Activity activity = UIHookManager.getCurrentActivity();
        if (activity != null) {
            GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
        }

        if (currentDialog != null && currentDialog.isShowing()) {
            try { currentDialog.dismiss(); } catch (Exception ignored) {}
        }
        currentDialog = null;

        IgColorRemapEngine.markModuleDialogView(outer);
        currentDialog = createBottomSheetDialog(context, outer);
        currentDialog.show();
        } finally {
            IgColorRemapEngine.leaveModuleUi();
        }
    }

    public static void showSimpleDialog(Context context, String title, String message) {
        try {
            IgColorRemapEngine.enterModuleUi();
            try {
                AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                        .setPositiveButton(I18n.t(context, R.string.ig_dialog_ok), null).create();
                markDialogDecor(dialog);
                dialog.show();
            } finally {
                IgColorRemapEngine.leaveModuleUi();
            }
        } catch (Exception e) {
        }
    }

    @SuppressLint("SetTextI18n")
    private static LinearLayout buildMainMenuLayout(Context context) {
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 0, 24, 0);

        mainLayout.addView(sectionHeader(context, I18n.t(context, R.string.feat_categories)));
        LinearLayout featuresGroup = createGroupCard(context);
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_tune, I18n.t(context, R.string.ig_dialog_menu_dev_options), "#0A84FF", () -> showDevOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_eye, I18n.t(context, R.string.ig_dialog_menu_ghost_settings), "#5E5CE6", () -> showGhostOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_shield, I18n.t(context, R.string.ig_dialog_menu_ad_analytics), "#FF453A", () -> showAdOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_block, I18n.t(context, R.string.ig_dialog_menu_distraction_free), "#30D158", () -> showDistractionOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_sparkle, I18n.t(context, R.string.ig_dialog_menu_clean_feed), "#64D2FF", () -> showCleanFeedOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_settings_gear, I18n.t(context, R.string.ig_dialog_menu_misc), "#BF5AF2", () -> showMiscOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_download, I18n.t(context, R.string.ig_dialog_menu_downloader), "#FF9F0A", () -> showDownloaderOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_pin, I18n.t(context, R.string.ig_dialog_menu_location), "#FFD60A", () -> showLocationOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_movie, I18n.t(context, R.string.ig_dialog_menu_quality), "#32D74B", () -> showQualityOptions(context)));
        featuresGroup.addView(createMenuRow(context, R.drawable.ic_palette, I18n.t(context, R.string.theme_customize), "#FF2D55", () -> openThemeCustomizer(context)));
        mainLayout.addView(featuresGroup);

        mainLayout.addView(sectionHeader(context, I18n.t(context, R.string.feat_tools)));
        LinearLayout toolsGroup = createGroupCard(context);
        toolsGroup.addView(createMenuRow(context, R.drawable.ic_save, I18n.t(context, R.string.ig_dialog_menu_backup_restore), "#64D2FF", () -> showBackupRestoreOptions(context)));
        toolsGroup.addView(createMenuRow(context, R.drawable.ic_info, I18n.t(context, R.string.ig_dialog_menu_about), "#8E8E93", () -> showAboutDialog(context)));
        toolsGroup.addView(createMenuRow(context, R.drawable.ic_restart, I18n.t(context, R.string.ig_dialog_menu_restart), "#FF9500", () -> showRestartSection(context)));
        toolsGroup.addView(createMenuRow(context, R.drawable.ic_delete, I18n.t(context, R.string.ig_dialog_clear_cache), "#FF453A", () -> showClearCacheSection(context)));
        mainLayout.addView(toolsGroup);

        TextView footer = new TextView(context);
        footer.setText("@reso7200");
        footer.setTextColor(Color.parseColor("#8E8E93"));
        footer.setTextSize(13);
        footer.setPadding(16, 24, 16, 8);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        mainLayout.addView(footer);

        return mainLayout;
    }

    private static TextView sectionHeader(Context context, String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextColor(Color.parseColor("#8E8E93"));
        header.setTextSize(13);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(16, 20, 16, 8);
        return header;
    }

    private static LinearLayout createGroupCard(Context context) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable groupBg = new GradientDrawable();
        groupBg.setColor(Color.parseColor("#2C2C2E"));
        groupBg.setCornerRadius(20);
        group.setBackground(groupBg);
        group.setPadding(6, 6, 6, 6);
        return group;
    }

    private static View createMenuRow(Context context, int iconRes, String labelWithEmoji, String accentHex, Runnable onClick) {
        String label = stripEdgeEmoji(labelWithEmoji);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 12, 14, 12);
        row.setClickable(true);
        row.setFocusable(true);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, roundedColor(Color.parseColor("#3A3A3C"), 16));
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackground(bg);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(Color.WHITE);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextSize(20);
        chevron.setTextColor(Color.parseColor("#8E8E93"));

        row.addView(buildIconChip(context, iconRes, accentHex));
        row.addView(labelView);
        row.addView(chevron);
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private static GradientDrawable roundedColor(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        return d;
    }

    private static final java.util.regex.Pattern LEADING_EMOJI =
            java.util.regex.Pattern.compile("^[\\p{So}\\p{Sk}\\u200D\\uFE0F]+\\s*");
    private static final java.util.regex.Pattern TRAILING_EMOJI =
            java.util.regex.Pattern.compile("\\s*[\\p{So}\\p{Sk}\\u200D\\uFE0F]+$");

    private static String stripEdgeEmoji(String text) {
        String stripped = LEADING_EMOJI.matcher(text).replaceFirst("");
        stripped = TRAILING_EMOJI.matcher(stripped).replaceFirst("");
        return stripped.trim();
    }

    private static void showGhostQuickToggleOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow[] toggleSwitches = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_seen),           FeatureFlags.quickToggleSeen),
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_voice_seen),     FeatureFlags.quickToggleVoiceSeen),
                createSwitch(context, R.drawable.ic_chat, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_typing),         FeatureFlags.quickToggleTyping),
                createSwitch(context, R.drawable.ic_camera, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_disable_screenshot),  FeatureFlags.quickToggleScreenshot),
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_view_once),      FeatureFlags.quickToggleViewOnce),
                createSwitch(context, R.drawable.ic_story_ring, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_story_seen),     FeatureFlags.quickToggleStory),
                createSwitch(context, R.drawable.ic_live, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_hide_live_seen),      FeatureFlags.quickToggleLive),
                createSwitch(context, R.drawable.ic_timer, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_keep_ephemeral),      FeatureFlags.quickToggleEphemeral),
                createSwitch(context, R.drawable.ic_restart, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_unlimited_replays),   FeatureFlags.quickToggleReplays),
                createSwitch(context, R.drawable.ic_eye, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_permanent_view),      FeatureFlags.quickTogglePermanentView),
                createSwitch(context, R.drawable.ic_camera, "#5E5CE6", I18n.t(context, R.string.ig_dialog_quick_allow_screenshots),   FeatureFlags.quickToggleAllowScreenshots)};

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(toggleSwitches));

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s :toggleSwitches) {
                s.setChecked(isChecked);
            }
        });

        for (int i = 0; i < toggleSwitches.length; i++) {
            final int index = i;
            toggleSwitches[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                enableAllSwitch.setOnCheckedChangeListener(null);
                enableAllSwitch.setChecked(areAllEnabled(toggleSwitches));
                enableAllSwitch.setOnCheckedChangeListener((buttonView2, isChecked2) -> {
                    for (ToggleRow s2 :toggleSwitches) {
                        s2.setChecked(isChecked2);
                    }
                });

                switch (index) {
                    case 0:
                        FeatureFlags.quickToggleSeen = isChecked;
                        break;
                    case 1:
                        FeatureFlags.quickToggleVoiceSeen = isChecked;
                        break;
                    case 2:
                        FeatureFlags.quickToggleTyping = isChecked;
                        break;
                    case 3:
                        FeatureFlags.quickToggleScreenshot = isChecked;
                        break;
                    case 4:
                        FeatureFlags.quickToggleViewOnce = isChecked;
                        break;
                    case 5:
                        FeatureFlags.quickToggleStory = isChecked;
                        break;
                    case 6:
                        FeatureFlags.quickToggleLive = isChecked;
                        break;
                    case 7:
                        FeatureFlags.quickToggleEphemeral = isChecked;
                        break;
                    case 8:
                        FeatureFlags.quickToggleReplays = isChecked;
                        break;
                    case 9:
                        FeatureFlags.quickTogglePermanentView = isChecked;
                        break;
                    case 10:
                        FeatureFlags.quickToggleAllowScreenshots = isChecked;
                        break;
                }

                SettingsManager.saveAllFlags();

                Activity activity = UIHookManager.getCurrentActivity();
                if (activity != null) {
                    GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
                }
            });
        }

        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :toggleSwitches) {
            layout.addView(s);
        }

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_quick_toggle), layout, () -> {
        });

    }

    private static View createDivider(Context context) {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        params.setMargins(0, 20, 0, 20);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(Color.DKGRAY);
        return divider;
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

    @SuppressLint("SetTextI18n")
    private static void showDevOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow devModeSwitch = createSwitch(context, R.drawable.ic_tune, "#0A84FF", I18n.t(context, R.string.ig_dialog_dev_enable), FeatureFlags.isDevEnabled);
        devModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.isDevEnabled = isChecked;
            SettingsManager.saveAllFlags();
        });

        layout.addView(devModeSwitch);
        layout.addView(createDivider(context));

        layout.addView(createActionRow(context, R.drawable.ic_download, I18n.t(context, R.string.ig_dialog_dev_import), "#30D158", v -> {
            Activity instagramActivity = UIHookManager.getCurrentActivity();
            if (instagramActivity != null && !instagramActivity.isFinishing()) {
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
            } else {
                showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
            }
        }));

        layout.addView(createActionRow(context, R.drawable.ic_upload, I18n.t(context, R.string.ig_dialog_dev_export), "#0A84FF", v -> {
            Activity instagramActivity = UIHookManager.getCurrentActivity();
            if (instagramActivity != null && !instagramActivity.isFinishing()) {
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
                    String json = sb.toString().trim();
                    Intent exportIntent = new Intent();
                    exportIntent.setComponent(new ComponentName("ps.reso.instaeclipse", "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                    exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    exportIntent.putExtra("json_content", json);
                    instagramActivity.startActivity(exportIntent);
                } catch (Exception e) {
                    showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_failed_read_config, e.getMessage()));
                }
            } else {
                showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
            }
        }));

        layout.addView(createActionRow(context, R.drawable.ic_restart, I18n.t(context, R.string.ig_dialog_dev_restore_default_config), "#FF9F0A", v -> {
            AlertDialog restoreDialog = new AlertDialog.Builder(context)
                    .setTitle(I18n.t(context, R.string.ig_dialog_dev_restore_default_config))
                    .setMessage(I18n.t(context, R.string.ig_dialog_dev_restore_default_config_confirm))
                    .setPositiveButton(I18n.t(context, R.string.ig_dialog_yes), (dialog, which) ->
                            ConfigManager.restoreDefaultConfig(context, Module.moduleSourceDir))
                    .setNegativeButton(I18n.t(context, R.string.ig_dialog_cancel), null)
                    .create();
            markDialogDecor(restoreDialog);
            restoreDialog.show();
        }));

        layout.addView(createDivider(context));

        ToggleRow buildExpiredSwitch = createSwitch(context, R.drawable.ic_block, "#FF453A", I18n.t(context, R.string.ig_dialog_dev_remove_build_expired), FeatureFlags.removeBuildExpiredPopup);
        buildExpiredSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.removeBuildExpiredPopup = isChecked;
            SettingsManager.saveAllFlags();
        });
        layout.addView(buildExpiredSwitch);

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_dev_options), layout, SettingsManager::saveAllFlags);
    }

    private static void showGhostOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow[] switches = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_dm_seen),         FeatureFlags.isGhostSeen),
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_voice_seen),      FeatureFlags.isGhostVoiceSeen),
                createSwitch(context, R.drawable.ic_chat, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_typing),          FeatureFlags.isGhostTyping),
                createSwitch(context, R.drawable.ic_story_ring, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_story_views),     FeatureFlags.isGhostStory),
                createSwitch(context, R.drawable.ic_live, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_live_presence),   FeatureFlags.isGhostLive),
                createSwitch(context, R.drawable.ic_camera, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_allow_screenshots_dms),FeatureFlags.allowScreenshots),
                createSwitch(context, R.drawable.ic_camera, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_bypass_screenshot),    FeatureFlags.isGhostScreenshot),
                createSwitch(context, R.drawable.ic_eye_off, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_hide_view_once),       FeatureFlags.isGhostViewOnce),
                createSwitch(context, R.drawable.ic_restart, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_unlimited_replays),    FeatureFlags.enableUnlimitedReplays),
                createSwitch(context, R.drawable.ic_eye, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_permanent_view_once),  FeatureFlags.permanentViewMode),
                createSwitch(context, R.drawable.ic_timer, "#5E5CE6", I18n.t(context, R.string.ig_dialog_ghost_keep_disappearing),    FeatureFlags.keepEphemeralMessages)};

        layout.addView(createActionRow(context, R.drawable.ic_tune, I18n.t(context, R.string.ig_dialog_customize_quick_toggle), "#5E5CE6", v -> showGhostQuickToggleOptions(context)));

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(switches));

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s :switches) {
                s.setChecked(isChecked);
            }
        });

        for (int i = 0; i < switches.length; i++) {
            final int index = i;
            switches[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                enableAllSwitch.setOnCheckedChangeListener(null);
                enableAllSwitch.setChecked(areAllEnabled(switches));
                enableAllSwitch.setOnCheckedChangeListener((buttonView2, isChecked2) -> {
                    for (ToggleRow s2 :switches) {
                        s2.setChecked(isChecked2);
                    }
                });

                switch (index) {
                    case 0:
                        FeatureFlags.isGhostSeen = isChecked;
                        break;
                    case 1:
                        FeatureFlags.isGhostVoiceSeen = isChecked;
                        break;
                    case 2:
                        FeatureFlags.isGhostTyping = isChecked;
                        break;
                    case 3:
                        FeatureFlags.isGhostStory = isChecked;
                        break;
                    case 4:
                        FeatureFlags.isGhostLive = isChecked;
                        break;
                    case 5:
                        FeatureFlags.allowScreenshots = isChecked;
                        break;
                    case 6:
                        FeatureFlags.isGhostScreenshot = isChecked;
                        break;
                    case 7:
                        FeatureFlags.isGhostViewOnce = isChecked;
                        break;
                    case 8:
                        FeatureFlags.enableUnlimitedReplays = isChecked;
                        break;
                    case 9:
                        FeatureFlags.permanentViewMode = isChecked;
                        break;
                    case 10:
                        FeatureFlags.keepEphemeralMessages = isChecked;
                        break;
                }

                SettingsManager.saveAllFlags();

                Activity activity = UIHookManager.getCurrentActivity();
                if (activity != null) {
                    GhostEmojiManager.addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
                }
            });
        }

        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :switches) {
            layout.addView(s);
        }

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_ghost_mode), layout, () -> {
        });
    }

    private static void showAdOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow adBlock = createSwitch(context, R.drawable.ic_shield, "#FF453A", I18n.t(context, R.string.ig_dialog_ad_block_ads), FeatureFlags.isAdBlockEnabled);

        ToggleRow analytics = createSwitch(context, R.drawable.ic_shield, "#FF453A", I18n.t(context, R.string.ig_dialog_ad_block_analytics), FeatureFlags.isAnalyticsBlocked);

        ToggleRow trackingLinks = createSwitch(context, R.drawable.ic_link, "#FF453A", I18n.t(context, R.string.ig_dialog_ad_disable_tracking), FeatureFlags.disableTrackingLinks);

        ToggleRow[] switches = new ToggleRow[]{adBlock, analytics, trackingLinks};

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(switches));

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s :switches) {
                s.setChecked(isChecked);
            }
        });

        for (int i = 0; i < switches.length; i++) {
            final int index = i;
            switches[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                enableAllSwitch.setOnCheckedChangeListener(null);
                enableAllSwitch.setChecked(areAllEnabled(switches));
                enableAllSwitch.setOnCheckedChangeListener((buttonView2, isChecked2) -> {
                    for (ToggleRow s2 :switches) {
                        s2.setChecked(isChecked2);
                    }
                });

                if (index == 0) FeatureFlags.isAdBlockEnabled = isChecked;
                if (index == 1) FeatureFlags.isAnalyticsBlocked = isChecked;
                if (index == 2) FeatureFlags.disableTrackingLinks = isChecked;

                SettingsManager.saveAllFlags();
            });
        }

        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :switches) {
            layout.addView(s);
        }

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_ad_analytics), layout, () -> {
        });
    }

    private static void showDistractionOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow extremeModeSwitch = createSwitch(context, R.drawable.ic_block, "#FF453A", I18n.t(context, R.string.ig_dialog_distraction_extreme_mode), FeatureFlags.isExtremeMode);
        ToggleRow disableStoriesSwitch = createSwitch(context, R.drawable.ic_story_ring, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_stories), FeatureFlags.disableStories);
        ToggleRow disableFeedSwitch = createSwitch(context, R.drawable.ic_block, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_feed), FeatureFlags.disableFeed);
        ToggleRow disableReelsSwitch = createSwitch(context, R.drawable.ic_movie, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_reels), FeatureFlags.disableReels);
        ToggleRow onlyInDMSwitch = createSwitch(context, R.drawable.ic_movie, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_reels_except_dm), FeatureFlags.disableReelsExceptDM);
        ToggleRow disableExploreSwitch = createSwitch(context, R.drawable.ic_search, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_explore), FeatureFlags.disableExplore);
        ToggleRow disableCommentsSwitch = createSwitch(context, R.drawable.ic_chat, "#30D158", I18n.t(context, R.string.ig_dialog_distraction_disable_comments), FeatureFlags.disableComments);

        ToggleRow[] switches = new ToggleRow[]{disableStoriesSwitch, disableFeedSwitch, disableReelsSwitch, onlyInDMSwitch, disableExploreSwitch, disableCommentsSwitch};

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(switches));

        if (FeatureFlags.isExtremeMode) {
            disableAllSwitches(switches, enableAllSwitch, onlyInDMSwitch);
            extremeModeSwitch.setChecked(true);
            extremeModeSwitch.setEnabled(false);
        }

        Runnable updateExtremeSwitchEnabled = () -> {
            if (!FeatureFlags.isExtremeMode) {
                boolean anyEnabled = false;
                for (ToggleRow s : switches) {
                    if (s.isChecked()) { anyEnabled = true; break; }
                }
                extremeModeSwitch.setEnabled(anyEnabled);
            }
        };

        updateExtremeSwitchEnabled.run();

        extremeModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(I18n.t(context, R.string.ig_dialog_distraction_extreme_title));
                builder.setMessage(I18n.t(context, R.string.ig_dialog_distraction_extreme_message));
                builder.setPositiveButton(I18n.t(context, R.string.ig_dialog_yes), (dialog, which) -> {
                    FeatureFlags.isExtremeMode = true;
                    FeatureFlags.isDistractionFree = true;

                    FeatureFlags.disableStories = disableStoriesSwitch.isChecked();
                    FeatureFlags.disableFeed = disableFeedSwitch.isChecked();
                    FeatureFlags.disableReels = disableReelsSwitch.isChecked();
                    FeatureFlags.disableReelsExceptDM = onlyInDMSwitch.isChecked();
                    FeatureFlags.disableExplore = disableExploreSwitch.isChecked();
                    FeatureFlags.disableComments = disableCommentsSwitch.isChecked();
                    SettingsManager.saveAllFlags();

                    disableAllSwitches(switches, enableAllSwitch, onlyInDMSwitch);
                    extremeModeSwitch.setEnabled(false);
                });
                builder.setNegativeButton(I18n.t(context, R.string.ig_dialog_cancel), (dialog, which) -> extremeModeSwitch.setChecked(false));
                AlertDialog extremeDialog = builder.create();
                markDialogDecor(extremeDialog);
                extremeDialog.show();
            }
        });

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s : switches) {
                s.setChecked(isChecked);
                s.setEnabled(true);
            }
            if (!isChecked) {
                onlyInDMSwitch.setChecked(false);
                onlyInDMSwitch.setEnabled(false);
            }
            updateExtremeSwitchEnabled.run();
        });

        disableReelsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            onlyInDMSwitch.setEnabled(isChecked);
            if (!isChecked) {
                onlyInDMSwitch.setChecked(false);
                onlyInDMSwitch.setEnabled(false);
            }
            updateMasterSwitch(enableAllSwitch, switches, disableReelsSwitch, onlyInDMSwitch);
            updateExtremeSwitchEnabled.run();
            SettingsManager.saveAllFlags();
        });

        onlyInDMSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !disableReelsSwitch.isChecked()) {
                disableReelsSwitch.setChecked(true);
            }
            updateMasterSwitch(enableAllSwitch, switches, disableReelsSwitch, onlyInDMSwitch);
            updateExtremeSwitchEnabled.run();
            SettingsManager.saveAllFlags();
        });

        for (ToggleRow s : new ToggleRow[]{disableStoriesSwitch, disableFeedSwitch, disableExploreSwitch, disableCommentsSwitch}) {
            s.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateMasterSwitch(enableAllSwitch, switches, disableReelsSwitch, onlyInDMSwitch);
                updateExtremeSwitchEnabled.run();
                SettingsManager.saveAllFlags();
            });
        }

        onlyInDMSwitch.setEnabled(disableReelsSwitch.isChecked());

        layout.addView(extremeModeSwitch);
        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :switches) {
            layout.addView(s);
        }

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_distraction_free), layout, () -> {
            FeatureFlags.disableStories = disableStoriesSwitch.isChecked();
            FeatureFlags.disableFeed = disableFeedSwitch.isChecked();
            FeatureFlags.disableReels = disableReelsSwitch.isChecked();
            FeatureFlags.disableReelsExceptDM = onlyInDMSwitch.isChecked();
            FeatureFlags.disableExplore = disableExploreSwitch.isChecked();
            FeatureFlags.disableComments = disableCommentsSwitch.isChecked();
        });

        SettingsManager.saveAllFlags();
    }

    private static void disableAllSwitches(ToggleRow[] switches, ToggleRow master, ToggleRow onlyInDMSwitch) {
        for (ToggleRow s : switches) {
            if (s == onlyInDMSwitch) {
                s.setEnabled(s.isChecked());
            } else {
                s.setEnabled(!s.isChecked());
            }
        }
        master.setEnabled(false);
    }

    private static void updateMasterSwitch(ToggleRow enableAllRow, ToggleRow[] switches, ToggleRow disableReelsSwitch, ToggleRow onlyInDMSwitch) {
        enableAllRow.setOnCheckedChangeListener(null);
        enableAllRow.setChecked(areAllEnabled(switches));
        enableAllRow.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s : switches) {
                s.setChecked(isChecked);
            }
            onlyInDMSwitch.setEnabled(disableReelsSwitch.isChecked());
        });
    }

    private static void showCleanFeedOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow hideSuggestedSwitch = createSwitch(context, R.drawable.ic_sparkle, "#64D2FF", I18n.t(context, R.string.ig_dialog_clean_feed_hide_suggested), FeatureFlags.hideSuggestionsInFeed);

        hideSuggestedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.hideSuggestionsInFeed = isChecked;
            SettingsManager.saveAllFlags();
        });

        layout.addView(hideSuggestedSwitch);

        ToggleRow hideThreadsSwitch = createSwitch(context, R.drawable.ic_sparkle, "#64D2FF", I18n.t(context, R.string.ig_dialog_clean_feed_hide_threads), FeatureFlags.hideThreadsSuggestions);

        hideThreadsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.hideThreadsSuggestions = isChecked;
            SettingsManager.saveAllFlags();
        });

        layout.addView(hideThreadsSwitch);

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_clean_feed), layout, () -> {});
    }

    private static void showMiscOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow[] switches = new ToggleRow[]{
                createSwitch(context, R.drawable.ic_story_ring, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_disable_story_autoswipe), FeatureFlags.disableStoryFlipping),
                createSwitch(context, R.drawable.ic_movie, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_disable_video_autoplay),  FeatureFlags.disableVideoAutoPlay),
                createSwitch(context, R.drawable.ic_block, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_disable_repost),          FeatureFlags.disableRepost),
                createSwitch(context, R.drawable.ic_notification, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_show_feature_toasts),     FeatureFlags.showFeatureToasts),
                createSwitch(context, R.drawable.ic_notification, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_show_follower_toast),     FeatureFlags.showFollowerToast),
                createSwitch(context, R.drawable.ic_at, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_view_story_mentions),     FeatureFlags.enableStoryMentions),
                createSwitch(context, R.drawable.ic_block, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_disable_discover_people), FeatureFlags.disableDiscoverPeople),
                createSwitch(context, R.drawable.ic_content_copy, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_copy_comment),            FeatureFlags.enableCopyComment),
                createSwitch(context, R.drawable.ic_heart, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_disable_double_tap_like), FeatureFlags.disableDoubleTapLike),
                createSwitch(context, R.drawable.ic_content_copy, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_copy_caption),            FeatureFlags.enableCaptionCopy),
                createSwitch(context, R.drawable.ic_search, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_photo_zoom),               FeatureFlags.enablePhotoZoom),
                createSwitch(context, R.drawable.ic_timer, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_spoof_last_seen),          FeatureFlags.spoofLastSeen),
                createSwitch(context, R.drawable.ic_shield, "#BF5AF2", I18n.t(context, R.string.ig_dialog_misc_bypass_channel_restrictions), FeatureFlags.bypassChannelRestrictions)
        };

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(switches));

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s :switches) {
                s.setChecked(isChecked);
            }
        });

        for (int i = 0; i < switches.length; i++) {
            final int index = i;
            switches[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                enableAllSwitch.setOnCheckedChangeListener(null);
                enableAllSwitch.setChecked(areAllEnabled(switches));
                enableAllSwitch.setOnCheckedChangeListener((buttonView2, isChecked2) -> {
                    for (ToggleRow s2 :switches) {
                        s2.setChecked(isChecked2);
                    }
                });

                switch (index) {
                    case 0:
                        FeatureFlags.disableStoryFlipping = isChecked;
                        break;
                    case 1:
                        FeatureFlags.disableVideoAutoPlay = isChecked;
                        break;
                    case 2:
                        FeatureFlags.disableRepost = isChecked;
                        break;
                    case 3:
                        FeatureFlags.showFeatureToasts = isChecked;
                        break;
                    case 4:
                        FeatureFlags.showFollowerToast = isChecked;
                        break;
                    case 5:
                        FeatureFlags.enableStoryMentions = isChecked;
                        break;
                    case 6:
                        FeatureFlags.disableDiscoverPeople = isChecked;
                        break;
                    case 7:
                        FeatureFlags.enableCopyComment = isChecked;
                        break;
                    case 8:
                        FeatureFlags.disableDoubleTapLike = isChecked;
                        break;
                    case 9:
                        FeatureFlags.enableCaptionCopy = isChecked;
                        break;
                    case 10:
                        FeatureFlags.enablePhotoZoom = isChecked;
                        break;
                    case 11:
                        FeatureFlags.spoofLastSeen = isChecked;
                        break;
                    case 12:
                        FeatureFlags.bypassChannelRestrictions = isChecked;
                        break;
                }

                SettingsManager.saveAllFlags();
            });
        }

        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :switches) {
            layout.addView(s);
        }

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_misc), layout, () -> {
        });
    }

    private static void showLocationOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        ToggleRow spoofSwitch = createSwitch(context, R.drawable.ic_pin, "#FFD60A",
                I18n.t(context, R.string.ig_dialog_location_spoof_enable), FeatureFlags.spoofLocation);
        spoofSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.spoofLocation = isChecked;
            SettingsManager.saveAllFlags();
        });

        String coordLabel = (FeatureFlags.spoofLat == 0.0 && FeatureFlags.spoofLng == 0.0)
                ? I18n.t(context, R.string.ig_dialog_location_unset)
                : I18n.t(context, R.string.ig_dialog_location_current, FeatureFlags.spoofLat, FeatureFlags.spoofLng);

        layout.addView(createDivider(context));
        layout.addView(spoofSwitch);
        layout.addView(createDivider(context));
        layout.addView(createActionRow(context, R.drawable.ic_pin,
                I18n.t(context, R.string.ig_dialog_location_pick) + " — " + coordLabel, "#FFD60A", v -> {
                    Bundle extras = new Bundle();
                    extras.putDouble(LocationPickerActivity.EXTRA_LAT, FeatureFlags.spoofLat);
                    extras.putDouble(LocationPickerActivity.EXTRA_LNG, FeatureFlags.spoofLng);
                    if (ModuleActivityLauncher.launch(context,
                            "ps.reso.instaeclipse.mods.location.LocationPickerActivity", extras)) {
                        if (currentDialog != null) {
                            try { currentDialog.dismiss(); } catch (Exception ignored) {}
                            currentDialog = null;
                        }
                    }
                }));

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_location), layout, () -> {
        });
    }

    private static void openThemeCustomizer(Context context) {
        if (ModuleActivityLauncher.launch(context,
                "ps.reso.instaeclipse.ui.theme.ThemeCustomizerActivity", null)) {
            if (currentDialog != null) {
                try { currentDialog.dismiss(); } catch (Exception ignored) {}
                currentDialog = null;
            }
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

    private static class RadioRow extends LinearLayout {
        private final TextView checkmark;

        RadioRow(Context context, String label, boolean checked) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(8, 4, 8, 4);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            setFocusable(true);

            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#2C2C2E")));
            bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            setBackground(bg);

            TextView labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(16);
            labelView.setPadding(0, 20, 16, 20);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            labelView.setLayoutParams(lp);

            checkmark = new TextView(context);
            checkmark.setText("✓");
            checkmark.setTextColor(Color.parseColor("#0A84FF"));
            checkmark.setTextSize(18);
            checkmark.setTypeface(null, Typeface.BOLD);
            checkmark.setVisibility(checked ? VISIBLE : INVISIBLE);

            addView(labelView);
            addView(checkmark);
        }

        void setChecked(boolean checked) {
            checkmark.setVisibility(checked ? VISIBLE : INVISIBLE);
        }
    }

    private static void showQualityOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        String[] labels = {
                I18n.t(context, R.string.ig_dialog_quality_auto),
                I18n.t(context, R.string.ig_dialog_quality_360),
                I18n.t(context, R.string.ig_dialog_quality_480),
                I18n.t(context, R.string.ig_dialog_quality_720),
                I18n.t(context, R.string.ig_dialog_quality_1080),
                I18n.t(context, R.string.ig_dialog_quality_max)
        };
        int[] values = {0, 360, 480, 720, 1080, Integer.MAX_VALUE};
        int current = FeatureFlags.forceReelQuality;

        RadioRow[] rows = new RadioRow[labels.length];
        for (int i = 0; i < labels.length; i++) {
            rows[i] = new RadioRow(context, labels[i], values[i] == current);
        }
        for (int i = 0; i < rows.length; i++) {
            int idx = i;
            rows[i].setOnClickListener(v -> {
                FeatureFlags.forceReelQuality = values[idx];
                SettingsManager.saveAllFlags();
                for (RadioRow r : rows) r.setChecked(false);
                rows[idx].setChecked(true);
            });
            layout.addView(rows[i]);
            if (i < rows.length - 1) layout.addView(createDivider(context));
        }

        showSectionDialog(context,
                I18n.t(context, R.string.ig_dialog_quality_force_reels) + " — " + qualityLabel(context, current),
                layout, () -> {
        });
    }

    private static void showDownloaderOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        layout.addView(createActionRow(context, R.drawable.ic_folder, I18n.t(context, R.string.ig_dialog_downloader_settings), "#FF9F0A", v -> showDownloaderSettings(context)));

        ToggleRow postSwitch    = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_posts),    FeatureFlags.enablePostDownload);
        ToggleRow storySwitch   = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_stories),  FeatureFlags.enableStoryDownload);
        ToggleRow reelSwitch    = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_reels),    FeatureFlags.enableReelDownload);
        ToggleRow profileSwitch = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_profiles), FeatureFlags.enableProfileDownload);
        ToggleRow voiceSwitch   = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_voice),    FeatureFlags.enableVoiceDownload);
        ToggleRow batchSwitch   = createSwitch(context, R.drawable.ic_download, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_batch),    FeatureFlags.enableBatchDownload);

        ToggleRow[] switches = new ToggleRow[]{postSwitch, storySwitch, reelSwitch, profileSwitch, voiceSwitch};

        ToggleRow enableAllSwitch = createSwitch(context, I18n.t(context, R.string.ig_dialog_enable_disable_all), areAllEnabled(switches));

        enableAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (ToggleRow s :switches) {
                s.setChecked(isChecked);
            }
        });

        for (int i = 0; i < switches.length; i++) {
            final int index = i;
            switches[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                enableAllSwitch.setOnCheckedChangeListener(null);
                enableAllSwitch.setChecked(areAllEnabled(switches));
                enableAllSwitch.setOnCheckedChangeListener((buttonView2, isChecked2) -> {
                    for (ToggleRow s2 :switches) {
                        s2.setChecked(isChecked2);
                    }
                });

                if (index == 0) FeatureFlags.enablePostDownload    = isChecked;
                if (index == 1) FeatureFlags.enableStoryDownload   = isChecked;
                if (index == 2) FeatureFlags.enableReelDownload    = isChecked;
                if (index == 3) FeatureFlags.enableProfileDownload = isChecked;
                if (index == 4) FeatureFlags.enableVoiceDownload   = isChecked;

                SettingsManager.saveAllFlags();
            });
        }

        layout.addView(createDivider(context));
        layout.addView(createEnableAllSwitch(context, enableAllSwitch));
        layout.addView(createDivider(context));

        for (ToggleRow s :switches) {
            layout.addView(s);
        }

        batchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.enableBatchDownload = isChecked;
            SettingsManager.saveAllFlags();
        });
        layout.addView(batchSwitch);

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_downloader), layout, () -> {});
    }

    private static void showDownloaderSettings(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        String folderRaw = FeatureFlags.downloaderCustomPath.isEmpty()
                ? android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/Download/InstaEclipse"
                : FeatureFlags.downloaderCustomPath;
        String folderDisplay = folderRaw.replaceFirst("^.*/0/", "");
        layout.addView(createInfoSection(context,
                I18n.t(context, R.string.ig_dialog_downloader_folder), folderDisplay));

        ToggleRow usernameFolderSwitch = createSwitch(context, R.drawable.ic_folder, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_username_subfolder), FeatureFlags.downloaderUsernameFolder);
        ToggleRow timestampSwitch = createSwitch(context, R.drawable.ic_timer, "#FF9F0A", I18n.t(context, R.string.ig_dialog_downloader_add_timestamp), FeatureFlags.downloaderAddTimestamp);

        usernameFolderSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.downloaderUsernameFolder = isChecked;
            SettingsManager.saveAllFlags();
        });
        timestampSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.downloaderAddTimestamp = isChecked;
            SettingsManager.saveAllFlags();
        });

        layout.addView(createDivider(context));
        layout.addView(usernameFolderSwitch);
        layout.addView(timestampSwitch);

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_downloader_settings), layout, () -> {});
    }

    private static Activity unwrapActivity(Context context) {
        while (context instanceof android.content.ContextWrapper wrapper) {
            if (context instanceof Activity a) return a;
            context = wrapper.getBaseContext();
        }
        return null;
    }

    @SuppressLint("SetTextI18n")
    private static void showBackupRestoreOptions(Context context) {
        LinearLayout layout = createSwitchLayout(context);

        layout.addView(createActionRow(context, R.drawable.ic_save, I18n.t(context, R.string.ig_dialog_backup_settings), "#30D158", v -> {
            try {
                String json = ps.reso.instaeclipse.utils.backup.SettingsBackupManager.toJson();
                Activity instagramActivity = UIHookManager.getCurrentActivity();
                if (instagramActivity != null && !instagramActivity.isFinishing()) {
                    Intent exportIntent = new Intent();
                    exportIntent.setComponent(new ComponentName("ps.reso.instaeclipse",
                            "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                    exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    exportIntent.putExtra("json_content", json);
                    exportIntent.putExtra("file_name", "instaeclipse_settings.json");
                    instagramActivity.startActivity(exportIntent);
                }
            } catch (Exception e) {
                showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_backup_failed, e.getMessage()));
            }
        }));

        layout.addView(createActionRow(context, R.drawable.ic_folder, I18n.t(context, R.string.ig_dialog_restore_settings), "#0A84FF", v -> {
            Activity instagramActivity = UIHookManager.getCurrentActivity();
            if (instagramActivity != null && !instagramActivity.isFinishing()) {
                Intent importIntent = new Intent();
                importIntent.setComponent(new ComponentName("ps.reso.instaeclipse",
                        "ps.reso.instaeclipse.mods.devops.config.JsonImportActivity"));
                importIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                importIntent.putExtra("target_package", context.getPackageName());
                importIntent.putExtra("broadcast_action", "ps.reso.instaeclipse.ACTION_RESTORE_SETTINGS");
                instagramActivity.startActivity(importIntent);
            } else {
                showSimpleDialog(context, I18n.t(context, R.string.ig_dialog_error), I18n.t(context, R.string.ig_dialog_instagram_not_ready));
            }
        }));

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_backup_restore), layout, () -> {});
    }

    private static void showAboutDialog(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 16);

        TextView title = new TextView(context);
        title.setText(I18n.t(context, R.string.ig_dialog_title));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 8);

        TextView creator = new TextView(context);
        creator.setText(I18n.t(context, R.string.ig_dialog_about_created_by));
        creator.setTextColor(Color.parseColor("#8E8E93"));
        creator.setTextSize(14f);
        creator.setGravity(Gravity.CENTER);
        creator.setPadding(0, 0, 0, 32);

        layout.addView(title);
        layout.addView(creator);
        LinearLayout linksRow = new LinearLayout(context);
        linksRow.setOrientation(LinearLayout.HORIZONTAL);
        linksRow.setGravity(Gravity.CENTER);

        View githubBtn = createActionRow(context, R.drawable.ic_github_logo, I18n.t(context, R.string.ig_dialog_about_github), "#8E8E93", v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/ReSo7200/InstaEclipse"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        githubBtn.setLayoutParams(btnLp);

        View tgBtn = createActionRow(context, R.drawable.ic_telegram_logo, I18n.t(context, R.string.ig_dialog_about_telegram), "#29B6F6", v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/InstaEclipse"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        });
        tgBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        linksRow.addView(githubBtn);
        linksRow.addView(tgBtn);
        layout.addView(linksRow);

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_about), layout, () -> {
        });
    }

    @SuppressLint("SetTextI18n")
    private static void showRestartSection(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView message = new TextView(context);
        message.setText(I18n.t(context, R.string.ig_dialog_restart_message));
        message.setTextColor(Color.WHITE);
        message.setTextSize(18f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 30);

        layout.addView(message);
        layout.addView(createActionRow(context, R.drawable.ic_restart, I18n.t(context, R.string.ig_dialog_restart_now), "#FF453A", v -> restartApp(context)));

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_restart), layout, () -> {
        });
    }

    private static void showClearCacheSection(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView message = new TextView(context);
        message.setText(I18n.t(context, R.string.ig_dialog_clear_cache_message));
        message.setTextColor(Color.WHITE);
        message.setTextSize(16f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 30);

        layout.addView(message);
        layout.addView(createActionRow(context, R.drawable.ic_delete, I18n.t(context, R.string.ig_dialog_clear_cache_now), "#FF9F0A", v -> {
            ps.reso.instaeclipse.utils.core.DexKitCache.clearCache();
            restartApp(context);
        }));

        showSectionDialog(context, I18n.t(context, R.string.ig_dialog_section_clear_cache), layout, () -> {});
    }

    @SuppressLint("SetTextI18n")
    private static void showSectionDialog(Context context, String title, LinearLayout contentLayout, Runnable onSave) {
        if (currentDialog != null) { try { currentDialog.dismiss(); } catch (Exception ignored) {} currentDialog = null; }

        IgColorRemapEngine.enterModuleUi();
        try {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 0);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#1C1C1E"));
        background.setCornerRadii(new float[]{40, 40, 40, 40, 0, 0, 0, 0});
        container.setBackground(background);

        container.addView(createDragHandle(context));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(24, 4, 24, 16);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView backBtn = new TextView(context);
        backBtn.setText("‹");
        backBtn.setTextColor(Color.parseColor("#0A84FF"));
        backBtn.setTextSize(36);
        backBtn.setIncludeFontPadding(false);
        backBtn.setGravity(Gravity.CENTER_VERTICAL);
        backBtn.setPadding(4, 0, 32, 4);
        backBtn.setMinWidth(0);
        backBtn.setMinimumWidth(0);
        StateListDrawable backBtnBg = new StateListDrawable();
        backBtnBg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#20FFFFFF")));
        backBtnBg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        backBtn.setBackground(backBtnBg);
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setOnClickListener(v -> {
            onSave.run();
            SettingsManager.saveAllFlags();
            showEclipseOptionsDialog(context);
        });

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);

        header.addView(backBtn);
        header.addView(titleView);
        container.addView(header);
        container.addView(createDivider(context));

        LinearLayout contentWrapper = new LinearLayout(context);
        contentWrapper.setOrientation(LinearLayout.VERTICAL);
        contentWrapper.setPadding(24, 0, 24, 0);
        contentWrapper.addView(contentLayout);
        container.addView(contentWrapper);

        container.addView(createDivider(context));

        View bottomPad = new View(context);
        bottomPad.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48));
        container.addView(bottomPad);

        ScrollView scrollView = createScrollableContainer(context, container);

        currentDialog = createBottomSheetDialog(context, scrollView);
        currentDialog.show();
        } finally {
            IgColorRemapEngine.leaveModuleUi();
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static class ToggleRow extends LinearLayout {
        private final Switch toggle;
        private final TextView labelView;

        ToggleRow(Context context, String label, boolean checked) {
            this(context, 0, null, label, checked);
        }

        ToggleRow(Context context, int iconRes, String accentHex, String label, boolean checked) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(8, 4, 8, 4);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            setFocusable(true);

            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#2C2C2E")));
            bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            setBackground(bg);

            if (iconRes != 0) {
                addView(buildIconChip(context, iconRes, accentHex));
            }

            labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(16);
            labelView.setPadding(0, 20, 16, 20);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            labelView.setLayoutParams(lp);

            toggle = new Switch(context);
            toggle.setChecked(checked);
            toggle.setThumbTintList(new ColorStateList(
                    new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Color.parseColor("#555555"), Color.parseColor("#448AFF"), Color.parseColor("#FFFFFF")}));
            toggle.setTrackTintList(new ColorStateList(
                    new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{Color.parseColor("#777777"), Color.parseColor("#1C4C78"), Color.parseColor("#CFD8DC")}));
            toggle.setClickable(false);
            toggle.setFocusable(false);

            addView(labelView);
            addView(toggle);
            setOnClickListener(v -> { if (isEnabled()) toggle.setChecked(!toggle.isChecked()); });
        }

        boolean isChecked() { return toggle.isChecked(); }
        void setChecked(boolean checked) { toggle.setChecked(checked); }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            toggle.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.38f);
        }

        void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
            toggle.setOnCheckedChangeListener(l);
        }

        void makeBold() {
            labelView.setTypeface(null, Typeface.BOLD);
            labelView.setTextSize(17);
        }
    }

    private static ToggleRow createSwitch(Context context, String label, boolean defaultState) {
        return new ToggleRow(context, label, defaultState);
    }

    private static ToggleRow createSwitch(Context context, int iconRes, String accentHex, String label, boolean defaultState) {
        return new ToggleRow(context, iconRes, accentHex, label, defaultState);
    }

    private static View buildIconChip(Context context, int iconRes, String accentHex) {
        android.widget.ImageView iconView = new android.widget.ImageView(context);
        int accent = Color.parseColor(accentHex);
        Drawable icon = loadModuleIcon(iconRes, accent);
        if (icon != null) iconView.setImageDrawable(icon);
        iconView.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int iconPad = dp(context, 8);
        iconView.setPadding(iconPad, iconPad, iconPad, iconPad);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor((accent & 0x00FFFFFF) | 0x33000000);
        chipBg.setCornerRadius(12);
        iconView.setBackground(chipBg);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36));
        iconLp.rightMargin = dp(context, 14);
        iconView.setLayoutParams(iconLp);
        return iconView;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout createSwitchLayout(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 8, 16, 8);
        return layout;
    }

    private static View createInfoSection(Context context, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(40, 24, 32, 24);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(17);
        labelView.setTextColor(Color.WHITE);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(13);
        valueView.setTextColor(Color.parseColor("#8E8E93"));
        valueView.setMaxLines(1);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        valueView.setPadding(16, 0, 0, 0);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(labelView);
        row.addView(valueView);
        return row;
    }

    private static View createActionRow(Context context, String emoji, String label, String accentHex, View.OnClickListener onClick) {
        TextView iconView = new TextView(context);
        iconView.setText(emoji);
        iconView.setTextSize(18);
        return createActionRow(context, iconView, label, accentHex, onClick);
    }

    private static View createActionRow(Context context, int iconRes, String label, String accentHex, View.OnClickListener onClick) {
        android.widget.ImageView iconView = new android.widget.ImageView(context);
        Drawable icon = loadModuleIcon(iconRes, Color.parseColor(accentHex));
        if (icon != null) iconView.setImageDrawable(icon);
        return createActionRow(context, iconView, label, accentHex, onClick);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private static Drawable loadModuleIcon(int iconRes, int tintColor) {
        try {
            Drawable icon = android.content.res.XModuleResources.createInstance(Module.moduleSourceDir, null)
                    .getDrawable(iconRes, null);
            icon = icon.mutate();
            icon.setColorFilter(new android.graphics.PorterDuffColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN));
            return icon;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View createActionRow(Context context, View iconView, String label, String accentHex, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(40, 22, 32, 22);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.parseColor("#2C2C2E")));
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackground(bg);

        GradientDrawable iconBg = new GradientDrawable();
        int accentColor = Color.parseColor(accentHex);
        iconBg.setColor((accentColor & 0x00FFFFFF) | 0x33000000);
        iconBg.setCornerRadius(14);
        iconView.setBackground(iconBg);
        iconView.setPadding(14, 10, 14, 10);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.rightMargin = 24;
        iconView.setLayoutParams(iconLp);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(16);
        labelView.setTextColor(Color.parseColor(accentHex));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(iconView);
        row.addView(labelView);
        row.setOnClickListener(onClick);
        return row;
    }

    private static View createDragHandle(Context context) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setPadding(0, 14, 0, 8);

        View handle = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 6);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        handle.setLayoutParams(lp);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Color.parseColor("#48484A"));
        handleBg.setCornerRadius(3);
        handle.setBackground(handleBg);

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

    private static ScrollView createScrollableContainer(Context context, View content) {
        return createScrollableContainer(context, content, 0.82f);
    }

    private static ScrollView createScrollableContainer(Context context, View content, float heightFraction) {
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        MaxHeightScrollView scrollView = new MaxHeightScrollView(context, Math.round(screenHeight * heightFraction));
        scrollView.addView(content);
        return scrollView;
    }

    private static AlertDialog createBottomSheetDialog(Context context, View contentView) {
        IgColorRemapEngine.markModuleDialogView(contentView);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(contentView).setCancelable(true).create();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.getAttributes().windowAnimations = android.R.style.Animation_InputMethod;
            IgColorRemapEngine.markModuleDialogView(window.getDecorView());
        }
        return dialog;
    }

    private static void markDialogDecor(AlertDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) IgColorRemapEngine.markModuleDialogView(window.getDecorView());
        View decor = dialog.findViewById(android.R.id.content);
        if (decor != null) IgColorRemapEngine.markModuleDialogView(decor);
    }

    private static LinearLayout createEnableAllSwitch(Context context, ToggleRow enableAllRow) {
        enableAllRow.makeBold();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(8, 4, 8, 4);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#2C2C2E"));
        background.setCornerRadius(16);
        container.setBackground(background);

        container.addView(enableAllRow);
        return container;
    }

    private static boolean areAllEnabled(ToggleRow[] rows) {
        for (ToggleRow r : rows) {
            if (!r.isChecked()) return false;
        }
        return true;
    }

}
