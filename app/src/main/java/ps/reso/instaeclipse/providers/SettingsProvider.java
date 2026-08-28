package ps.reso.instaeclipse.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;

public class SettingsProvider extends ContentProvider {

    public static final String CACHE_PREFS = "instaeclipse_cache";
    public static final String AUTHORITY = "ps.reso.instaeclipse.settings";
    public static final String KEY_UPDATED_AT = "settingsUpdatedAt";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isCallerAllowed()) return null;
        Context context = getContext();
        if (context == null) return null;
        if ("getAll".equals(method)) {
            return bundleFromPrefs(context);
        }
        if ("putAll".equals(method)) {
            if (extras == null) return null;
            int written = writeBundleToPrefs(context, extras);
            Bundle result = new Bundle();
            result.putBoolean("ok", true);
            result.putInt("written", written);
            return result;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private boolean isCallerAllowed() {
        Context context = getContext();
        if (context == null) return false;
        String callingPackage = getCallingPackage();
        if (callingPackage == null) {
            int callingUid = Binder.getCallingUid();
            if (callingUid == context.getApplicationInfo().uid) return true;
            String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
            if (packages != null) {
                for (String pkg : packages) {
                    if (CommonUtils.SUPPORTED_PACKAGES.contains(pkg)) return true;
                }
            }
            return false;
        }
        return callingPackage.equals(context.getPackageName())
                || CommonUtils.SUPPORTED_PACKAGES.contains(callingPackage);
    }

    private Bundle bundleFromPrefs(Context context) {
        Bundle bundle = new Bundle();
        for (java.util.Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            putValue(bundle, entry.getKey(), entry.getValue());
        }
        return bundle;
    }

    private int writeBundleToPrefs(Context context, Bundle extras) {
        android.content.SharedPreferences.Editor editor = prefs(context).edit();
        int count = 0;
        for (String key : extras.keySet()) {
            Object value = CommonUtils.readBundleValue(extras, key);
            if (putPref(editor, key, value)) count++;
        }
        editor.commit();
        LsPatchCompanionBridge.makeWorldReadable(context, CACHE_PREFS);
        return count;
    }

    private android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
    }

    public static void putValue(Bundle bundle, String key, Object value) {
        if (value instanceof Boolean) bundle.putBoolean(key, (Boolean) value);
        else if (value instanceof String) bundle.putString(key, (String) value);
        else if (value instanceof Integer) bundle.putInt(key, (Integer) value);
        else if (value instanceof Long) bundle.putLong(key, (Long) value);
        else if (value instanceof Float) bundle.putFloat(key, (Float) value);
    }

    public static boolean putPref(android.content.SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
            return true;
        }
        if (value instanceof String) {
            editor.putString(key, (String) value);
            return true;
        }
        if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
            return true;
        }
        if (value instanceof Long) {
            editor.putLong(key, (Long) value);
            return true;
        }
        if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
            return true;
        }
        return false;
    }
}
