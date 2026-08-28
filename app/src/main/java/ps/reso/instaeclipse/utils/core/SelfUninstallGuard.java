package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;

import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class SelfUninstallGuard {

    private static final String[] PREF_NAMES = {
            "instaeclipse_prefs",
            "instaeclipse_dexkit_cache",
            "instaeclipse_crash_guard",
            "instaeclipse_cache"
    };

    private static final String[] FILE_NAMES = {
            "instaeclipse_logging.log",
            "instaeclipse_module.log",
            "instaeclipse_downloads.log",
            "instaeclipse_runtime.log"
    };

    private static volatile boolean cleaned;

    private SelfUninstallGuard() {}

    public static boolean isModulePresent(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo(CommonUtils.MY_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (Throwable ignored) {
        }
        String path = NativeLibLoader.resolveModulePath(Module.moduleSourceDir);
        return path != null && new File(path).isFile();
    }

    public static boolean checkAndCleanIfUninstalled(Context context) {
        if (isModulePresent(context)) return false;
        if (!cleaned) {
            cleaned = true;
            ModuleLog.line("(InstaEclipse | SelfUninstallGuard): Module package is gone but hooks were still loaded — wiping residual data and skipping hook install.");
            wipeResidualData(context);
        }
        return true;
    }

    private static void wipeResidualData(Context context) {
        for (String name : PREF_NAMES) {
            try {
                context.deleteSharedPreferences(name);
            } catch (Throwable ignored) {}
        }
        File filesDir = context.getFilesDir();
        if (filesDir != null) {
            for (String name : FILE_NAMES) {
                deleteRecursively(new File(filesDir, name));
            }
            deleteRecursively(new File(filesDir, "mobileconfig"));
            File[] children = filesDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    String n = child.getName();
                    if (n != null && n.startsWith("instaeclipse")) {
                        deleteRecursively(child);
                    }
                }
            }
        }
        try {
            deleteRecursively(new File(context.getDataDir(), "code_cache/instaeclipse"));
        } catch (Throwable ignored) {}
        try {
            File sharedPrefs = new File(context.getDataDir(), "shared_prefs");
            File[] prefs = sharedPrefs.listFiles();
            if (prefs != null) {
                for (File pref : prefs) {
                    String n = pref.getName();
                    if (n != null && n.startsWith("instaeclipse")) {
                        deleteRecursively(pref);
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            deleteRecursively(new File(context.getFilesDir(), "download_history"));
        } catch (Throwable ignored) {}
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
