package ps.reso.instaeclipse.mods.ads;

import android.content.ClipData;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;

public class TrackingLinkDisable {
    public void disableTrackingLinks(ClassLoader classLoader) throws Throwable {
        FeatureStatusTracker.setHooked("DisableTrackingLinks");
        Class<?> clipboardManagerClass = XposedHelpers.findClass("android.content.ClipboardManager", classLoader);
        XposedHelpers.findAndHookMethod(clipboardManagerClass, "setPrimaryClip",
                Class.forName("android.content.ClipData"), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!FeatureFlags.disableTrackingLinks) return;

                        ClipData clipData = (ClipData) param.args[0];
                        if (clipData == null || clipData.getItemCount() == 0) return;

                        ClipData.Item item = clipData.getItemAt(0);
                        if (item == null || item.getText() == null) return;

                        String url = item.getText().toString();
                        if (!url.contains("https://www.instagram.com/")) return;

                        boolean hasTracking = url.contains("igsh=")
                                || url.contains("igshid=")
                                || url.contains("ig_rid=")
                                || url.contains("ig_mid=")
                                || url.contains("utm_source=")
                                || url.contains("utm_medium=")
                                || url.contains("share_id=")
                                || url.contains("story_media_id=")
                                || url.matches("(?i).*saved[-_]by.*");

                        if (hasTracking) {
                            param.args[0] = ClipData.newPlainText("URL", url.replaceAll("\\?.*", ""));
                        }
                    }
                });
    }
}
