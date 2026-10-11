package ps.reso.instaeclipse.utils.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.theme.IgColorRemapEngine;
import ps.reso.instaeclipse.utils.core.CommonUtils;

public final class EclipseUiPalette {

    public static final int ON_ACCENT = 0xFF1A1205;
    public static final int ON_DANGER = 0xFF1A0604;
    public static final int DANGER_TEXT = 0xFFFFB3A9;
    public static final int SWITCH_OFF_TRACK = 0xFF1E1E26;
    public static final int SWITCH_OFF_BORDER = 0xFF4A4A57;
    public static final int SWITCH_OFF_THUMB = 0xFF8D8A99;
    public static final int UNSTABLE_TEXT = 0xFFFFB27A;
    public static final int UNSTABLE_BG = 0x24FF8C50;
    public static final int SCRIM = 0x99000000;

    public final boolean dark;
    public final int sheetBg;
    public final int groupBg;
    public final int primaryText;
    public final int secondaryText;
    public final int divider;
    public final int pressed;
    public final int handle;
    public final int accent;
    public final int headerBg;
    public final int sectionLabel;
    public final int chipBg;
    public final int border;
    public final int muted;
    public final int danger;

    private static volatile boolean moduleColorsRegistered;

    private EclipseUiPalette(boolean dark, int sheetBg, int groupBg, int primaryText, int secondaryText,
                              int divider, int pressed, int handle, int accent, int headerBg, int sectionLabel,
                              int chipBg, int border, int muted, int danger) {
        this.dark = dark;
        this.sheetBg = sheetBg;
        this.groupBg = groupBg;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.divider = divider;
        this.pressed = pressed;
        this.handle = handle;
        this.accent = accent;
        this.headerBg = headerBg;
        this.sectionLabel = sectionLabel;
        this.chipBg = chipBg;
        this.border = border;
        this.muted = muted;
        this.danger = danger;
    }

    public static EclipseUiPalette resolve(Context context) {
        boolean dark = isDark(context);
        Context resContext = CommonUtils.moduleContext(context);
        int accent = ContextCompat.getColor(resContext, R.color.corona_amber);
        return dark ? darkPalette(resContext, accent) : lightPalette(resContext, accent);
    }

    public static EclipseUiPalette resolveModuleSheet(Context context) {
        Context resContext = CommonUtils.moduleContext(context);
        int accent = ContextCompat.getColor(resContext, R.color.corona_amber);
        EclipseUiPalette palette = darkPalette(resContext, accent);
        ensureModuleColorsRegistered(resContext, accent);
        return palette;
    }

    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static EclipseUiPalette darkPalette(Context context, int accent) {
        return new EclipseUiPalette(true,
                ContextCompat.getColor(context, R.color.sheet_bg_dark),
                ContextCompat.getColor(context, R.color.sheet_group_dark),
                ContextCompat.getColor(context, R.color.text_primary_dark),
                ContextCompat.getColor(context, R.color.text_secondary_dark),
                ContextCompat.getColor(context, R.color.sheet_divider_dark),
                ContextCompat.getColor(context, R.color.sheet_chip_dark),
                ContextCompat.getColor(context, R.color.sheet_handle_dark),
                accent,
                ContextCompat.getColor(context, R.color.sheet_group_dark),
                ContextCompat.getColor(context, R.color.text_tertiary_dark),
                ContextCompat.getColor(context, R.color.sheet_chip_dark),
                ContextCompat.getColor(context, R.color.sheet_border_dark),
                ContextCompat.getColor(context, R.color.sheet_muted_dark),
                Color.parseColor(EclipseAccents.DANGER));
    }

    private static EclipseUiPalette lightPalette(Context context, int accent) {
        return new EclipseUiPalette(false,
                ContextCompat.getColor(context, R.color.surface_0_light),
                ContextCompat.getColor(context, R.color.surface_1_light),
                ContextCompat.getColor(context, R.color.text_primary_light),
                ContextCompat.getColor(context, R.color.text_secondary_light),
                ContextCompat.getColor(context, R.color.outline_light),
                ContextCompat.getColor(context, R.color.surface_2_light),
                ContextCompat.getColor(context, R.color.text_tertiary_light),
                accent,
                ContextCompat.getColor(context, R.color.surface_2_light),
                ContextCompat.getColor(context, R.color.text_tertiary_light),
                ContextCompat.getColor(context, R.color.surface_2_light),
                ContextCompat.getColor(context, R.color.outline_variant_light),
                ContextCompat.getColor(context, R.color.text_tertiary_light),
                Color.parseColor("#C9372C"));
    }

    private static void ensureModuleColorsRegistered(Context context, int accent) {
        if (moduleColorsRegistered) return;
        synchronized (EclipseUiPalette.class) {
            if (moduleColorsRegistered) return;
            int danger = Color.parseColor(EclipseAccents.DANGER);
            EclipseUiPalette dark = darkPalette(context, accent);
            EclipseUiPalette light = lightPalette(context, accent);
            IgColorRemapEngine.registerModuleUiColors(
                    dark.sheetBg, dark.groupBg, dark.primaryText, dark.secondaryText, dark.divider,
                    dark.pressed, dark.handle, dark.accent, dark.headerBg, dark.sectionLabel,
                    dark.chipBg, dark.border, dark.muted,
                    light.sheetBg, light.groupBg, light.primaryText, light.secondaryText, light.divider,
                    light.pressed, light.handle, light.accent, light.headerBg, light.sectionLabel,
                    danger, accent, ON_ACCENT, ON_DANGER, DANGER_TEXT,
                    SWITCH_OFF_TRACK, SWITCH_OFF_BORDER, SWITCH_OFF_THUMB, UNSTABLE_TEXT, UNSTABLE_BG,
                    withAlpha(accent, 0x14), withAlpha(accent, 0x29), withAlpha(accent, 0x33),
                    withAlpha(accent, 0x55), withAlpha(accent, 0x59),
                    withAlpha(danger, 0x12), withAlpha(danger, 0x1F), withAlpha(danger, 0x20),
                    withAlpha(danger, 0x29), withAlpha(danger, 0x33), withAlpha(danger, 0x59),
                    withAlpha(0xFFFFFF, 0x20), SCRIM,
                    Color.WHITE, Color.TRANSPARENT,
                    Color.parseColor("#555555"), Color.parseColor("#3A3F4B"));
            moduleColorsRegistered = true;
        }
    }

    private static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
