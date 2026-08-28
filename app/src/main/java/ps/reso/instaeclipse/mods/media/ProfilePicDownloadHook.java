package ps.reso.instaeclipse.mods.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Profile Picture Downloader
 *
 * Strategy:
 *   Hook View.onAttachedToWindow() globally, filter for "expanded_profile_pic" by resource name
 *   (cached as an int ID after first resolution). When found, attach a long-press listener that
 *   reads the ImageUrl field (getUrl()) from IgImageView and downloads via FeedVideoDownloadHook helpers.
 *
 * Gated by FeatureFlags.enableProfileDownload.
 */
public class ProfilePicDownloadHook {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String  HOOKED_TAG  = "ie_profile_dl";

    /** Cached resource ID for "expanded_profile_pic"; 0 = not yet resolved. */
    private static volatile int expandedPicViewId = 0;

    /** Guards against a second long-press queuing a duplicate download of the same pic. */
    private static final Set<String> profileDownloadInFlight =
            Collections.synchronizedSet(new HashSet<>());

    // ── Discoverable "Download" row in the interaction bar ──────────────────────
    private static final String DOWNLOAD_ITEM_TAG = "ie_profile_dl_btn";
    private static volatile WeakReference<View> activeProfilePic;

    // ── Install ───────────────────────────────────────────────────────────────

    public static void install() {
        // Mark status before hook setup so the toast shows correctly
        if (FeatureFlags.enableProfileDownload) {
            FeatureStatusTracker.setEnabled("ProfileDownload", R.string.ig_dialog_downloader_profiles);
            FeatureStatusTracker.setHooked("ProfileDownload");
        }

        // Hook View.onAttachedToWindow — fires once per view attachment, works for any
        // window type (Activity, Dialog, BottomSheet) without relying on layout listeners.
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableProfileDownload) return;
                View v = (View) param.thisObject;
                int vid = v.getId();
                if (vid == View.NO_ID) return;

                // Fast path: cached int comparison (only resolves resource name once)
                if (expandedPicViewId != 0) {
                    if (vid != expandedPicViewId) return;
                } else {
                    try {
                        String name = v.getResources().getResourceEntryName(vid);
                        if (!"expanded_profile_pic".equals(name)) return;
                        expandedPicViewId = vid;
                    } catch (Throwable ignored) { return; }
                }

                activeProfilePic = new WeakReference<>(v);
                injectLongPress(v);
                scheduleInteractionBarButton(v);
            }
        });

        XposedHelpers.findAndHookMethod(View.class, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                View v = (View) param.thisObject;
                if (!isExpandedProfilePic(v)) return;
                WeakReference<View> active = activeProfilePic;
                if (active != null && active.get() == v) activeProfilePic = null;
            }
        });
    }

    /** Fast-path check reusing the same cached resource id as the onAttachedToWindow hook. */
    private static boolean isExpandedProfilePic(View v) {
        int vid = v.getId();
        if (vid == View.NO_ID) return false;
        if (expandedPicViewId != 0) return vid == expandedPicViewId;
        try {
            String name = v.getResources().getResourceEntryName(vid);
            if (!"expanded_profile_pic".equals(name)) return false;
            expandedPicViewId = vid;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── UI injection ──────────────────────────────────────────────────────────

    private static void injectLongPress(View view) {
        try {
            view.setTag(HOOKED_TAG);
            view.setOnLongClickListener(v -> {
                triggerDownload(v);
                return true;
            });
        } catch (Throwable t) {
            ModuleLog.line("(IE|ProfileDL) ❌ injectLongPress: " + t.getMessage());
        }
    }

    // ── Discoverable "Download" row in the interaction bar ──────────────────────
    //
    // Structural detection only (no obfuscated class/method names): the interaction bar
    // is identified purely by shape — a visible ViewGroup with >= 2 children that each
    // contain a "profile_interaction_item_icon"-id view. This stays version-agnostic,
    // unlike hooking the (obfuscated, version-specific) builder class directly.
    //
    // Deliberately NOT hooking ViewGroup.addView here (one of the hottest methods in the
    // app, fires on every view added anywhere) — it reproducibly crashed ART's
    // Concurrent-Mark-Compact GC on at least one Android 16 device. scheduleInjectOnBar's
    // own postDelayed retries (below) already cover the "bar renders after the profile pic
    // attaches" case without needing a second hook.

    private static View resolveActiveProfilePic(View near) {
        WeakReference<View> cachedRef = activeProfilePic;
        View cached = cachedRef != null ? cachedRef.get() : null;
        if (cached != null) return cached;
        if (near == null) return null;
        View root = near.getRootView();
        if (root == null) root = near;
        return findExpandedProfilePicInTree(root);
    }

    private static void scheduleInteractionBarButton(View profilePicView) {
        scheduleInjectOnBar(null, profilePicView);
    }

    private static void scheduleInjectOnBar(ViewGroup knownBar, View profilePicView) {
        Runnable inject = () -> tryInject(knownBar, profilePicView);
        if (knownBar != null) knownBar.post(inject);
        profilePicView.post(inject);
        mainHandler.postDelayed(inject, 80);
        mainHandler.postDelayed(inject, 250);
        mainHandler.postDelayed(inject, 600);
        mainHandler.postDelayed(inject, 1200);
    }

    private static void tryInject(ViewGroup knownBar, View profilePicView) {
        if (!FeatureFlags.enableProfileDownload) return;
        ViewGroup bar = knownBar != null ? knownBar : findInteractionBar(profilePicView);
        if (bar == null) return;
        injectDownloadButton(bar, profilePicView);
    }

    @SuppressLint("DiscouragedApi")
    private static ViewGroup findInteractionBar(View profilePicView) {
        View root = profilePicView.getRootView();
        if (root == null) return null;
        Context ctx = profilePicView.getContext();

        int barId = ctx.getResources().getIdentifier("profile_share_card", "id", ctx.getPackageName());
        if (barId != 0) {
            View found = root.findViewById(barId);
            if (found instanceof ViewGroup group && isInteractionBar(group, ctx)) return group;
        }

        int iconId = ctx.getResources().getIdentifier("profile_interaction_item_icon", "id", ctx.getPackageName());
        if (iconId == 0) return null;
        return findInteractionBarRecursive(root);
    }

    private static ViewGroup findInteractionBarRecursive(View node) {
        if (node instanceof ViewGroup group) {
            if (isInteractionBar(group, node.getContext())) return group;
            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup found = findInteractionBarRecursive(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressLint("DiscouragedApi")
    private static boolean isInteractionBar(ViewGroup group, Context ctx) {
        if (group.getVisibility() != View.VISIBLE) return false;
        int iconId = ctx.getResources().getIdentifier("profile_interaction_item_icon", "id", ctx.getPackageName());
        if (iconId == 0) return false;

        int items = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup vg && vg.findViewById(iconId) != null) items++;
        }
        return items >= 2;
    }

    private static View findExpandedProfilePicInTree(View root) {
        if (isExpandedProfilePic(root)) return root;
        if (!(root instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findExpandedProfilePicInTree(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void injectDownloadButton(ViewGroup bar, View profilePicView) {
        if (bar.findViewWithTag(DOWNLOAD_ITEM_TAG) != null) return;

        Context ctx = bar.getContext();
        View item = inflateInteractionItem(ctx, bar);
        if (item == null) return;

        item.setTag(DOWNLOAD_ITEM_TAG);
        String label = I18n.t(ctx, R.string.ig_profile_download_button);
        bindInteractionItem(item, ctx, label, android.R.drawable.stat_sys_download);
        item.setOnClickListener(v -> triggerDownload(profilePicView));
        item.setLayoutParams(copyInteractionItemLayoutParams(bar, ctx));

        int insertIndex = findInsertIndexBeforeCopyLink(bar, ctx);
        if (insertIndex < 0 || insertIndex > bar.getChildCount()) {
            bar.addView(createBarSpacer(ctx));
            bar.addView(item);
        } else {
            bar.addView(createBarSpacer(ctx), insertIndex);
            bar.addView(item, insertIndex + 1);
        }
        bar.requestLayout();
        ModuleLog.line("(IE|ProfileDL) download button injected, children=" + bar.getChildCount());
    }

    private static Space createBarSpacer(Context ctx) {
        Space space = new Space(ctx);
        space.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        return space;
    }

    @SuppressLint("DiscouragedApi")
    private static LinearLayout.LayoutParams copyInteractionItemLayoutParams(ViewGroup bar, Context ctx) {
        int iconId = ctx.getResources().getIdentifier("profile_interaction_item_icon", "id", ctx.getPackageName());
        if (iconId != 0) {
            for (int i = 0; i < bar.getChildCount(); i++) {
                View child = bar.getChildAt(i);
                if (!(child instanceof ViewGroup vg) || vg.findViewById(iconId) == null) continue;
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams llp) {
                    LinearLayout.LayoutParams copy = new LinearLayout.LayoutParams(llp);
                    copy.weight = 0f;
                    return copy;
                }
            }
        }
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @SuppressLint("DiscouragedApi")
    private static View inflateInteractionItem(Context ctx, ViewGroup parent) {
        int layoutId = ctx.getResources().getIdentifier(
                "layout_expanded_profile_picture_interaction_bar_item_view", "layout", ctx.getPackageName());
        if (layoutId != 0) {
            try {
                return LayoutInflater.from(ctx).inflate(layoutId, parent, false);
            } catch (Throwable ignored) {}
        }

        LinearLayout fallback = new LinearLayout(ctx);
        fallback.setOrientation(LinearLayout.VERTICAL);
        fallback.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(ctx);
        icon.setTag("ie_icon");
        TextView text = new TextView(ctx);
        text.setTag("ie_label");
        text.setTextColor(Color.WHITE);
        text.setTextSize(11f);
        text.setGravity(Gravity.CENTER);
        fallback.addView(icon);
        fallback.addView(text);
        return fallback;
    }

    @SuppressLint("DiscouragedApi")
    private static void bindInteractionItem(View item, Context ctx, String label, int iconRes) {
        int iconId = ctx.getResources().getIdentifier("profile_interaction_item_icon", "id", ctx.getPackageName());
        int labelId = ctx.getResources().getIdentifier("profile_interaction_item_label", "id", ctx.getPackageName());

        ImageView icon = iconId != 0 ? item.findViewById(iconId) : null;
        TextView text = labelId != 0 ? item.findViewById(labelId) : null;
        if (icon == null) icon = item.findViewWithTag("ie_icon");
        if (text == null) text = item.findViewWithTag("ie_label");

        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(Color.WHITE);
            icon.setContentDescription(label);
        }
        if (text != null) {
            text.setText(label);
            text.setMinLines(2);
        }
        item.setContentDescription(label);
    }

    @SuppressLint("DiscouragedApi")
    private static int findInsertIndexBeforeCopyLink(ViewGroup bar, Context ctx) {
        int labelId = ctx.getResources().getIdentifier("profile_interaction_item_label", "id", ctx.getPackageName());
        String copyLinkHint = resolveCopyLinkLabel(ctx);

        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            TextView label = labelId != 0 ? child.findViewById(labelId) : null;
            if (label == null) continue;
            CharSequence text = label.getText();
            if (matchesCopyLink(text != null ? text.toString() : "", copyLinkHint)) return i;
        }
        return -1;
    }

    @SuppressLint("DiscouragedApi")
    private static String resolveCopyLinkLabel(Context ctx) {
        int strId = ctx.getResources().getIdentifier("copy_link", "string", ctx.getPackageName());
        if (strId != 0) {
            try {
                return ctx.getString(strId);
            } catch (Throwable ignored) {}
        }
        return "Copy link";
    }

    private static boolean matchesCopyLink(String label, String copyLinkHint) {
        if (label == null || label.isEmpty()) return false;
        String lower = label.toLowerCase(Locale.US);
        if (copyLinkHint != null && !copyLinkHint.isEmpty()
                && lower.equals(copyLinkHint.toLowerCase(Locale.US))) return true;
        return lower.contains("copy") && lower.contains("link");
    }

    /** Shared trigger for both the long-press gesture and the interaction-bar row click. */
    private static void triggerDownload(View profilePicView) {
        Context ctx = profilePicView.getContext();
        Activity activity = activityFromContext(ctx);

        String url = extractUrl(profilePicView);
        if (url == null) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_profile_pic_url_not_found), Toast.LENGTH_SHORT).show();
            ModuleLog.line("(IE|ProfileDL) ❌ URL extraction failed");
            return;
        }
        String username = activity != null ? extractUsername(activity) : null;
        String stableId = (username != null && !username.isEmpty())
                ? username : Integer.toHexString(url.hashCode());

        if (FeedVideoDownloadHook.isProfileDownloaded(ctx, username, stableId)) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_profile_pic_already_saved), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!profileDownloadInFlight.add(stableId)) return;

        String filename = FeedVideoDownloadHook.buildFilename(username, "profile", stableId, false);
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading_profile_pic), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(ctx, url, filename, false, username);
                if (!delegated) {
                    mainHandler.post(() -> Toast.makeText(ctx,
                            I18n.t(ctx, R.string.ig_toast_profile_pic_saved), Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                ModuleLog.line("(IE|ProfileDL) ❌ download: " + e.getMessage());
                mainHandler.post(() -> Toast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            } finally {
                profileDownloadInFlight.remove(stableId);
            }
        }).start();
    }

    // ── URL extraction ────────────────────────────────────────────────────────

    /**
     * Extracts the image URL from the profile pic view (CircularImageView extends IgImageView).
     * Scans known ImageUrl-typed fields by name; tries multiple candidates in order.
     */
    private static String extractUrl(View view) {
        for (String fieldName : new String[]{"A0E", "A0D", "A0c"}) {
            try {
                String url = getUrlFromImageUrlField(view, fieldName);
                if (url != null) return url;
            } catch (Throwable ignored) {}
        }

        // Fallback: tag-based URI
        try {
            Object tag = view.getTag();
            if (tag instanceof Uri) return tag.toString();
            if (tag instanceof String s && s.startsWith("http")) return s;
        } catch (Throwable ignored) {}

        ModuleLog.line("(IE|ProfileDL) ❌ all URL strategies failed for " + view.getClass().getName());
        return null;
    }

    /**
     * Walks the class hierarchy to find a field by name, reads it as an ImageUrl,
     * then calls getUrl() on it (ImageUrl is a non-obfuscated interface).
     */
    private static String getUrlFromImageUrlField(View view, String fieldName) throws Throwable {
        Class<?> cls = view.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object imageUrl = f.get(view);
                if (imageUrl == null) return null;
                java.lang.reflect.Method getUrl = imageUrl.getClass().getMethod("getUrl");
                Object result = getUrl.invoke(imageUrl);
                if (result instanceof String s && s.startsWith("http")) return s;
                return null;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    // ── Username extraction ───────────────────────────────────────────────────

    @SuppressLint("DiscouragedApi")
    private static String extractUsername(Activity activity) {
        try {
            android.app.ActionBar ab = activity.getActionBar();
            if (ab != null && ab.getTitle() != null) {
                String t = ab.getTitle().toString().trim();
                if (looksLikeUsername(t)) return t;
            }
        } catch (Throwable ignored) {}

        try {
            int titleId = activity.getResources()
                    .getIdentifier("action_bar_title", "id", activity.getPackageName());
            if (titleId != 0) {
                android.widget.TextView tv = activity.findViewById(titleId);
                if (tv != null) {
                    String t = tv.getText().toString().trim();
                    if (looksLikeUsername(t)) return t;
                }
            }
        } catch (Throwable ignored) {}

        try {
            CharSequence t = activity.getTitle();
            if (t != null && looksLikeUsername(t.toString().trim())) return t.toString().trim();
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean looksLikeUsername(String s) {
        return s != null && s.length() >= 1 && s.length() <= 30
                && s.matches("[a-zA-Z0-9._]+")
                && !s.matches("\\d+");
    }

    // ── Context → Activity ────────────────────────────────────────────────────

    private static Activity activityFromContext(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private static void downloadToStream(String url, OutputStream out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
        conn.connect();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }
}
