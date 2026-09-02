package ps.reso.instaeclipse.utils.ui;

import android.graphics.Color;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public final class FeatureAccents {

    public static final String DEV = "#0A84FF";
    public static final String GHOST = "#5E5CE6";
    public static final String ADS = "#FF453A";
    public static final String CLEAN_FEED = "#64D2FF";
    public static final String DISTRACTION = "#30D158";
    public static final String MISC = "#BF5AF2";
    public static final String DOWNLOADER = "#FF9F0A";
    public static final String LOCATION = "#FFD60A";
    public static final String QUALITY = "#64D2FF";
    public static final String THEME = "#FFB020";
    public static final String TOOLS = "#A8B0BE";
    public static final String VERIFY = "#FFB020";
    public static final String SUCCESS = "#30D158";
    public static final String INFO = "#0A84FF";
    public static final String NEUTRAL = "#8E8E93";
    public static final String TELEGRAM = "#29B6F6";

    private FeatureAccents() {
    }

    public static boolean isColorful() {
        return FeatureFlags.colorfulFeatureIcons;
    }

    public static int color(String hex) {
        if (!isColorful()) {
            return Color.parseColor(EclipseAccents.PRIMARY);
        }
        return Color.parseColor(hex);
    }

    public static int primary() {
        return Color.parseColor(EclipseAccents.PRIMARY);
    }
}
