package ps.reso.instaeclipse.utils.version;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ps.reso.instaeclipse.R;

public class VersionCheckUtility {

    private static final String CURRENT_VERSION = "0.6.1";
    private static final String VERSION_CHECK_URL =
            "https://raw.githubusercontent.com/ReSo7200/InstaEclipse/refs/heads/main/version.json";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private VersionCheckUtility() {
    }

    public static String currentVersion() {
        return CURRENT_VERSION;
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
            if (result.updateAvailable && context != null) {
                showUpdateDialog(context, result.updateUrl, result.latestVersion);
            }
        });
    }

    private static VersionCheckResult fetchResult() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_CHECK_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            VersionCheck versionCheck = new Gson().fromJson(response.toString(), VersionCheck.class);
            if (versionCheck == null || versionCheck.getLatestVersion() == null) {
                return VersionCheckResult.offline();
            }
            if (!CURRENT_VERSION.equals(versionCheck.getLatestVersion())) {
                return new VersionCheckResult(true, versionCheck.getLatestVersion(), versionCheck.getUpdateUrl());
            }
            return VersionCheckResult.upToDate();
        } catch (Exception e) {
            return VersionCheckResult.offline();
        }
    }

    private static void showUpdateDialog(Context context, String updateUrl, String newVersion) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.ig_update_title))
                .setMessage(context.getString(R.string.ig_update_message, newVersion))
                .setPositiveButton(context.getString(R.string.ig_update_button), (dialogInterface, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
                    context.startActivity(browserIntent);
                })
                .setNegativeButton(context.getString(R.string.ig_update_later), (dialogInterface, which) -> dialogInterface.dismiss())
                .show();
    }
}
