package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.Map;

import ps.reso.instaeclipse.providers.SettingsProvider;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class SettingsManager {
    private static final String PREF_NAME = "instaeclipse_prefs";
    private static SharedPreferences prefs;
    private static Context appContext;

    public static void init(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static long stampUpdatedAt(SharedPreferences.Editor editor) {
        long updatedAt = System.currentTimeMillis();
        editor.putLong(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        return updatedAt;
    }

    public static void saveAllFlags() {
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("isDevEnabled", FeatureFlags.isDevEnabled);

        // Ghost Mode
        editor.putBoolean("isGhostModeEnabled", FeatureFlags.isGhostModeEnabled);
        editor.putBoolean("isGhostSeen", FeatureFlags.isGhostSeen);
        editor.putBoolean("isGhostTyping", FeatureFlags.isGhostTyping);
        editor.putBoolean("isGhostScreenshot", FeatureFlags.isGhostScreenshot);
        editor.putBoolean("isGhostViewOnce", FeatureFlags.isGhostViewOnce);
        editor.putBoolean("enableUnlimitedReplays", FeatureFlags.enableUnlimitedReplays);
        editor.putBoolean("isGhostStory", FeatureFlags.isGhostStory);
        editor.putBoolean("isGhostLive", FeatureFlags.isGhostLive);
        editor.putBoolean("allowScreenshots", FeatureFlags.allowScreenshots);
        editor.putBoolean("keepEphemeralMessages", FeatureFlags.keepEphemeralMessages);
        editor.putBoolean("permanentViewMode", FeatureFlags.permanentViewMode);

        // Quick Toggles
        editor.putBoolean("quickToggleSeen", FeatureFlags.quickToggleSeen);
        editor.putBoolean("quickToggleTyping", FeatureFlags.quickToggleTyping);
        editor.putBoolean("quickToggleScreenshot", FeatureFlags.quickToggleScreenshot);
        editor.putBoolean("quickToggleViewOnce", FeatureFlags.quickToggleViewOnce);
        editor.putBoolean("quickToggleStory", FeatureFlags.quickToggleStory);
        editor.putBoolean("quickToggleLive", FeatureFlags.quickToggleLive);
        editor.putBoolean("quickToggleEphemeral", FeatureFlags.quickToggleEphemeral);
        editor.putBoolean("quickToggleReplays", FeatureFlags.quickToggleReplays);
        editor.putBoolean("quickTogglePermanentView", FeatureFlags.quickTogglePermanentView);
        editor.putBoolean("quickToggleAllowScreenshots", FeatureFlags.quickToggleAllowScreenshots);

        // Distraction Free
        editor.putBoolean("isExtremeMode", FeatureFlags.isExtremeMode);
        editor.putBoolean("isDistractionFree", FeatureFlags.isDistractionFree);
        editor.putBoolean("disableStories", FeatureFlags.disableStories);
        editor.putBoolean("disableFeed", FeatureFlags.disableFeed);
        editor.putBoolean("disableReels", FeatureFlags.disableReels);
        editor.putBoolean("disableReelsExceptDM", FeatureFlags.disableReelsExceptDM);
        editor.putBoolean("disableExplore", FeatureFlags.disableExplore);
        editor.putBoolean("disableComments", FeatureFlags.disableComments);

        // Clean Feed
        editor.putBoolean("hideSuggestionsInFeed", FeatureFlags.hideSuggestionsInFeed);
        editor.putBoolean("hideThreadsSuggestions", FeatureFlags.hideThreadsSuggestions);

        // Ads
        editor.putBoolean("isAdBlockEnabled", FeatureFlags.isAdBlockEnabled);
        editor.putBoolean("isAnalyticsBlocked", FeatureFlags.isAnalyticsBlocked);
        editor.putBoolean("disableTrackingLinks", FeatureFlags.disableTrackingLinks);

        // Misc
        editor.putBoolean("isMiscEnabled", FeatureFlags.isMiscEnabled);
        editor.putBoolean("disableStoryFlipping", FeatureFlags.disableStoryFlipping);
        editor.putBoolean("disableVideoAutoPlay", FeatureFlags.disableVideoAutoPlay);
        editor.putBoolean("spoofLastSeen", FeatureFlags.spoofLastSeen);
        editor.putBoolean("spoofLocation", FeatureFlags.spoofLocation);
        editor.putString("spoofLat", String.valueOf(FeatureFlags.spoofLat));
        editor.putString("spoofLng", String.valueOf(FeatureFlags.spoofLng));
        editor.putInt("forceReelQuality", FeatureFlags.forceReelQuality);
        editor.putBoolean("disableRepost", FeatureFlags.disableRepost);
        editor.putBoolean("showFollowerToast", FeatureFlags.showFollowerToast);
        editor.putBoolean("showFeatureToasts", FeatureFlags.showFeatureToasts);
        editor.putBoolean("enableStoryMentions", FeatureFlags.enableStoryMentions);
        editor.putBoolean("disableDiscoverPeople", FeatureFlags.disableDiscoverPeople);
        editor.putBoolean("removeBuildExpiredPopup", FeatureFlags.removeBuildExpiredPopup);
        editor.putBoolean("enableCopyComment", FeatureFlags.enableCopyComment);
        editor.putBoolean("enableCaptionCopy", FeatureFlags.enableCaptionCopy);
        editor.putBoolean("disableDoubleTapLike", FeatureFlags.disableDoubleTapLike);
        editor.putBoolean("enablePhotoZoom", FeatureFlags.enablePhotoZoom);
        editor.putBoolean("enablePostDownload", FeatureFlags.enablePostDownload);
        editor.putBoolean("enableStoryDownload", FeatureFlags.enableStoryDownload);
        editor.putBoolean("enableReelDownload", FeatureFlags.enableReelDownload);
        editor.putBoolean("enableProfileDownload", FeatureFlags.enableProfileDownload);
        editor.putBoolean("enableBatchDownload", FeatureFlags.enableBatchDownload);
        editor.putBoolean("downloaderUsernameFolder", FeatureFlags.downloaderUsernameFolder);
        editor.putBoolean("downloaderAddTimestamp", FeatureFlags.downloaderAddTimestamp);
        editor.putString("downloaderCustomPath", FeatureFlags.downloaderCustomPath);
        editor.putString("downloaderCustomUri",  FeatureFlags.downloaderCustomUri);

        editor.putBoolean("customThemeEnabled", FeatureFlags.customThemeEnabled);
        editor.putInt("themePresetId", FeatureFlags.themePresetId);
        editor.putString("themePaletteJson", FeatureFlags.themePaletteJson);
        editor.putString("themeCustomPresetsJson", FeatureFlags.themeCustomPresetsJson);

        stampUpdatedAt(editor);
        editor.commit();

        LsPatchCompanionBridge.syncFrom(prefs);
        LsPatchCompanionBridge.makeWorldReadable(appContext, PREF_NAME);
        pushSettingsToCompanion();
        FeatureManager.refreshFeatureStatus();
    }

    public static void mergeFrom(Context context, SharedPreferences source) {
        if (source == null) return;
        init(context);
        Map<String, ?> all = source.getAll();
        if (all == null || all.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
                changed = true;
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
                changed = true;
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
                changed = true;
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
                changed = true;
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
                changed = true;
            }
        }
        if (changed) {
            editor.commit();
            loadAllFlags(context);
        }
    }

    public static void loadAllFlags(Context context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        FeatureFlags.isDevEnabled = prefs.getBoolean("isDevEnabled", false);

        // Ghost Mode
        FeatureFlags.isGhostModeEnabled = prefs.getBoolean("isGhostModeEnabled", false);
        FeatureFlags.isGhostSeen = prefs.getBoolean("isGhostSeen", false);
        FeatureFlags.isGhostTyping = prefs.getBoolean("isGhostTyping", false);
        FeatureFlags.isGhostScreenshot = prefs.getBoolean("isGhostScreenshot", false);
        FeatureFlags.isGhostViewOnce = prefs.getBoolean("isGhostViewOnce", false);
        FeatureFlags.enableUnlimitedReplays = prefs.getBoolean("enableUnlimitedReplays", false);
        FeatureFlags.isGhostStory = prefs.getBoolean("isGhostStory", false);
        FeatureFlags.isGhostLive = prefs.getBoolean("isGhostLive", false);
        FeatureFlags.allowScreenshots = prefs.getBoolean("allowScreenshots", false);
        FeatureFlags.keepEphemeralMessages = prefs.getBoolean("keepEphemeralMessages", false);
        FeatureFlags.permanentViewMode = prefs.getBoolean("permanentViewMode", false);

        // Quick Toggles
        FeatureFlags.quickToggleSeen = prefs.getBoolean("quickToggleSeen", false);
        FeatureFlags.quickToggleTyping = prefs.getBoolean("quickToggleTyping", false);
        FeatureFlags.quickToggleScreenshot = prefs.getBoolean("quickToggleScreenshot", false);
        FeatureFlags.quickToggleViewOnce = prefs.getBoolean("quickToggleViewOnce", false);
        FeatureFlags.quickToggleStory = prefs.getBoolean("quickToggleStory", false);
        FeatureFlags.quickToggleLive = prefs.getBoolean("quickToggleLive", false);
        FeatureFlags.quickToggleEphemeral = prefs.getBoolean("quickToggleEphemeral", false);
        FeatureFlags.quickToggleReplays = prefs.getBoolean("quickToggleReplays", false);
        FeatureFlags.quickTogglePermanentView = prefs.getBoolean("quickTogglePermanentView", false);
        FeatureFlags.quickToggleAllowScreenshots = prefs.getBoolean("quickToggleAllowScreenshots", false);

        // Distraction Free
        FeatureFlags.isExtremeMode = prefs.getBoolean("isExtremeMode", false);
        FeatureFlags.isDistractionFree = prefs.getBoolean("isDistractionFree", false);
        FeatureFlags.disableStories = prefs.getBoolean("disableStories", false);
        FeatureFlags.disableFeed = prefs.getBoolean("disableFeed", false);
        FeatureFlags.disableReels = prefs.getBoolean("disableReels", false);
        FeatureFlags.disableReelsExceptDM = prefs.getBoolean("disableReelsExceptDM", false);
        FeatureFlags.disableExplore = prefs.getBoolean("disableExplore", false);
        FeatureFlags.disableComments = prefs.getBoolean("disableComments", false);

        // Clean Feed
        FeatureFlags.hideSuggestionsInFeed = prefs.getBoolean("hideSuggestionsInFeed", false);
        FeatureFlags.hideThreadsSuggestions = prefs.getBoolean("hideThreadsSuggestions", false);

        // Ads
        FeatureFlags.isAdBlockEnabled = prefs.getBoolean("isAdBlockEnabled", false);
        FeatureFlags.isAnalyticsBlocked = prefs.getBoolean("isAnalyticsBlocked", false);
        FeatureFlags.disableTrackingLinks = prefs.getBoolean("disableTrackingLinks", false);

        // Misc
        FeatureFlags.isMiscEnabled = prefs.getBoolean("isMiscEnabled", false);
        FeatureFlags.disableStoryFlipping = prefs.getBoolean("disableStoryFlipping", false);
        FeatureFlags.disableVideoAutoPlay = prefs.getBoolean("disableVideoAutoPlay", false);
        FeatureFlags.spoofLastSeen = prefs.getBoolean("spoofLastSeen", false);
        FeatureFlags.spoofLocation = prefs.getBoolean("spoofLocation", false);
        FeatureFlags.spoofLat = readDoublePref(prefs, "spoofLat", 0.0);
        FeatureFlags.spoofLng = readDoublePref(prefs, "spoofLng", 0.0);
        FeatureFlags.forceReelQuality = prefs.getInt("forceReelQuality", 0);
        FeatureFlags.disableRepost = prefs.getBoolean("disableRepost", false);
        FeatureFlags.showFollowerToast = prefs.getBoolean("showFollowerToast", false);
        FeatureFlags.showFeatureToasts = prefs.getBoolean("showFeatureToasts", false);
        FeatureFlags.enableStoryMentions = prefs.getBoolean("enableStoryMentions", false);
        FeatureFlags.disableDiscoverPeople = prefs.getBoolean("disableDiscoverPeople", false);
        FeatureFlags.removeBuildExpiredPopup = prefs.getBoolean("removeBuildExpiredPopup", false);
        FeatureFlags.enableCopyComment = prefs.getBoolean("enableCopyComment", false);
        FeatureFlags.enableCaptionCopy = prefs.getBoolean("enableCaptionCopy", false);
        FeatureFlags.disableDoubleTapLike = prefs.getBoolean("disableDoubleTapLike", false);
        FeatureFlags.enablePhotoZoom = prefs.getBoolean("enablePhotoZoom", false);
        FeatureFlags.enablePostDownload = prefs.getBoolean("enablePostDownload", false);
        FeatureFlags.enableStoryDownload = prefs.getBoolean("enableStoryDownload", false);
        FeatureFlags.enableReelDownload = prefs.getBoolean("enableReelDownload", false);
        FeatureFlags.enableProfileDownload = prefs.getBoolean("enableProfileDownload", false);
        FeatureFlags.enableBatchDownload = prefs.getBoolean("enableBatchDownload", false);
        FeatureFlags.downloaderUsernameFolder = prefs.getBoolean("downloaderUsernameFolder", false);
        FeatureFlags.downloaderAddTimestamp   = prefs.getBoolean("downloaderAddTimestamp", false);
        FeatureFlags.downloaderCustomPath     = prefs.getString("downloaderCustomPath", "");
        FeatureFlags.downloaderCustomUri      = prefs.getString("downloaderCustomUri",  "");

        FeatureFlags.customThemeEnabled = prefs.getBoolean("customThemeEnabled", false);
        FeatureFlags.themePresetId = prefs.getInt("themePresetId", 1);
        FeatureFlags.themePaletteJson = prefs.getString("themePaletteJson", "");
        FeatureFlags.themeCustomPresetsJson = prefs.getString("themeCustomPresetsJson", "");

        FeatureManager.refreshFeatureStatus();
    }

    public static boolean syncFromCompanion(Context context, boolean force) {
        init(context);
        try {
            Bundle bundle = context.getContentResolver().call(SettingsProvider.URI, "getAll", null, null);
            if (bundle == null || bundle.isEmpty()) {
                overlayCompanionPrefs(context);
                return false;
            }

            long remoteUpdatedAt = CommonUtils.readUpdatedAt(
                    CommonUtils.readBundleValue(bundle, SettingsProvider.KEY_UPDATED_AT));
            long localUpdatedAt = prefs.getLong(SettingsProvider.KEY_UPDATED_AT, 0L);
            if (!force) {
                if (remoteUpdatedAt < localUpdatedAt) return false;
                if (remoteUpdatedAt == 0L && localUpdatedAt == 0L) {
                    boolean localHasData = false;
                    for (String key : prefs.getAll().keySet()) {
                        if (!SettingsProvider.KEY_UPDATED_AT.equals(key)) {
                            localHasData = true;
                            break;
                        }
                    }
                    if (localHasData) {
                        SharedPreferences.Editor editor = prefs.edit();
                        stampUpdatedAt(editor);
                        editor.commit();
                        pushSettingsToCompanion();
                        return false;
                    }
                }
            }

            SharedPreferences.Editor editor = prefs.edit();
            applyBundleToEditor(bundle, editor);
            if (remoteUpdatedAt > 0L) {
                editor.putLong(SettingsProvider.KEY_UPDATED_AT, remoteUpdatedAt);
            } else {
                stampUpdatedAt(editor);
            }
            editor.commit();
            return true;
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse) Failed to sync settings from companion: " + t.getMessage());
            overlayCompanionPrefs(context);
            return false;
        }
    }

    public static void pushSettingsToCompanion() {
        Context context = appContext;
        if (context == null) return;
        if (CommonUtils.MY_PACKAGE_NAME.equals(context.getPackageName())) return;

        try {
            Bundle bundle = prefsToBundle();
            if (bundle.isEmpty()) return;
            context.getContentResolver().call(SettingsProvider.URI, "putAll", null, bundle);

            Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_PREFS");
            reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
            reply.putExtras(bundle);
            context.sendBroadcast(reply);
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse) Failed to push settings to companion: " + t.getMessage());
        }
    }

    private static void overlayCompanionPrefs(Context context) {
        try {
            de.robv.android.xposed.XSharedPreferences cache =
                    new de.robv.android.xposed.XSharedPreferences(
                            CommonUtils.MY_PACKAGE_NAME, SettingsProvider.CACHE_PREFS);
            cache.reload();
            if (!cache.getAll().isEmpty()) mergeFrom(context, cache);
        } catch (Throwable ignored) {
        }
    }

    private static Bundle prefsToBundle() {
        Bundle bundle = new Bundle();
        if (prefs == null) return bundle;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            SettingsProvider.putValue(bundle, entry.getKey(), entry.getValue());
        }
        return bundle;
    }

    private static void applyBundleToEditor(Bundle bundle, SharedPreferences.Editor editor) {
        for (String key : bundle.keySet()) {
            SettingsProvider.putPref(editor, key, CommonUtils.readBundleValue(bundle, key));
        }
    }

    private static double readDoublePref(SharedPreferences p, String key, double fallback) {
        try {
            return Double.parseDouble(p.getString(key, String.valueOf(fallback)));
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
