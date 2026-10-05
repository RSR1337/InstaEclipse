package ps.reso.instaeclipse.mods.ui.theme;

public final class ThemeContrastChecker {

    public static final int BUCKET_GOOD = 0;
    public static final int BUCKET_FAIR = 1;
    public static final int BUCKET_LOW = 2;

    public static float calculateContrastRatio(int foreground, int background) {
        return IgThemeContrast.contrastRatio(foreground, background);
    }

    public static int bucketFor(float contrastRatio) {
        if (contrastRatio >= 7f) return BUCKET_GOOD;
        if (contrastRatio >= 4.5f) return BUCKET_FAIR;
        return BUCKET_LOW;
    }

    private ThemeContrastChecker() {
    }
}
