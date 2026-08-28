package ps.reso.instaeclipse.mods.ui.theme;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public final class ThemeSettingsHelper {

    public static final int CUSTOM_PRESET_ID = 0;

    private ThemeSettingsHelper() {}

    public static boolean isCustomMode(int presetId) {
        return presetId == CUSTOM_PRESET_ID;
    }

    public static IgThemePalette resolveRawPalette(int presetId, String paletteJson, String customPresetsJson) {
        if (presetId >= CustomThemeStore.MIN_ID) {
            ThemePreset saved = CustomThemeStore.find(customPresetsJson, presetId);
            if (saved != null) return saved.palette.copy();
        }
        if (presetId > 0) return ThemePresets.getById(presetId).palette.copy();
        return IgThemePalette.fromJson(paletteJson);
    }

    public static IgThemePalette resolveEffectivePalette() {
        IgThemePalette palette = resolveRawPalette(
                FeatureFlags.themePresetId,
                FeatureFlags.themePaletteJson,
                FeatureFlags.themeCustomPresetsJson);
        applyRefinements(palette);
        return palette;
    }

    static void applyRefinements(IgThemePalette palette) {
        if (palette == null) return;
        if (IgThemeContrast.contrastRatio(palette.surface, palette.background) < 1.08f) {
            palette.surface = IgThemeContrast.mix(palette.surface, palette.primaryText, 0.07f);
        }
        IgThemeContrast.ensurePalette(palette);
    }
}
