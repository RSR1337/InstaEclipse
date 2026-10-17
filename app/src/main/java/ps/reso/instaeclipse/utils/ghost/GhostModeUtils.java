package ps.reso.instaeclipse.utils.ghost;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.i18n.I18n;

public class GhostModeUtils {
    public static boolean isGhostModeActive() {
        if (FeatureFlags.quickToggleSeen && FeatureFlags.isGhostSeen) return true;
        if (FeatureFlags.quickToggleTyping && FeatureFlags.isGhostTyping) return true;
        if (FeatureFlags.quickToggleScreenshot && FeatureFlags.isGhostScreenshot) return true;
        if (FeatureFlags.quickToggleViewOnce && FeatureFlags.isGhostViewOnce) return true;
        if (FeatureFlags.quickToggleStory && FeatureFlags.isGhostStory) return true;
        if (FeatureFlags.quickToggleLive && FeatureFlags.isGhostLive) return true;
        if (FeatureFlags.quickToggleEphemeral && FeatureFlags.keepEphemeralMessages) return true;
        if (FeatureFlags.quickToggleReplays && FeatureFlags.enableUnlimitedReplays) return true;
        if (FeatureFlags.quickTogglePermanentView && FeatureFlags.permanentViewMode) return true;
        return FeatureFlags.quickToggleAllowScreenshots && FeatureFlags.allowScreenshots;
    }

    public static boolean hasQuickToggleSelection() {
        return FeatureFlags.quickToggleSeen || FeatureFlags.quickToggleTyping
                || FeatureFlags.quickToggleScreenshot || FeatureFlags.quickToggleViewOnce
                || FeatureFlags.quickToggleStory || FeatureFlags.quickToggleLive
                || FeatureFlags.quickToggleEphemeral || FeatureFlags.quickToggleReplays
                || FeatureFlags.quickTogglePermanentView || FeatureFlags.quickToggleAllowScreenshots;
    }

    public static void toggleSelectedGhostOptions(Context context) {
        if (!hasQuickToggleSelection()) {
            Activity activity = UIHookManager.getCurrentActivity();
            if (activity != null) {
                GhostEmojiManager.addGhostEmojiNextToInbox(activity, false);
            }
            Toast.makeText(context, "❗ " + I18n.t(context, R.string.ig_toast_ghost_no_options), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean newState = !isGhostModeActive();

        if (FeatureFlags.quickToggleSeen) FeatureFlags.isGhostSeen = newState;
        if (FeatureFlags.quickToggleTyping) FeatureFlags.isGhostTyping = newState;
        if (FeatureFlags.quickToggleScreenshot) FeatureFlags.isGhostScreenshot = newState;
        if (FeatureFlags.quickToggleViewOnce) FeatureFlags.isGhostViewOnce = newState;
        if (FeatureFlags.quickToggleStory) FeatureFlags.isGhostStory = newState;
        if (FeatureFlags.quickToggleLive) FeatureFlags.isGhostLive = newState;
        if (FeatureFlags.quickToggleEphemeral) FeatureFlags.keepEphemeralMessages = newState;
        if (FeatureFlags.quickToggleReplays) FeatureFlags.enableUnlimitedReplays = newState;
        if (FeatureFlags.quickTogglePermanentView) FeatureFlags.permanentViewMode = newState;
        if (FeatureFlags.quickToggleAllowScreenshots) FeatureFlags.allowScreenshots = newState;

        SettingsManager.saveAllFlags();

        Activity activity = UIHookManager.getCurrentActivity();
        if (activity != null) {
            GhostEmojiManager.addGhostEmojiNextToInbox(activity, newState);
        }

        if (newState) {
            Toast.makeText(context, "👻 " + I18n.t(context, R.string.ig_toast_ghost_enabled), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "❌ " + I18n.t(context, R.string.ig_toast_ghost_disabled), Toast.LENGTH_SHORT).show();
        }
    }
}
