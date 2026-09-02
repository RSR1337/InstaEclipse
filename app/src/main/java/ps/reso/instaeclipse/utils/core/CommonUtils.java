package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.Arrays;
import java.util.List;

public class CommonUtils {
    public static final String IG_PACKAGE_NAME = "com.instagram.android";
    public static final String MY_PACKAGE_NAME = "ps.reso.instaeclipse";

    public static Context moduleContext(Context host) {
        if (host == null) return null;
        try {
            return host.createPackageContext(MY_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            return host;
        }
    }

    public static final String ACTION_REQUEST_LOGS = "ps.reso.instaeclipse.ACTION_REQUEST_LOGS";
    public static final String ACTION_LOGS_REPLY = "ps.reso.instaeclipse.ACTION_LOGS_REPLY";
    public static final String ACTION_CLEAR_LOGS = "ps.reso.instaeclipse.ACTION_CLEAR_LOGS";
    public static final String EXTRA_LOG_TEXT = "log_text";
    public static final String EXTRA_LOG_SOURCE = "source_package";
    public static final String EXTRA_LOG_ERROR = "error";

    public static final String ACTION_REQUEST_DOWNLOAD_HISTORY = "ps.reso.instaeclipse.ACTION_REQUEST_DOWNLOAD_HISTORY";
    public static final String ACTION_DOWNLOAD_HISTORY_REPLY = "ps.reso.instaeclipse.ACTION_DOWNLOAD_HISTORY_REPLY";
    public static final String ACTION_CLEAR_DOWNLOAD_HISTORY = "ps.reso.instaeclipse.ACTION_CLEAR_DOWNLOAD_HISTORY";
    public static final String EXTRA_DOWNLOAD_HISTORY_JSON = "download_history_json";
    public static final String EXTRA_DOWNLOAD_HISTORY_ERROR = "download_history_error";
    public static final String EXTRA_DOWNLOAD_HISTORY_SOURCE = "download_history_source";

    public static final String ACTION_REQUEST_FEATURE_STATUS = "ps.reso.instaeclipse.ACTION_REQUEST_FEATURE_STATUS";
    public static final String ACTION_FEATURE_STATUS_REPLY = "ps.reso.instaeclipse.ACTION_FEATURE_STATUS_REPLY";
    public static final String EXTRA_FEATURE_STATUS_JSON = "feature_status_json";
    public static final String EXTRA_FEATURE_STATUS_ERROR = "feature_status_error";
    public static final String EXTRA_FEATURE_STATUS_SOURCE = "feature_status_source";

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

    public static String getVariantLabel(String packageName) {
        if (IG_PACKAGE_NAME.equals(packageName)) return "Official";
        String[] parts = packageName.split("\\.");
        String best = parts.length >= 2 ? parts[1] : packageName;
        if (best.length() <= 2 && parts.length >= 3) best = parts[2];
        return Character.toUpperCase(best.charAt(0)) + best.substring(1).toLowerCase();
    }

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

    @SuppressWarnings("deprecation")
    public static Object readBundleValue(Bundle bundle, String key) {
        return bundle.get(key);
    }

    public static long readUpdatedAt(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Throwable ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
