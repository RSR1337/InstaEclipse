package ps.reso.instaeclipse.mods.ui.theme;

import android.graphics.Color;

public final class ThemeContrastChecker {

    public static final int BUCKET_GOOD = 0;
    public static final int BUCKET_FAIR = 1;
    public static final int BUCKET_LOW = 2;

    public static float calculateContrastRatio(int foreground, int background) {
        double l1 = relativeLuminance(foreground) + 0.05;
        double l2 = relativeLuminance(background) + 0.05;
        double ratio = l1 > l2 ? l1 / l2 : l2 / l1;
        return (float) ratio;
    }

    public static int bucketFor(float contrastRatio) {
        if (contrastRatio >= 7f) return BUCKET_GOOD;
        if (contrastRatio >= 4.5f) return BUCKET_FAIR;
        return BUCKET_LOW;
    }

    private static double relativeLuminance(int color) {
        double r = channel(Color.red(color));
        double g = channel(Color.green(color));
        double b = channel(Color.blue(color));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int component) {
        double c = component / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private ThemeContrastChecker() {
    }
}
