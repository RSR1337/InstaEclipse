package ps.reso.instaeclipse.utils.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.theme.IgColorRemapEngine;
import ps.reso.instaeclipse.utils.core.CommonUtils;

public final class EclipseUiPalette {

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

    private static volatile boolean moduleColorsRegistered;

    private EclipseUiPalette(boolean dark, int sheetBg, int groupBg, int primaryText, int secondaryText,
                              int divider, int pressed, int handle, int accent, int headerBg, int sectionLabel) {
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

    private static EclipseUiPalette darkPalette(Context context, int accent) {
        return new EclipseUiPalette(true,
                ContextCompat.getColor(context, R.color.surface_0_dark),
                ContextCompat.getColor(context, R.color.surface_2_dark),
                ContextCompat.getColor(context, R.color.text_primary_dark),
                ContextCompat.getColor(context, R.color.text_secondary_dark),
                ContextCompat.getColor(context, R.color.outline_dark),
                ContextCompat.getColor(context, R.color.surface_3_dark),
                ContextCompat.getColor(context, R.color.text_tertiary_dark),
                accent,
                ContextCompat.getColor(context, R.color.surface_1_dark),
                ContextCompat.getColor(context, R.color.text_tertiary_dark));
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
                ContextCompat.getColor(context, R.color.text_tertiary_light));
    }

    private static void ensureModuleColorsRegistered(Context context, int accent) {
        if (moduleColorsRegistered) return;
        synchronized (EclipseUiPalette.class) {
            if (moduleColorsRegistered) return;
            int danger = ContextCompat.getColor(context, R.color.error_red);
            EclipseUiPalette dark = darkPalette(context, accent);
            EclipseUiPalette light = lightPalette(context, accent);
            IgColorRemapEngine.registerModuleUiColors(
                    dark.sheetBg, dark.groupBg, dark.primaryText, dark.secondaryText, dark.divider,
                    dark.pressed, dark.handle, dark.accent, dark.headerBg, dark.sectionLabel,
                    light.sheetBg, light.groupBg, light.primaryText, light.secondaryText, light.divider,
                    light.pressed, light.handle, light.accent, light.headerBg, light.sectionLabel,
                    danger, accent,
                    withAlpha(accent, 0x33), withAlpha(accent, 0x55), withAlpha(danger, 0x20),
                    withAlpha(danger, 0x33), withAlpha(0xFFFFFF, 0x20),
                    Color.WHITE, Color.TRANSPARENT,
                    Color.parseColor("#555555"), Color.parseColor("#3A3F4B"));
            moduleColorsRegistered = true;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}
