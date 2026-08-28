package ps.reso.instaeclipse.utils.feature;

import ps.reso.instaeclipse.R;

public class FeatureManager {

    public static void refreshFeatureStatus() {
        track("DevOptions", R.string.ig_dialog_section_dev_options, FeatureFlags.isDevEnabled);
        track("GhostSeen", R.string.ig_dialog_ghost_hide_dm_seen, FeatureFlags.isGhostSeen);
        track("GhostTyping", R.string.ig_dialog_ghost_hide_typing, FeatureFlags.isGhostTyping);
        track("GhostScreenshot", R.string.ig_dialog_ghost_bypass_screenshot, FeatureFlags.isGhostScreenshot);
        track("GhostViewOnce", R.string.ig_dialog_ghost_hide_view_once, FeatureFlags.isGhostViewOnce);
        track("UnlimitedReplays", R.string.ig_dialog_ghost_unlimited_replays, FeatureFlags.enableUnlimitedReplays);
        track("GhostStories", R.string.ig_dialog_ghost_hide_story_views, FeatureFlags.isGhostStory);
        track("GhostLive", R.string.ig_dialog_ghost_hide_live_presence, FeatureFlags.isGhostLive);
        track("AllowScreenshots", R.string.ig_dialog_ghost_allow_screenshots_dms, FeatureFlags.allowScreenshots);
        track("KeepEphemeralMessages", R.string.ig_dialog_ghost_keep_disappearing, FeatureFlags.keepEphemeralMessages);
        track("PermanentViewMode", R.string.ig_dialog_ghost_permanent_view_once, FeatureFlags.permanentViewMode);
        track("HideSuggestionsInFeed", R.string.ig_dialog_clean_feed_hide_suggested, FeatureFlags.hideSuggestionsInFeed);
        track("HideThreadsSuggestions", R.string.ig_dialog_clean_feed_hide_threads, FeatureFlags.hideThreadsSuggestions);
        track("AdBlocker", R.string.ig_dialog_ad_block_ads, FeatureFlags.isAdBlockEnabled);
        track("DisableTrackingLinks", R.string.ig_dialog_ad_disable_tracking, FeatureFlags.disableTrackingLinks);
        track("FollowerToast", R.string.ig_dialog_misc_show_follower_toast, FeatureFlags.showFollowerToast);
        track("StoryMentions", R.string.ig_dialog_misc_view_story_mentions, FeatureFlags.enableStoryMentions);
        track("DisableDiscoverPeople", R.string.ig_dialog_misc_disable_discover_people, FeatureFlags.disableDiscoverPeople);
        track("ForceReelQuality", R.string.ig_dialog_quality_force_reels, FeatureFlags.forceReelQuality > 0);
        track("SpoofLocation", R.string.ig_dialog_location_spoof_enable, FeatureFlags.spoofLocation);
        track("SpoofLastSeen", R.string.ig_dialog_misc_spoof_last_seen, FeatureFlags.spoofLastSeen);
        track("CustomTheme", R.string.theme_title, FeatureFlags.customThemeEnabled);
        track("RemoveBuildExpiredPopup", R.string.ig_dialog_dev_remove_build_expired, FeatureFlags.removeBuildExpiredPopup);
        track("PostDownload", R.string.ig_dialog_downloader_posts, FeatureFlags.enablePostDownload);
        track("StoryDownload", R.string.ig_dialog_downloader_stories, FeatureFlags.enableStoryDownload);
        track("ReelDownload", R.string.ig_dialog_downloader_reels, FeatureFlags.enableReelDownload);
        track("ProfileDownload", R.string.ig_dialog_downloader_profiles, FeatureFlags.enableProfileDownload);
        track("DisableDoubleTapLike", R.string.ig_dialog_misc_disable_double_tap_like, FeatureFlags.disableDoubleTapLike);
        track("CaptionCopy", R.string.ig_dialog_misc_copy_caption, FeatureFlags.enableCaptionCopy);
        track("CopyComment", R.string.ig_dialog_misc_copy_comment, FeatureFlags.enableCopyComment);
        track("PhotoZoom", R.string.ig_dialog_misc_photo_zoom, FeatureFlags.enablePhotoZoom);
    }

    private static void track(String name, int labelResId, boolean enabled) {
        if (enabled) {
            FeatureStatusTracker.setEnabled(name, labelResId);
        } else {
            FeatureStatusTracker.setDisabled(name, labelResId);
        }
    }
}
