package ps.reso.instaeclipse.mods.ui.theme;

import android.graphics.Color;

public final class IgThemeContrast {

    private IgThemeContrast() {}

    public static void ensurePalette(IgThemePalette palette) {
        if (palette == null) return;
        palette.primaryText = ensureReadable(palette.primaryText, palette.background, 4.5f);
        palette.secondaryText = ensureReadable(palette.secondaryText, palette.background, 3.0f);
        palette.icon = ensureReadable(palette.icon, palette.background, 3.0f);
        palette.glyph = ensureReadable(palette.glyph, palette.background, 4.5f);
        palette.divider = ensureReadable(palette.divider, palette.background, 1.4f);
        palette.border = ensureReadable(palette.border, palette.background, 1.6f);
        palette.link = ensureReadable(palette.link, palette.background, 3.0f);
        palette.error = ensureReadable(palette.error, palette.background, 3.0f);
        palette.destructive = ensureReadable(palette.destructive, palette.background, 3.0f);
        int onButton = contrastRatio(Color.WHITE, palette.button) >= contrastRatio(Color.BLACK, palette.button)
                ? Color.WHITE : Color.BLACK;
        if (contrastRatio(onButton, palette.button) < 3.0f) {
            palette.button = shiftToward(palette.button, onButton == Color.WHITE ? Color.BLACK : Color.WHITE, 0.18f);
        }
    }

    private static int ensureReadable(int foreground, int background, float minRatio) {
        if (isReadable(foreground, background, minRatio)) return foreground;
        float bgLum = relativeLuminance(background);
        float[] hsv = new float[3];
        Color.colorToHSV(foreground, hsv);
        float step = bgLum < 0.5f ? 0.05f : -0.05f;
        int current = foreground;
        for (int i = 0; i < 24; i++) {
            hsv[2] = clamp(hsv[2] + step);
            current = (Color.alpha(foreground) << 24) | (Color.HSVToColor(hsv) & 0x00FFFFFF);
            if (isReadable(current, background, minRatio)) return current;
        }
        return bgLum < 0.5f ? 0xFFFFFFFF : 0xFF000000;
    }

    private static boolean isReadable(int foreground, int background, float minRatio) {
        return contrastRatio(foreground, background) >= minRatio;
    }

    public static float contrastRatio(int foreground, int background) {
        float l1 = relativeLuminance(foreground) + 0.05f;
        float l2 = relativeLuminance(background) + 0.05f;
        return l1 > l2 ? l1 / l2 : l2 / l1;
    }

    private static float relativeLuminance(int color) {
        float r = channel(Color.red(color) / 255f);
        float g = channel(Color.green(color) / 255f);
        float b = channel(Color.blue(color) / 255f);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static int mix(int from, int to, float amount) {
        amount = clamp(amount);
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * amount);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.argb(a, r, g, b);
    }

    private static int shiftToward(int color, int target, float amount) {
        return mix(color, target, amount);
    }

    private static float channel(float c) {
        return c <= 0.03928f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float clamp(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }
}
