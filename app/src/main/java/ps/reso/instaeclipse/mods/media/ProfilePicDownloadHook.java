package ps.reso.instaeclipse.mods.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import ps.reso.instaeclipse.xposedcompat.XC_MethodHook;
import ps.reso.instaeclipse.xposedcompat.XposedBridge;
import ps.reso.instaeclipse.xposedcompat.XposedHelpers;
import ps.reso.instaeclipse.mods.feed.ZoomableImageView;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.DexKitQueryCache;
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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class ProfilePicDownloadHook {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String  HOOKED_TAG  = "ie_profile_dl";

    private static volatile int expandedPicViewId = 0;

    private static final Set<String> profileDownloadInFlight =
            Collections.synchronizedSet(new HashSet<>());

    private static final String DOWNLOAD_ITEM_TAG = "ie_profile_dl_btn";

    public static void install() {
        if (FeatureFlags.enableProfileDownload) {
            FeatureStatusTracker.setEnabled("ProfileDownload", R.string.ig_dialog_downloader_profiles);
            FeatureStatusTracker.setHooked("ProfileDownload");
        }

        ViewAttachDispatcher.add(new ViewAttachDispatcher.Listener() {
            @Override
            public void onViewAttached(View v) {
                if (!FeatureFlags.enableProfileDownload) return;
                int vid = v.getId();
                if (vid == View.NO_ID) return;

                if (expandedPicViewId != 0) {
                    if (vid != expandedPicViewId) return;
                } else {
                    try {
                        String name = v.getResources().getResourceEntryName(vid);
                        if (!"expanded_profile_pic".equals(name)) return;
                        expandedPicViewId = vid;
                    } catch (Throwable ignored) { return; }
                }

                injectLongPress(v);
                scheduleInteractionBarButton(v);
            }
        });
    }

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
                // Resolve activity lazily at tap time — context is valid at this point
                Context ctx = v.getContext();
                Activity activity = activityFromContext(ctx);

                String url = extractUrl(v);
                if (url == null) {
                    ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_profile_pic_url_not_found), Toast.LENGTH_SHORT).show();
                    ModuleLog.line("(IE|ProfileDL) ❌ URL extraction failed");
                    return true;
                }
                String username = activity != null ? extractUsername(activity) : null;
                String hd = hdUrl(url);
                ModuleLog.line("(IE|ProfileDL) viewer: " + (hd != null ? "HD original" : "displayed copy"));
                showViewer(ctx, hd != null ? hd : url, username);
                return true;
            });

        } catch (Throwable t) {
            ModuleLog.line("(IE|ProfileDL) ❌ injectLongPress: " + t.getMessage());
        }
    }

    private static View resolveActiveProfilePic(View near) {
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

    private static void triggerDownload(View profilePicView) {
        Context ctx = profilePicView.getContext();
        Activity activity = activityFromContext(ctx);

        String rawUrl = extractUrl(profilePicView);
        if (rawUrl == null) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_profile_pic_url_not_found), Toast.LENGTH_SHORT).show();
            ModuleLog.line("(IE|ProfileDL) ❌ URL extraction failed");
            return;
        }
        String hd = hdUrl(rawUrl);
        String url = hd != null ? hd : rawUrl;
        String username = activity != null ? extractUsername(activity) : null;
        String stableId = (username != null && !username.isEmpty())
                ? username : Integer.toHexString(rawUrl.hashCode());

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

    /**
     * Extracts the image URL from the profile pic view (CircularImageView extends IgImageView).
     * IgImageView keeps the non-obfuscated getter getTypedUrl() (the displayed ImageUrl); if a
     * build drops it, scans the hierarchy for ImageUrl-typed fields (A0E/A0D/A0e on 452) by TYPE,
     * never by the obfuscated field letter. ImageUrl itself is a non-obfuscated interface.
     */
    private static String extractUrl(View view) {
        try {
            java.lang.reflect.Method getter = findMethod(view.getClass(), "getTypedUrl");
            if (getter != null) {
                String url = urlOf(getter.invoke(view));
                if (url != null) return url;
            }
        } catch (Throwable ignored) {}

        try {
            String url = getUrlFromImageUrlFields(view);
            if (url != null) return url;
        } catch (Throwable ignored) {}

        // Fallback: tag-based URI
        try {
            Object tag = view.getTag();
            if (tag instanceof Uri) return tag.toString();
            if (tag instanceof String s && s.startsWith("http")) return s;
        } catch (Throwable ignored) {}

        ModuleLog.line("(IE|ProfileDL) ❌ all URL strategies failed for " + view.getClass().getName());
        return null;
    }

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

    private static Activity activityFromContext(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }


    // ── Install ───────────────────────────────────────────────────────────────

    private static final String FRAGMENT = "com.instagram.profile.fragment.UserDetailFragment";
    private static final String VIEW_MODEL = "com.instagram.profile.fragment.UserDetailViewModel";
    private static final String USER = "com.instagram.user.model.User";
    private static volatile Method hdGetter;
    private static volatile WeakReference<Object> lastProfile;

    private static void resolveHdGetter(DexKitBridge bridge, ClassLoader cl) {
        try {
            Method m = DexKitCache.isCacheValid() ? DexKitCache.loadMethod("ProfilePic_hdGetter", cl) : null;
            if (m == null) {
                List<MethodData> found = DexKitQueryCache.findMethod(bridge, FindMethod.create().matcher(MethodMatcher.create()
                        .declaredClass(USER)
                        .usingEqStrings(List.of("hd_profile_pic_url_info"))
                        .returnType("com.instagram.api.schemas.ProfilePicUrlInfo")
                        .paramCount(0)));
                if (found.size() != 1) {
                    ModuleLog.line("(IE|ProfileDL) ⚠️ hd_profile_pic_url_info getter: " + found.size() + " matches");
                    return;
                }
                m = found.get(0).getMethodInstance(cl);
                DexKitCache.saveMethod("ProfilePic_hdGetter", m);
            }
            m.setAccessible(true);
            hdGetter = m;
            ModuleLog.line("(IE|ProfileDL) ✅ HD getter User." + m.getName());
        } catch (Throwable t) {
            ModuleLog.line("(IE|ProfileDL) ⚠️ HD getter: " + t);
        }
    }

    /** The viewed profile's original picture when it is the same image as displayedUrl, else null. */
    private static String hdUrl(String displayedUrl) {
        try {
            Method getter = hdGetter;
            WeakReference<Object> ref = lastProfile;
            Object frag = ref != null ? ref.get() : null;
            if (getter == null || frag == null) return null;
            Object vm = fieldOfType(frag, VIEW_MODEL);
            Object user = vm != null ? fieldOfType(vm, USER) : null;
            Object info = user != null ? getter.invoke(user) : null;
            if (info == null) return null;
            Object url = info.getClass().getMethod("getUrl").invoke(info);
            if (!(url instanceof String hd) || !hd.startsWith("http")) return null;
            if (!fileName(hd).equals(fileName(displayedUrl))) {
                ModuleLog.line("(IE|ProfileDL) HD picture belongs to another profile, keeping the displayed one");
                return null;
            }
            return hd;
        } catch (Throwable t) {
            ModuleLog.line("(IE|ProfileDL) ⚠️ HD url: " + t);
            return null;
        }
    }

    private static String fileName(String url) {
        String path = Uri.parse(url).getPath();
        return path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
    }

    private static Object fieldOfType(Object o, String type) throws IllegalAccessException {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(c)) {
                if (!f.getType().getName().equals(type)) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v != null) return v;
            }
        }
        return null;
    }

    // ── Viewer ────────────────────────────────────────────────────────────────

    /** Full-screen square picture (pinch / double-tap to zoom, tap to close) with a Download pill. */
    private static void showViewer(Context ctx, String url, String username) {
        android.app.Dialog dialog = new android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.BLACK);

        ZoomableImageView image = new ZoomableImageView(ctx);
        image.setOnDismissRequest(dialog::dismiss);
        root.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar spinner = new ProgressBar(ctx);
        root.addView(spinner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView size = new TextView(ctx);
        size.setTextColor(0xB3FFFFFF);
        size.setTextSize(12);
        FrameLayout.LayoutParams sizeLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sizeLp.topMargin = (int) (40 * dp);
        root.addView(size, sizeLp);

        TextView download = new TextView(ctx);
        download.setText(I18n.t(ctx, R.string.ig_download_choice_title));
        download.setTextColor(Color.WHITE);
        download.setTextSize(15);
        download.setGravity(Gravity.CENTER);
        int ph = (int) (28 * dp), pv = (int) (12 * dp);
        download.setPadding(ph, pv, ph, pv);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x33FFFFFF);
        pill.setCornerRadius(24 * dp);
        download.setBackground(pill);
        FrameLayout.LayoutParams dlLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlLp.bottomMargin = (int) (56 * dp);
        root.addView(download, dlLp);
        download.setOnClickListener(v -> {
            dialog.dismiss();
            save(ctx, url, username);
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();

        new Thread(() -> {
            Bitmap bmp = null;
            try {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                downloadToStream(url, buf);
                byte[] bytes = buf.toByteArray();
                bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Throwable t) {
                ModuleLog.line("(IE|ProfileDL) ⚠️ viewer load: " + t);
            }
            final Bitmap b = bmp;
            mainHandler.post(() -> {
                spinner.setVisibility(View.GONE);
                if (b == null) {
                    ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_profile_pic_url_not_found), Toast.LENGTH_SHORT).show();
                    return;
                }
                image.setImageBitmap(b);
                size.setText(b.getWidth() + " × " + b.getHeight());
            });
        }).start();
    }

    private static void save(Context ctx, String url, String username) {
        String filename = FeedVideoDownloadHook.buildFilename(username, "profile", null, false);
        ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading_profile_pic), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(ctx, url, filename, false, username);
                if (!delegated) {
                    mainHandler.post(() -> ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx,
                            I18n.t(ctx, R.string.ig_toast_profile_pic_saved), Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                ModuleLog.line("(IE|ProfileDL) ❌ download: " + e.getMessage());
                mainHandler.post(() -> ps.reso.instaeclipse.utils.toast.IeToast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── URL extraction ────────────────────────────────────────────────────────

    private static final String IMAGE_URL = "com.instagram.common.typedurl.ImageUrl";

    /**
     * Walks the class hierarchy and reads every field typed as ImageUrl, returning the first one
     * whose getUrl() yields an http(s) URL. getDeclaredFields() order is unspecified, but all the
     * candidates point at the same displayed picture (main / tracking / fallback copy).
     */
    private static String getUrlFromImageUrlFields(View view) {
        for (Class<?> cls = view.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (Field f : ps.reso.instaeclipse.utils.core.SafeReflect.fields(cls)) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!IMAGE_URL.equals(f.getType().getName())) continue;
                try {
                    f.setAccessible(true);
                    String url = urlOf(f.get(view));
                    if (url != null) return url;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** ImageUrl.getUrl() if it is an http(s) URL, else null. */
    private static String urlOf(Object imageUrl) throws Throwable {
        if (imageUrl == null) return null;
        Object result = imageUrl.getClass().getMethod("getUrl").invoke(imageUrl);
        return result instanceof String s && s.startsWith("http") ? s : null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> cls, String name) {
        for (; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private static void downloadToStream(String url, OutputStream out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        MediaHttp.applyTimeouts(conn);
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

    public static void install(DexKitBridge bridge, ClassLoader cl) {
        resolveHdGetter(bridge, cl);
        try {
            XposedHelpers.findAndHookMethod(cl.loadClass("androidx.fragment.app.Fragment"), "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) { ps.reso.instaeclipse.utils.core.JitGuard.tick(param.method);
                    if (!FeatureFlags.enableProfileDownload) return;
                    for (Class<?> c = param.thisObject.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                        if (c.getName().equals(FRAGMENT)) {
                            lastProfile = new WeakReference<>(param.thisObject);
                            return;
                        }
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(IE|ProfileDL) ⚠️ profile tracking: " + t);
        }
        install();
    }
}
