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
        track("VoiceDownload", R.string.ig_dialog_downloader_voice, FeatureFlags.enableVoiceDownload);
        track("DisableDoubleTapLike", R.string.ig_dialog_misc_disable_double_tap_like, FeatureFlags.disableDoubleTapLike);
        track("CaptionCopy", R.string.ig_dialog_misc_copy_caption, FeatureFlags.enableCaptionCopy);
        track("CopyComment", R.string.ig_dialog_misc_copy_comment, FeatureFlags.enableCopyComment);
        track("PhotoZoom", R.string.ig_dialog_misc_photo_zoom, FeatureFlags.enablePhotoZoom);
        track("KeepUnsentMessages", R.string.ig_dialog_ghost_keep_unsent, FeatureFlags.keepUnsentMessages);
        track("RemoveMetaAI", R.string.ig_dialog_misc_remove_meta_ai, FeatureFlags.removeMetaAI);
        track("LockDirectMessages", R.string.ig_dialog_misc_lock_dms, FeatureFlags.lockDirectMessages);
        track("HideSpecificChats", R.string.ig_hide_chats_title, FeatureFlags.hideSpecificChats);
        track("ExpandCaptions", R.string.ig_dialog_clean_feed_expand_captions, FeatureFlags.expandCaptions);
        track("HideCommunityNotes", R.string.ig_dialog_clean_feed_hide_community_notes, FeatureFlags.hideCommunityNotes);
        track("FollowingOnlyFeed", R.string.ig_dialog_clean_feed_following_only, FeatureFlags.followingOnlyFeed);
        track("ConfirmFeedRefresh", R.string.ig_confirm_refresh, FeatureFlags.confirmFeedRefresh);
        track("HideLikedPosts", R.string.ig_dialog_clean_feed_hide_liked, FeatureFlags.hideLikedPosts);
        track("ProfileFollowsYou", R.string.ig_dialog_misc_profile_follows_label, FeatureFlags.profileFollowsYouLabel);
        track("LikeUndo", R.string.ig_dialog_misc_like_undo, FeatureFlags.likeUndo);
        track("ExactTimestamps", R.string.ig_dialog_misc_exact_timestamps, FeatureFlags.exactTimestamps);
        track("HideShareCreateGroup", R.string.ig_dialog_misc_hide_share_create_group, FeatureFlags.hideShareCreateGroup);
        track("OpenLinksExternally", R.string.ig_dialog_open_links_externally, FeatureFlags.openLinksExternally);
        track("CopyMediaLink", R.string.ig_copy_link_title, FeatureFlags.copyMediaLink);
        track("SaveInstants", R.string.ig_instant_save_title, FeatureFlags.saveInstants);
        track("UploadInstants", R.string.ig_instant_upload_title, FeatureFlags.uploadInstants);
        track("CacheStories", R.string.ig_story_cache_title, FeatureFlags.cacheStories);
        track("CustomFont", R.string.ig_custom_font_title, FeatureFlags.customFontEnabled);
        track("CustomEmoji", R.string.ig_custom_emoji_title, FeatureFlags.customEmojiEnabled);
        track("StartupTab", R.string.ig_dialog_extras_startup_tab, !FeatureFlags.startupTab.isEmpty());
        track("ChatExport", R.string.ig_chat_export, FeatureFlags.chatExport);
        track("FancyBioFont", R.string.ig_dialog_fancy_bio_font, FeatureFlags.fancyBioFont);
        track("EditHistory", R.string.ig_feat_edit_history, FeatureFlags.keepEditHistory);
        track("JumpToFirstMessage", R.string.ig_jump_first_message, FeatureFlags.jumpToFirstMessage);
        track("ChatTranslate", R.string.ig_dialog_translate_messages, FeatureFlags.translateMessages);
        track("BulkUnsend", R.string.ig_dialog_bulk_unsend, FeatureFlags.bulkUnsend);
        track("LockSavedCollections", R.string.ig_feat_lock_collections, FeatureFlags.lockSavedCollections);
        track("DisableRepost", R.string.ig_dialog_misc_disable_repost, FeatureFlags.disableRepost);
        track("CommentSearch", R.string.ig_feat_comment_search, FeatureFlags.commentSearch);
        track("StoryViewerSearch", R.string.ig_feat_viewer_search, FeatureFlags.storyViewerSearch);
        track("StoryLinks", R.string.ig_feat_story_links, FeatureFlags.storyLinks);
        track("StoryInfo", R.string.ig_feat_story_info, FeatureFlags.storyInfo);
        track("FollowListExport", R.string.ig_feat_follow_export, FeatureFlags.followListExport);
        track("FollowersHistory", R.string.ig_feat_followers_history, FeatureFlags.followersHistory);
        track("SavedOnProfile", R.string.ig_feat_saved_on_profile, FeatureFlags.savedOnProfile);
        track("HideNotesTray", R.string.ig_feat_hide_notes, FeatureFlags.hideNotesTray);
        track("HideDmSuggestions", R.string.ig_hide_dm_suggestions, FeatureFlags.hideDmSuggestions);
        track("ConfirmLikes", R.string.ig_confirm_likes, FeatureFlags.confirmLikes);
        track("ConfirmFollows", R.string.ig_confirm_follows, FeatureFlags.confirmFollows);
        track("ProfileMenuTools", R.string.ig_profile_menu_tools, FeatureFlags.profileMenuTools);
        track("PostCountSpoof", R.string.ig_post_counts_feature, FeatureFlags.postCountSpoof);
        track("ProfileSpoofer", R.string.ig_profile_spoofer, FeatureFlags.profileSpoofer);
        track("ProfileAnalyzer", R.string.ig_profile_analyzer, FeatureFlags.profileAnalyzer);
        track("HideInstants", R.string.ig_hide_instants, FeatureFlags.hideInstants);
        track("BackLeavesHome", R.string.ig_back_leaves_home, FeatureFlags.backLeavesHome);
        track("HideDmBlend", R.string.ig_feat_hide_blend, FeatureFlags.hideDmBlend);
        track("ReadReceiptChats", R.string.ig_feat_read_receipt_chats, FeatureFlags.readReceiptChats);
        track("HideThreadsButton", R.string.ig_feat_hide_threads, FeatureFlags.hideThreadsButton);
        track("PlaybackSpeed", R.string.ig_playback_speed_title, FeatureFlags.offerPlaybackSpeed);
        track("HoldSlowMotion", R.string.ig_dialog_reels_hold_left_slow, FeatureFlags.holdLeftSlowMotion);
        track("StickerDownload", R.string.ig_feat_sticker_download, FeatureFlags.enableStickerDownload);
        track("StoryRingColor", R.string.ig_story_ring_color, !FeatureFlags.storyRingColor.isEmpty());
        track("RoundedFeedPosts", R.string.ig_feed_corner_radius, FeatureFlags.feedCornerRadius > 0);
        track("DisableSearchHistory", R.string.ig_disable_search_history, FeatureFlags.disableSearchHistory);
        track("ConfirmCalls", R.string.ig_call_confirm, FeatureFlags.confirmCalls);
        track("DmMediaDownload", R.string.ig_dialog_downloader_dm_media, FeatureFlags.enableDmMediaDownload);
        track("MaxUploadQuality", R.string.ig_feat_max_upload_quality, FeatureFlags.maxUploadQuality);
        track("BlockAnalytics", R.string.ig_dialog_ad_block_analytics, FeatureFlags.isAnalyticsBlocked);
        track("HidePlusUpsells", R.string.ig_dialog_clean_feed_hide_plus, FeatureFlags.hidePlusUpsells);
        track("DisableVideoAutoPlay", R.string.ig_dialog_misc_disable_video_autoplay, FeatureFlags.disableVideoAutoPlay);
        track("ForceReelScrubber", R.string.ig_dialog_downloader_reel_scrubber, FeatureFlags.forceReelScrubber);
        track("DisableStoryFlipping", R.string.ig_dialog_misc_disable_story_autoswipe, FeatureFlags.disableStoryFlipping);
        track("LockWholeApp", R.string.ig_dialog_misc_lock_whole_app, FeatureFlags.lockWholeApp);
        track("AudioDownload", R.string.ig_dialog_downloader_offer_audio, FeatureFlags.offerAudioDownload);
        track("CopyImage", R.string.ig_dialog_downloader_offer_copy_image, FeatureFlags.offerCopyImage);
        track("MediaInfo", R.string.ig_dialog_downloader_offer_media_info, FeatureFlags.offerMediaInfo);
        track("SpoofDevice", R.string.ig_spoof_device, !FeatureFlags.spoofDevice.isEmpty());
        track("StoryTraySize", R.string.ig_story_tray_size, FeatureFlags.storyTrayScale < 100);
        track("AutoClearCache", R.string.ig_dialog_auto_clear_cache, FeatureFlags.autoClearCache);
        track("HideNavTabs", R.string.menu_nav_tabs, FeatureFlags.hideTabHome || FeatureFlags.hideTabReels || FeatureFlags.hideTabCreate || FeatureFlags.hideTabMessages || FeatureFlags.hideTabActivity || FeatureFlags.hideTabProfile);
        track("DisableStoriesSection", R.string.ig_dialog_distraction_disable_stories, FeatureFlags.disableStories);
        track("DisableFeedSection", R.string.ig_dialog_distraction_disable_feed, FeatureFlags.disableFeed);
        track("DisableReelsSection", R.string.ig_dialog_distraction_disable_reels, FeatureFlags.disableReels || FeatureFlags.disableReelsExceptDM);
        track("DisableExploreSection", R.string.ig_dialog_distraction_disable_explore, FeatureFlags.disableExplore);
        track("DisableCommentsSection", R.string.ig_dialog_distraction_disable_comments, FeatureFlags.disableComments);
        for (ExtrasFeatures.Entry e : ExtrasFeatures.ALL) {
            track(e.flag, e.label, ExtrasFeatures.get(e.flag));
        }
    }

    private static void track(String name, int labelResId, boolean enabled) {
        if (enabled) {
            FeatureStatusTracker.setEnabled(name, labelResId);
        } else {
            FeatureStatusTracker.setDisabled(name, labelResId);
        }
    }
}
