package ps.reso.instaeclipse.Xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import org.luckypray.dexkit.DexKitBridge;

import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ps.reso.instaeclipse.mods.ads.AdBlocker;
import ps.reso.instaeclipse.mods.feed.FeedPhotoZoomHook;
import ps.reso.instaeclipse.mods.location.LocationSpoofHook;
import ps.reso.instaeclipse.utils.log.Logging;
import ps.reso.instaeclipse.utils.history.DownloadHistory;
import ps.reso.instaeclipse.mods.media.ForceReelQualityHook;
import ps.reso.instaeclipse.mods.feed.HideSuggestedFeedItemsHook;
import ps.reso.instaeclipse.mods.ads.TrackingLinkDisable;
import ps.reso.instaeclipse.mods.devops.BuildExpiredPopupHook;
import ps.reso.instaeclipse.mods.devops.DevOptionsUnlockHook;
import ps.reso.instaeclipse.mods.ghost.GhostChannelMarkAsReadHook;
import ps.reso.instaeclipse.mods.ghost.GhostDMMarkAsReadHook;
import ps.reso.instaeclipse.mods.ghost.GhostDMSeenHook;
import ps.reso.instaeclipse.mods.ghost.GhostEphemeralKeepHook;
import ps.reso.instaeclipse.mods.ghost.GhostPermanentViewHook;
import ps.reso.instaeclipse.mods.ghost.GhostReplayLimitHook;
import ps.reso.instaeclipse.mods.ghost.GhostScreenshotDetectionHook;
import ps.reso.instaeclipse.mods.ghost.GhostStorySeenHook;
import ps.reso.instaeclipse.mods.ghost.GhostTypingIndicatorHook;
import ps.reso.instaeclipse.mods.ghost.GhostViewOnceHook;
import ps.reso.instaeclipse.mods.ghost.ScreenshotPermissionHook;
import ps.reso.instaeclipse.mods.media.FeedVideoDownloadHook;
import ps.reso.instaeclipse.mods.media.PostDownloadContextMenuHook;
import ps.reso.instaeclipse.mods.media.ProfilePicDownloadHook;
import ps.reso.instaeclipse.mods.media.ReelDownloadHook;
import ps.reso.instaeclipse.mods.media.StoryDownloadHook;
import ps.reso.instaeclipse.mods.media.VoiceDownloadHook;
import ps.reso.instaeclipse.mods.misc.AppInitCrashGuardHook;
import ps.reso.instaeclipse.mods.misc.CommentCopyHook;
import ps.reso.instaeclipse.mods.misc.CaptionCopyContextMenuHook;
import ps.reso.instaeclipse.mods.misc.DisableDoubleTapLikeHook;
import ps.reso.instaeclipse.mods.misc.IGMantleCrashHook;
import ps.reso.instaeclipse.mods.misc.IgApiLookupCrashHook;
import ps.reso.instaeclipse.mods.misc.LsPatchVerifyErrorGuard;
import ps.reso.instaeclipse.mods.misc.StaleStateCrashGuardHook;
import ps.reso.instaeclipse.mods.misc.DisableStoryFlippingHook;
import ps.reso.instaeclipse.mods.misc.DisableVideoAutoPlayHook;
import ps.reso.instaeclipse.mods.misc.StoryMentionHook;
import ps.reso.instaeclipse.mods.network.IGNetworkInterceptor;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeEngine;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeHook;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;
import ps.reso.instaeclipse.utils.core.NativeLibLoader;
import ps.reso.instaeclipse.utils.core.SelfUninstallGuard;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureHealthReport;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class Module implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final List<String> SUPPORTED_PACKAGES = CommonUtils.SUPPORTED_PACKAGES;
    public static DexKitBridge dexKitBridge;
    public static ClassLoader hostClassLoader;
    public static String moduleSourceDir;
    public static Context hostAppContext;

    @Override
    public void initZygote(StartupParam startupParam) {
        moduleSourceDir = NativeLibLoader.normalizeModulePath(startupParam.modulePath);
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (SUPPORTED_PACKAGES.contains(lpparam.packageName)) {
            try {
                hookDefaultUncaughtExceptionHandler();
                setupUncaughtExceptionHandler();
                LsPatchVerifyErrorGuard.install();

                try {
                    new AppInitCrashGuardHook().install(null, lpparam.classLoader);
                } catch (Throwable t) {
                    ModuleLog.line("(InstaEclipse | AppInitGuard): ❌ Early install error: " + t.getMessage());
                }

                if (dexKitBridge == null) {
                    moduleSourceDir = NativeLibLoader.resolveModulePath(moduleSourceDir);
                    String dataDir = lpparam.appInfo != null ? lpparam.appInfo.dataDir : null;
                    NativeLibLoader.loadDexKit(moduleSourceDir, dataDir);
                    dexKitBridge = DexKitBridge.create(lpparam.appInfo.sourceDir);
                }

                hostClassLoader = lpparam.classLoader;
                hookInstagram(lpparam);

            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse): Failed to initialize DexKitBridge for " + lpparam.packageName + ": " + e.getMessage());
            } catch (UnsatisfiedLinkError e) {
                ModuleLog.line("(InstaEclipse): Failed to load native libs for " + lpparam.packageName + ": " + e.getMessage());
            }
        }
    }

    private void hookInstagram(XC_LoadPackage.LoadPackageParam lpparam) {

        try {

            XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = (Context) param.args[0];
                    hostAppContext = context;
                    if (SelfUninstallGuard.checkAndCleanIfUninstalled(context)) {
                        return;
                    }
                    SettingsManager.init(context);
                    SettingsManager.loadAllFlags(context);

                    try {
                        android.content.pm.PackageInfo pi =
                                context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        long vc = pi.getLongVersionCode();
                        DexKitCache.init(context, String.valueOf(vc));
                    } catch (Throwable e) {
                        ModuleLog.line("(DexKitCache) ❌ init failed: " + e.getMessage());
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {

                    Context context = (Context) param.args[0];
                    hostAppContext = context;
                    if (SelfUninstallGuard.checkAndCleanIfUninstalled(context)) {
                        return;
                    }
                    SettingsManager.init(context);
                    LsPatchCompanionBridge.initForHook(context);
                    SettingsManager.syncFromCompanion(context, false);
                    SettingsManager.loadAllFlags(context);

                    Logging.init(context, "instaeclipse_module.log");
                    DownloadHistory.init(context);

                    FeatureManager.refreshFeatureStatus();

                    registerSyncReceiver(context);

                    try {
                        UIHookManager.registerConfigImportReceiver(context);
                    } catch (Throwable e) {
                        ModuleLog.line("(InstaEclipse | ImportReceiver): ❌ " + e.getMessage());
                    }
                    try {
                        UIHookManager.registerSettingsRestoreReceiver(context);
                    } catch (Throwable e) {
                        ModuleLog.line("(InstaEclipse | RestoreReceiver): ❌ " + e.getMessage());
                    }
                    UIHookManager instagramUI = new UIHookManager();
                    instagramUI.mainActivity(hostClassLoader);

                    IGNetworkInterceptor interceptor = new IGNetworkInterceptor();

                    try {
                        new StaleStateCrashGuardHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | CrashGuard): ❌ Failed to hook");
                    }

                    try {
                        new AppInitCrashGuardHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | AppInitGuard): ❌ Failed to hook");
                    }

                    try {
                        new IGMantleCrashHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | MantleCrash): ❌ Failed to hook");
                    }

                    try {
                        new IgApiLookupCrashHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ApiLookupCrash): ❌ Failed to hook");
                    }

                    try {
                        new DevOptionsUnlockHook().handleDevOptions(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | DevOptions): ❌ Failed to hook");
                    }

                    try {
                        new GhostDMSeenHook().handleSeenBlock(dexKitBridge);
                        new GhostDMMarkAsReadHook(moduleSourceDir).install(lpparam.classLoader);
                        new GhostChannelMarkAsReadHook().install(lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | GhostSeen): ❌ Failed to hook");
                    }

                    try {
                        new GhostTypingIndicatorHook().handleTypingBlock(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | GhostTyping): ❌ Failed to hook");
                    }

                    try {
                        new GhostScreenshotDetectionHook().handleScreenshotBlock(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | GhostScreenshot): ❌ Failed to hook");
                    }

                    try {
                        new ScreenshotPermissionHook().install(lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ScreenshotPermission): ❌ Failed to hook");
                    }

                    try {
                        new GhostViewOnceHook().handleViewOnceBlock(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | GhostViewOnce): ❌ Failed to hook");
                    }

                    try {
                        new GhostReplayLimitHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | UnlimitedReplays): ❌ Failed to hook");
                    }

                    try {
                        new GhostStorySeenHook().handleStorySeenBlock(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | GhostStorySeen): ❌ Failed to hook");
                    }

                    try {
                        new HideSuggestedFeedItemsHook().install(dexKitBridge, hostClassLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | HideSuggested): ❌ Failed to hook");
                    }

                    try {
                        new AdBlocker().disableSponsoredContent(dexKitBridge, hostClassLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | AdBlocker): ❌ Failed to hook");
                    }

                    try {
                        new TrackingLinkDisable().disableTrackingLinks(hostClassLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | TrackingLinkDisable): ❌ Failed to hook");
                    }

                    try {
                        new DisableStoryFlippingHook().handleStoryFlippingDisable(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ Failed to hook");
                    }

                    try {
                        new StoryMentionHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | StoryMentions): ❌ Failed to hook");
                    }

                    try {
                        new CommentCopyHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | CopyComment): ❌ Failed to hook");
                    }

                    try {
                        new CaptionCopyContextMenuHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | Caption): ❌ Failed to hook");
                    }

                    try {
                        new DisableDoubleTapLikeHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | DoubleTapLike): ❌ Failed to hook");
                    }

                    try {
                        new FeedPhotoZoomHook().install(lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | PhotoZoom): ❌ Failed to hook");
                    }

                    try {
                        new LocationSpoofHook().install(lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | SpoofLocation): ❌ Failed to hook");
                    }

                    try {
                        new IgThemeHook().install(hostClassLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | Theme): ❌ Failed to hook");
                    }

                    try {
                        new ForceReelQualityHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ Failed to hook");
                    }

                    try {
                        new DisableVideoAutoPlayHook().handleAutoPlayDisable(dexKitBridge);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | AutoPlayDisable): ❌ Failed to hook");
                    }

                    try {
                        new BuildExpiredPopupHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | BuildExpired): ❌ Failed to hook");
                    }

                    try {
                        new FeedVideoDownloadHook().install(lpparam.classLoader);
                        FeedVideoDownloadHook.installVideoUrlCaptureHook(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | MediaDownload): ❌ Failed to hook");
                    }

                    try {
                        new VoiceDownloadHook(moduleSourceDir).install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ Failed to hook");
                    }

                    try {
                        new PostDownloadContextMenuHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | PostDownload): ❌ Failed to hook");
                    }

                    try {
                        new GhostEphemeralKeepHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | EphemeralHook): ❌ Failed to hook");
                    }

                    try {
                        new GhostPermanentViewHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ViewOnceMedia): ❌ Failed to hook");
                    }

                    try {
                        new StoryDownloadHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | StoryDownload): ❌ Failed to hook");
                    }

                    try {
                        new ReelDownloadHook().install(dexKitBridge, lpparam.classLoader);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ReelDownload): ❌ Failed to hook");
                    }

                    try {
                        ProfilePicDownloadHook.install();
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | ProfileDownload): ❌ Failed to hook");
                    }

                    try {
                        interceptor.handleInterceptor(lpparam);
                    } catch (Throwable ignored) {
                        ModuleLog.line("(InstaEclipse | Interceptor): ❌ Failed to hook");
                    }

                }

            });

        } catch (Exception e) {
            ModuleLog.line("(InstaEclipse): Failed to hook " + lpparam.packageName + ": " + e.getMessage());
        }
    }

    private void registerSyncReceiver(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF".equals(action)) {
                    String key = intent.getStringExtra("key");
                    boolean value = intent.getBooleanExtra("value", false);

                    ModuleLog.line("(InstaEclipse) Sync: Updating " + key + " to " + value);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    long updatedAt = intent.getLongExtra(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, System.currentTimeMillis());
                    prefs.edit()
                            .putBoolean(key, value)
                            .putLong(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, updatedAt)
                            .commit();

                    SettingsManager.loadAllFlags(ctx);
                    FeatureManager.refreshFeatureStatus();
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();

                } else if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING".equals(action)) {
                    String key = intent.getStringExtra("key");
                    String value = intent.getStringExtra("value");

                    ModuleLog.line("(InstaEclipse) Sync: Updating string pref " + key);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    long updatedAt = intent.getLongExtra(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, System.currentTimeMillis());
                    prefs.edit()
                            .putString(key, value)
                            .putLong(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, updatedAt)
                            .commit();

                    SettingsManager.loadAllFlags(ctx);
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();

                } else if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT".equals(action)) {
                    String key = intent.getStringExtra("key");
                    int value = intent.getIntExtra("value", 0);

                    ModuleLog.line("(InstaEclipse) Sync: Updating int pref " + key + " to " + value);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    long updatedAt = intent.getLongExtra(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, System.currentTimeMillis());
                    prefs.edit()
                            .putInt(key, value)
                            .putLong(ps.reso.instaeclipse.providers.SettingsProvider.KEY_UPDATED_AT, updatedAt)
                            .commit();

                    SettingsManager.loadAllFlags(ctx);
                    FeatureManager.refreshFeatureStatus();
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();

                } else if (CommonUtils.ACTION_REQUEST_LOGS.equals(action)) {
                    try {
                        Intent reply = new Intent(CommonUtils.ACTION_LOGS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        reply.putExtra(CommonUtils.EXTRA_LOG_TEXT, Logging.getSnapshotForIpc());
                        reply.putExtra(CommonUtils.EXTRA_LOG_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    } catch (Throwable t) {
                        Intent reply = new Intent(CommonUtils.ACTION_LOGS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        reply.putExtra(CommonUtils.EXTRA_LOG_ERROR, String.valueOf(t.getMessage()));
                        reply.putExtra(CommonUtils.EXTRA_LOG_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    }

                } else if (CommonUtils.ACTION_REQUEST_FEATURE_STATUS.equals(action)) {
                    try {
                        FeatureManager.refreshFeatureStatus();
                        Intent reply = new Intent(CommonUtils.ACTION_FEATURE_STATUS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        reply.putExtra(CommonUtils.EXTRA_FEATURE_STATUS_JSON, FeatureHealthReport.buildJson(ctx));
                        reply.putExtra(CommonUtils.EXTRA_FEATURE_STATUS_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    } catch (Throwable t) {
                        Intent reply = new Intent(CommonUtils.ACTION_FEATURE_STATUS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                        reply.putExtra(CommonUtils.EXTRA_FEATURE_STATUS_ERROR, String.valueOf(t.getMessage()));
                        reply.putExtra(CommonUtils.EXTRA_FEATURE_STATUS_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    }

                } else if (CommonUtils.ACTION_CLEAR_LOGS.equals(action)) {
                    Logging.clear();

                } else if (CommonUtils.ACTION_REQUEST_DOWNLOAD_HISTORY.equals(action)) {
                    try {
                        Intent reply = new Intent(CommonUtils.ACTION_DOWNLOAD_HISTORY_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.putExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_JSON, DownloadHistory.snapshotJson());
                        reply.putExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    } catch (Throwable t) {
                        Intent reply = new Intent(CommonUtils.ACTION_DOWNLOAD_HISTORY_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.putExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_ERROR, String.valueOf(t.getMessage()));
                        reply.putExtra(CommonUtils.EXTRA_DOWNLOAD_HISTORY_SOURCE, ctx.getPackageName());
                        ctx.sendBroadcast(reply);
                    }

                } else if (CommonUtils.ACTION_CLEAR_DOWNLOAD_HISTORY.equals(action)) {
                    DownloadHistory.clear();

                } else if ("ps.reso.instaeclipse.ACTION_REQUEST_PREFS".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested current preferences.");

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_PREFS");
                    reply.setPackage("ps.reso.instaeclipse");

                    Bundle bundle = new Bundle();
                    for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                        ps.reso.instaeclipse.providers.SettingsProvider.putValue(
                                bundle, entry.getKey(), entry.getValue());
                    }
                    reply.putExtras(bundle);
                    ctx.sendBroadcast(reply);

                } else if ("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested Dev Config export.");
                    try {
                        java.io.File source = new java.io.File(ctx.getFilesDir(), "mobileconfig/mc_overrides.json");
                        if (!source.exists()) {
                            ModuleLog.line("(InstaEclipse) Export: mc_overrides.json not found.");
                            Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_CONFIG");
                            reply.setPackage("ps.reso.instaeclipse");
                            reply.putExtra("error", "mc_overrides.json not found.");
                            ctx.sendBroadcast(reply);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(source))) {
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                        }
                        Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_CONFIG");
                        reply.setPackage("ps.reso.instaeclipse");
                        reply.putExtra("json_content", sb.toString().trim());
                        ctx.sendBroadcast(reply);
                        ModuleLog.line("(InstaEclipse) Export: config reply sent to companion.");
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse) Export: failed: " + e.getMessage());
                    }

                } else if ("ps.reso.instaeclipse.ACTION_BACKUP_SETTINGS".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested Settings backup.");
                    try {
                        String json = ps.reso.instaeclipse.utils.backup.SettingsBackupManager.toJson();
                        Intent exportIntent = new Intent();
                        exportIntent.setComponent(new android.content.ComponentName("ps.reso.instaeclipse", "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                        exportIntent.putExtra("json_content", json);
                        exportIntent.putExtra("file_name", "instaeclipse_settings.json");
                        exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(exportIntent);
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse) Failed to create backup: " + e.getMessage());
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        filter.addAction(CommonUtils.ACTION_REQUEST_LOGS);
        filter.addAction(CommonUtils.ACTION_REQUEST_FEATURE_STATUS);
        filter.addAction(CommonUtils.ACTION_CLEAR_LOGS);
        filter.addAction(CommonUtils.ACTION_REQUEST_DOWNLOAD_HISTORY);
        filter.addAction(CommonUtils.ACTION_CLEAR_DOWNLOAD_HISTORY);
        filter.addAction("ps.reso.instaeclipse.ACTION_REQUEST_PREFS");
        filter.addAction("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG");
        filter.addAction("ps.reso.instaeclipse.ACTION_BACKUP_SETTINGS");

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        }
    }

    private void setupUncaughtExceptionHandler() {
        try {
            Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
            if (original instanceof ModuleExceptionHandler) return;
            Thread.setDefaultUncaughtExceptionHandler(new ModuleExceptionHandler(original));
        } catch (Throwable ignored) {}
    }

    private void hookDefaultUncaughtExceptionHandler() {
        try {
            XposedHelpers.findAndHookMethod(
                    Thread.class,
                    "setDefaultUncaughtExceptionHandler",
                    Thread.UncaughtExceptionHandler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object handler = param.args[0];
                            if (handler != null && !(handler instanceof ModuleExceptionHandler)) {
                                param.args[0] = new ModuleExceptionHandler((Thread.UncaughtExceptionHandler) handler);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse) Failed to hook setDefaultUncaughtExceptionHandler: " + t.getMessage());
        }
    }

    private static final class ModuleExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler original;

        ModuleExceptionHandler(Thread.UncaughtExceptionHandler original) {
            this.original = original;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (LsPatchVerifyErrorGuard.isCertVerifyError(throwable)) {
                LsPatchVerifyErrorGuard.swallow(throwable);
                return;
            }
            if (thread != null && thread.getName() != null && (
                    thread.getName().startsWith("AppInit")
                            || thread.getName().startsWith("IgSchedulerExecutor")
                            || thread.getName().startsWith("IgExecutor")
                            || thread.getName().startsWith("SWPool"))) {
                try {
                    ModuleLog.line("(InstaEclipse | AppInitGuard): ⚠️ Swallowed uncaught exception in thread \"" + thread.getName() + "\": " + throwable.getMessage());
                } catch (Throwable ignored) {}
                return;
            }
            try {
                ModuleLog.line("(InstaEclipse) UNCAUGHT EXCEPTION in thread \"" + thread.getName() + "\":", throwable);
            } catch (Throwable ignored) {}
            if (StaleStateCrashGuardHook.looksLikeStaleFragmentState(throwable)) {
                try {
                    Context ctx = hostAppContext;
                    if (ctx != null) {
                        StaleStateCrashGuardHook.flagPendingCleanRestart(ctx);
                        ModuleLog.line("(InstaEclipse | CrashGuard): Flagged stale-state crash — next launch will drop savedInstanceState");
                    }
                } catch (Throwable ignored) {}
            }
            if (original != null) original.uncaughtException(thread, throwable);
        }
    }
}
