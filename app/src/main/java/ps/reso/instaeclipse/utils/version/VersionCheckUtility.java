package ps.reso.instaeclipse.utils.version;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ps.reso.instaeclipse.BuildConfig;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class VersionCheckUtility {

    private static final String VERSION_CHECK_URL =
            "https://raw.githubusercontent.com/ReSo7200/InstaEclipse/refs/heads/main/version.json";
    private static final String RELEASES_URL = "https://github.com/ReSo7200/InstaEclipse/releases/latest";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_RESPONSE_CHARS = 16 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private VersionCheckUtility() {
    }

    public static String currentVersion() {
        return BuildConfig.VERSION_NAME;
    }

    public static void checkForUpdatesSilently(VersionCheckListener listener) {
        EXECUTOR.execute(() -> {
            VersionCheckResult result = fetchResult();
            if (listener != null) {
                MAIN.post(() -> listener.onResult(result));
            }
        });
    }

    public static void checkForUpdates(Context context) {
        checkForUpdatesSilently(result -> {
            if (!result.updateAvailable || context == null) return;
            if (context instanceof Activity a && (a.isFinishing() || a.isDestroyed())) return;
            showUpdateDialog(context, result.updateUrl, result.latestVersion);
        });
    }

    private static VersionCheckResult fetchResult() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(VERSION_CHECK_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return VersionCheckResult.offline();

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    if (response.length() > MAX_RESPONSE_CHARS) return VersionCheckResult.offline();
                }
            }
            VersionCheck versionCheck = new Gson().fromJson(response.toString(), VersionCheck.class);
            if (versionCheck == null || versionCheck.getLatestVersion() == null) {
                return VersionCheckResult.offline();
            }
            String latest = versionCheck.getLatestVersion();
            if (VersionComparator.compare(latest, currentVersion()) > 0) {
                return new VersionCheckResult(true, latest, safeUpdateUrl(versionCheck.getUpdateUrl()));
            }
            return VersionCheckResult.upToDate();
        } catch (Exception e) {
            ModuleLog.line("(InstaEclipse | Update): check failed: " + e.getMessage());
            return VersionCheckResult.offline();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String safeUpdateUrl(String url) {
        if (url != null) {
            Uri uri = Uri.parse(url);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) return url;
        }
        return RELEASES_URL;
    }

    private static void showUpdateDialog(Context context, String updateUrl, String newVersion) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.ig_update_title))
                .setMessage(context.getString(R.string.ig_update_message, newVersion))
                .setPositiveButton(context.getString(R.string.ig_update_button), (dialogInterface, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
                    try {
                        context.startActivity(browserIntent);
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(context.getString(R.string.ig_update_later), (dialogInterface, which) -> dialogInterface.dismiss())
                .show();
    }
}
