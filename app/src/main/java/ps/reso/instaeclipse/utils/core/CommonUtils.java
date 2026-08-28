package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.Arrays;
import java.util.List;

public class CommonUtils {
    public static final String IG_PACKAGE_NAME = "com.instagram.android";
    public static final String MY_PACKAGE_NAME = "ps.reso.instaeclipse";

    // In-app log viewer IPC
    public static final String ACTION_REQUEST_LOGS = "ps.reso.instaeclipse.ACTION_REQUEST_LOGS";
    public static final String ACTION_LOGS_REPLY = "ps.reso.instaeclipse.ACTION_LOGS_REPLY";
    public static final String ACTION_CLEAR_LOGS = "ps.reso.instaeclipse.ACTION_CLEAR_LOGS";
    public static final String EXTRA_LOG_TEXT = "log_text";
    public static final String EXTRA_LOG_SOURCE = "source_package";
    public static final String EXTRA_LOG_ERROR = "error";

    // Download History viewer IPC
    public static final String ACTION_REQUEST_DOWNLOAD_HISTORY = "ps.reso.instaeclipse.ACTION_REQUEST_DOWNLOAD_HISTORY";
    public static final String ACTION_DOWNLOAD_HISTORY_REPLY = "ps.reso.instaeclipse.ACTION_DOWNLOAD_HISTORY_REPLY";
    public static final String ACTION_CLEAR_DOWNLOAD_HISTORY = "ps.reso.instaeclipse.ACTION_CLEAR_DOWNLOAD_HISTORY";
    public static final String EXTRA_DOWNLOAD_HISTORY_JSON = "download_history_json";
    public static final String EXTRA_DOWNLOAD_HISTORY_ERROR = "download_history_error";
    public static final String EXTRA_DOWNLOAD_HISTORY_SOURCE = "download_history_source";

    /** All Instagram packages this module hooks into. */
    public static final List<String> SUPPORTED_PACKAGES = Arrays.asList(
            "com.instagram.android",
            "com.instagold.android",
            "com.instaflux.app",
            "com.myinsta.android",
            "cc.honista.app",
            "com.instaprime.android",
            "com.instafel.android",
            "com.instadm.android",
            "com.dfistagram.android",
            "com.Instander.android",
            "com.aero.instagram",
            "com.instapro.android",
            "com.instaflow.android",
            "com.instagram1.android",
            "com.instagram2.android",
            "com.instagramclone.android",
            "com.instaclone.android"
    );

    /**
     * Returns a human-readable variant label for a package name.
     * "com.instagram.android" → "Official"
     * "com.instaflow.android" → "Instaflow"
     * "cc.honista.app"        → "Honista"
     */
    public static String getVariantLabel(String packageName) {
        if (IG_PACKAGE_NAME.equals(packageName)) return "Official";
        String[] parts = packageName.split("\\.");
        // Use the most descriptive segment: skip generic TLDs and short segments
        String best = parts.length >= 2 ? parts[1] : packageName;
        // If second segment is very short or generic, try third
        if (best.length() <= 2 && parts.length >= 3) best = parts[2];
        return Character.toUpperCase(best.charAt(0)) + best.substring(1).toLowerCase();
    }

    /** Sends a broadcast to every supported Instagram variant currently installed. */
    public static void broadcastToInstagram(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : SUPPORTED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                Intent targeted = new Intent(intent);
                targeted.setPackage(pkg);
                context.sendBroadcast(targeted);
            } catch (Throwable ignored) {}
        }
    }

    /*
    Dev Purposes
    public static final String USER_SESSION_CLASS = "com.instagram.common.session.UserSession";
    */
}
