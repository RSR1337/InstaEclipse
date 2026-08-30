package ps.reso.instaeclipse.utils.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeEngine;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeHook;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class LsPatchCompanionBridge {

    public static final String ACTION_REQUEST_PUSH = "org.lsposed.lspatch.action.REQUEST_PUSH";
    public static final String PREF_GROUP = "instaeclipse_prefs";
    public static final String COMPANION_CACHE = "instaeclipse_cache";

    private static final Object LOCK = new Object();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean listenerRegistered;
    private static volatile XposedService service;
    private static volatile SharedPreferences remotePrefs;
    private static volatile Context appContext;
    private static volatile boolean hookProcess;

    private LsPatchCompanionBridge() {}

    public static void initForCompanion(Context context) {
        appContext = context.getApplicationContext();
        hookProcess = false;
        registerListener();
        requestPush(appContext);
    }

    public static void initForHook(Context context) {
        appContext = context.getApplicationContext();
        hookProcess = true;
        registerListener();
        overlayCompanionPrefs(context);
    }

    public static void syncFrom(SharedPreferences source) {
        if (source == null) return;
        writeRemote(editor -> copyAll(source, editor));
    }

    public static void makeWorldReadable(Context context, String prefName) {
        if (context == null || prefName == null) return;
        try {
            File file = new File(
                    context.getApplicationInfo().dataDir + "/shared_prefs/" + prefName + ".xml");
            file.setReadable(true, false);
        } catch (Throwable ignored) {}
    }

    private static void registerListener() {
        synchronized (LOCK) {
            if (listenerRegistered) return;
            listenerRegistered = true;
        }
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService bound) {
                    service = bound;
                    try {
                        remotePrefs = bound.getRemotePreferences(PREF_GROUP);
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | LSPatch): remote prefs unavailable: " + t.getMessage());
                        remotePrefs = null;
                        return;
                    }
                    Context ctx = appContext;
                    if (ctx == null) return;
                    if (hookProcess) {
                        applyRemoteToHost(ctx);
                        listenForRemoteChanges(ctx);
                    } else {
                        syncFrom(ctx.getSharedPreferences(COMPANION_CACHE, Context.MODE_PRIVATE));
                    }
                }

                @Override
                public void onServiceDied(XposedService dead) {
                    if (service == dead) {
                        service = null;
                        remotePrefs = null;
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): service listener failed: " + t.getMessage());
        }
    }

    private static void requestPush(Context context) {
        try {
            Intent query = new Intent(ACTION_REQUEST_PUSH);
            List<ResolveInfo> services = context.getPackageManager().queryIntentServices(query, 0);
            if (services == null || services.isEmpty()) {
                bindPull(context, query);
                return;
            }
            for (ResolveInfo info : services) {
                if (info.serviceInfo == null) continue;
                Intent explicit = new Intent(query);
                explicit.setPackage(info.serviceInfo.packageName);
                if (bindPull(context, explicit)) return;
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): bind REQUEST_PUSH failed: " + t.getMessage());
        }
    }

    private static boolean bindPull(Context context, Intent intent) {
        try {
            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    try {
                        org.lsposed.lspatch.IXposedServicePull pull =
                                org.lsposed.lspatch.IXposedServicePull.Stub.asInterface(binder);
                        if (pull != null) pull.requestPush();
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | LSPatch): requestPush failed: " + t.getMessage());
                    } finally {
                        try {
                            context.unbindService(this);
                        } catch (Throwable ignored) {}
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };
            return context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): bind REQUEST_PUSH failed: " + t.getMessage());
            return false;
        }
    }

    private static void overlayCompanionPrefs(Context context) {
        try {
            XSharedPreferences cache = new XSharedPreferences(CommonUtils.MY_PACKAGE_NAME, COMPANION_CACHE);
            cache.reload();
            if (!cache.getAll().isEmpty()) {
                SettingsManager.mergeFrom(context, cache);
            }
        } catch (Throwable ignored) {}
        try {
            XSharedPreferences prefs = new XSharedPreferences(CommonUtils.MY_PACKAGE_NAME, PREF_GROUP);
            prefs.reload();
            if (!prefs.getAll().isEmpty()) {
                SettingsManager.mergeFrom(context, prefs);
            }
        } catch (Throwable ignored) {}
    }

    private static void applyRemoteToHost(Context context) {
        SharedPreferences remote = remotePrefs;
        if (remote == null) return;
        try {
            SettingsManager.mergeFrom(context, remote);
            FeatureManager.refreshFeatureStatus();
            IgThemeEngine.invalidate();
            IgThemeHook.refreshCurrentActivity();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): apply remote prefs failed: " + t.getMessage());
        }
    }

    private static void listenForRemoteChanges(Context context) {
        SharedPreferences remote = remotePrefs;
        if (remote == null) return;
        try {
            remote.registerOnSharedPreferenceChangeListener((prefs, key) -> mainHandler.post(() -> {
                try {
                    SettingsManager.mergeFrom(context, prefs);
                    FeatureManager.refreshFeatureStatus();
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();
                } catch (Throwable ignored) {}
            }));
        } catch (Throwable ignored) {}
    }

    private interface RemoteEdit {
        void apply(SharedPreferences.Editor editor);
    }

    private static void writeRemote(RemoteEdit edit) {
        SharedPreferences remote = remotePrefs;
        if (remote == null) return;
        try {
            SharedPreferences.Editor editor = remote.edit();
            edit.apply(editor);
            editor.apply();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LSPatch): write remote prefs failed: " + t.getMessage());
        }
    }

    private static void copyAll(SharedPreferences source, SharedPreferences.Editor editor) {
        Map<String, ?> all = source.getAll();
        if (all == null) return;
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            }
        }
    }
}
