package ps.reso.instaeclipse.mods.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.history.DownloadHistory;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.users.UserUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class FeedVideoDownloadHook {

    // Matches "{stem}_{yyyyMMdd}_{HHmmss}{.ext}" — the timestamp suffix buildFilename()
    // appends when FeatureFlags.downloaderAddTimestamp is on.
    private static final Pattern TIMESTAMPED_FILENAME_PATTERN =
            Pattern.compile("^(.*)_([0-9]{8}_[0-9]{6})(\\.[^.]+)$");

    static final String[] VIDEO_VERSION_INTF_CLASSES = {
            "com.instagram.api.schemas.VideoVersionIntf",
            "com.instagram.model.mediasize.VideoVersionIntf"
    };

    // ── Floating download button overlay state ────────────────────────────────
    private static final String DECOR_OVERLAY_TAG = "ie_decor_overlay";
    private static final int TAG_CACHED_MEDIA = "ie_dl_media".hashCode();
    private static final int TAG_OVERLAY_ANCHOR = "ie_dl_overlay_anchor".hashCode();

    private static final WeakHashMap<View, Boolean> injectedHosts = new WeakHashMap<>();
    private static final WeakHashMap<View, OverlayBinding> activeOverlays = new WeakHashMap<>();
    private static final WeakHashMap<Activity, View> bottomSheetContainers = new WeakHashMap<>();

    private static volatile int sFeedShareId;
    private static volatile int sFeedLikeId;
    private static volatile int sFeedSaveId;
    private static volatile int sFeedButtonsGroupId;
    private static volatile int sReelLikeId;
    private static volatile int sClipsUfiId;
    private static volatile int sBottomSheetContainerId;

    @SuppressLint("DiscouragedApi")
    private static void ensureViewIdsCached(Context ctx) {
        if (sFeedShareId != 0 && sFeedLikeId != 0 && sFeedSaveId != 0 && sFeedButtonsGroupId != 0
                && sReelLikeId != 0 && sClipsUfiId != 0 && sBottomSheetContainerId != 0) return;
        String pkg = ctx.getPackageName();
        android.content.res.Resources res = ctx.getResources();
        if (sFeedShareId == 0) sFeedShareId = res.getIdentifier("row_feed_button_share", "id", pkg);
        if (sFeedLikeId == 0) sFeedLikeId = res.getIdentifier("row_feed_button_like", "id", pkg);
        if (sFeedButtonsGroupId == 0) sFeedButtonsGroupId = res.getIdentifier("row_feed_view_group_buttons", "id", pkg);
        if (sFeedSaveId == 0) sFeedSaveId = res.getIdentifier("row_feed_button_save", "id", pkg);
        if (sReelLikeId == 0) sReelLikeId = res.getIdentifier("like_button", "id", pkg);
        if (sClipsUfiId == 0) sClipsUfiId = res.getIdentifier("clips_ufi_component", "id", pkg);
        if (sBottomSheetContainerId == 0) sBottomSheetContainerId = res.getIdentifier("layout_container_bottom_sheet", "id", pkg);
    }

    // ── Class/method refs resolved once at hook install time ─────────────────
    private static Class<?> mediaExtKtClass;
    private static Class<?> mediaClass;
    static Class<?> mutableMediaDictIntfClass;
    private static Method   methodImageUrl;         // MediaExtKt: static (Context, Media) -> String

    // VideoVersionIntf – stable public interface with getUrl()
    static Class<?> videoVersionIntfClass;
    static Method   videoVersionGetUrl;             // VideoVersionIntf.getUrl() -> String

    static Class<?> imageUrlClass;
    static Class<?> imageInfoClass;

    // All () -> List candidates from MutableMediaDictIntf + its superinterfaces
    static final List<Method> carouselCandidates = new ArrayList<>();

    // User class + the method on MutableMediaDictIntf that returns it — resolved via DexKit
    private static Class<?> userClass;
    private static Method   dictUserGetter;    // () -> UserClass on MutableMediaDictIntf
    // userUsernameGetter lives in UserUtils — resolved here and stored there

    // ── Uri.parse fallback buffer ─────────────────────────────────────────────
    private static final class UrlEntry {
        final String url; final long time;
        UrlEntry(String u) { url = u; time = System.currentTimeMillis(); }
    }
    private static final int MAX_URLS = 200;
    private static final int CAROUSEL_MAX_ITEMS = 20;
    private static final Deque<UrlEntry> urlBuffer      = new ArrayDeque<>();
    private static final Deque<UrlEntry> videoUrlBuffer = new ArrayDeque<>(); // DexKit-captured video URLs
    static final ExecutorService executor    = Executors.newCachedThreadPool();
    static final Handler         mainHandler = new Handler(Looper.getMainLooper());

    // Username + media ID resolved at download trigger time
    private volatile String currentDownloadUsername = null;
    private volatile String currentDownloadMediaId  = null;

    // ── Entry point ──────────────────────────────────────────────────────────

    public void install(ClassLoader classLoader) {
        // Load Media and MediaExtKt
        try {
            mediaClass      = classLoader.loadClass("com.instagram.feed.media.Media");
            mediaExtKtClass = classLoader.loadClass("com.instagram.feed.media.MediaExtKt");
            // Find static (Context, Media) -> String method (name changes every version)
            for (Method m : mediaExtKtClass.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2
                        && "android.content.Context".equals(p[0].getName())
                        && p[1] == mediaClass
                        && m.getReturnType() == String.class) {
                    m.setAccessible(true);
                    methodImageUrl = m;
                    break;
                }
            }
        } catch (Throwable ignored) {}

        // Load VideoVersionIntf (stable public interface with getUrl()).
        // IG 444+ moved this from com.instagram.model.mediasize to com.instagram.api.schemas —
        // try both so this keeps working across the package move either way.
        for (String cn : VIDEO_VERSION_INTF_CLASSES) {
            try {
                videoVersionIntfClass = classLoader.loadClass(cn);
                videoVersionGetUrl = videoVersionIntfClass.getMethod("getUrl");
                break;
            } catch (Throwable ignored) {}
        }

        try {
            imageUrlClass = classLoader.loadClass("com.instagram.common.typedurl.ImageUrl");
        } catch (Throwable ignored) {}
        try {
            imageInfoClass = classLoader.loadClass("com.instagram.model.mediasize.ImageInfo");
        } catch (Throwable ignored) {}
        try {
            userClass = classLoader.loadClass("com.instagram.user.model.User");
        } catch (Throwable ignored) {}

        // Load MutableMediaDictIntf and collect () -> List methods from it
        // AND its direct superinterfaces only (Instagram 423+ moved Cz7() to LX/IdM).
        // Do NOT recurse deeper — LX/IdM's own ancestors flood us with unrelated methods.
        Set<String> seen = new HashSet<>();
        try {
            mutableMediaDictIntfClass = classLoader.loadClass("com.instagram.feed.media.MutableMediaDictIntf");
            collectListGetters(mutableMediaDictIntfClass, seen);
            for (Class<?> superIface : mutableMediaDictIntfClass.getInterfaces()) {
                String sn = superIface.getName();
                if (!sn.startsWith("com.instagram.") && !sn.startsWith("com.facebook.") && !sn.startsWith("X.")) continue;
                collectListGetters(superIface, seen);
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> liveTreeDictClass = classLoader.loadClass("com.instagram.feed.media.LiveTreeMediaDict");
            if (mutableMediaDictIntfClass == null) mutableMediaDictIntfClass = liveTreeDictClass;
            collectListGetters(liveTreeDictClass, seen);
        } catch (Throwable ignored) {}

        installUriCaptureHook();
        installViewHook();
    }

    // ── Hook 1: Uri.parse (fallback buffer) ──────────────────────────────────

    private void installUriCaptureHook() {
        try {
            XposedHelpers.findAndHookMethod(Uri.class, "parse", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!FeatureFlags.enablePostDownload && !FeatureFlags.enableReelDownload) return;
                            String s = (String) param.args[0];
                            if (s == null || !isCdnMediaUrl(s)) return;
                            synchronized (urlBuffer) {
                                if (!urlBuffer.isEmpty() && urlBuffer.peekFirst().url.equals(s))
                                    return;
                                urlBuffer.addFirst(new UrlEntry(s));
                                while (urlBuffer.size() > MAX_URLS) urlBuffer.removeLast();
                            }
                        }
                    });
            FeatureStatusTracker.setHooked("PostDownload");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | MediaDownload): ❌ Uri.parse hook: " + t);
        }
    }

    private void installViewHook() {
        XC_MethodHook attachHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View view)) return;
                scheduleInjectionForView(view);
            }
        };
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", attachHook);
            FeatureStatusTracker.setHooked("PostDownload");
            ModuleLog.line("(IE|DL) view injection hooks installed");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | MediaDownload): ❌ View hook: " + t);
        }
    }

    private void scheduleInjectionForView(View view) {
        if (!FeatureFlags.enablePostDownload && !FeatureFlags.enableReelDownload) return;
        Context ctx = view.getContext();
        if (ctx == null) return;
        ensureViewIdsCached(ctx);

        int viewId = view.getId();
        boolean feedShare = FeatureFlags.enablePostDownload && sFeedShareId != 0 && viewId == sFeedShareId;
        boolean feedRow = FeatureFlags.enablePostDownload && sFeedButtonsGroupId != 0 && viewId == sFeedButtonsGroupId;
        boolean reelLike = FeatureFlags.enableReelDownload && sReelLikeId != 0 && viewId == sReelLikeId
                && hasAncestorWithId(view, sClipsUfiId);
        boolean clipsUfi = FeatureFlags.enableReelDownload && sClipsUfiId != 0 && viewId == sClipsUfiId;

        if (!feedShare && !feedRow && !reelLike && !clipsUfi) return;

        Runnable work = () -> {
            try {
                if (feedShare) {
                    tryInjectFeedDownload(view, ctx);
                } else if (feedRow) {
                    View rowRoot = findFeedRowRoot(view);
                    View shareBtn = rowRoot != null ? rowRoot.findViewById(sFeedShareId) : null;
                    if (shareBtn == null && view instanceof ViewGroup vg) shareBtn = vg.findViewById(sFeedShareId);
                    if (shareBtn != null) tryInjectFeedDownload(shareBtn, ctx);
                }
                if (reelLike) {
                    tryInjectReelDownload(view, ctx);
                } else if (clipsUfi && view instanceof ViewGroup vg) {
                    View likeBtn = vg.findViewById(sReelLikeId);
                    if (likeBtn != null) tryInjectReelDownload(likeBtn, ctx);
                }
            } catch (Throwable t) {
                ModuleLog.line("(IE|DL) inject failed: " + t.getMessage());
            }
        };

        postWhenLaidOut(view, work);
    }

    private enum OverlayPlacement { LEFT_OF, ABOVE, RIGHT_OF }

    /**
     * Binds a floating overlay button to its anchor view for as long as the anchor stays
     * attached, continuously resyncing position (scroll/relayout) and disposing itself
     * (removing the button + all listeners) the moment the anchor detaches — e.g. when the
     * RecyclerView recycles the row. Without this, a button added directly into a recycled
     * row can go stale or duplicate across scroll.
     */
    private static final class OverlayBinding {
        private final View anchor;
        private final ImageButton btn;
        private final ViewGroup host;
        private final View dedupeKey;
        private final OverlayPlacement placement;
        private final int gapPx;
        private final View root;
        private final View scrollParent;
        private boolean disposed;
        private final ViewTreeObserver.OnScrollChangedListener scrollListener;
        private final View.OnLayoutChangeListener layoutListener;

        OverlayBinding(View anchor, ImageButton btn, ViewGroup host, View dedupeKey,
                       OverlayPlacement placement, int gapPx) {
            this.anchor = anchor;
            this.btn = btn;
            this.host = host;
            this.dedupeKey = dedupeKey;
            this.placement = placement;
            this.gapPx = gapPx;
            this.root = host.getRootView();
            this.scrollParent = findVerticalScrollParent(anchor);

            layoutListener = (v, l, t, r, b, ol, ot, orr, ob) -> syncPosition();
            scrollListener = this::syncPosition;

            anchor.addOnLayoutChangeListener(layoutListener);
            host.addOnLayoutChangeListener(layoutListener);
            if (scrollParent != null) {
                scrollParent.addOnLayoutChangeListener(layoutListener);
                ViewTreeObserver svto = scrollParent.getViewTreeObserver();
                if (svto.isAlive()) svto.addOnScrollChangedListener(scrollListener);
            }
            ViewTreeObserver rvto = root.getViewTreeObserver();
            if (rvto.isAlive()) rvto.addOnScrollChangedListener(scrollListener);
            anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { syncPosition(); }
                @Override public void onViewDetachedFromWindow(View v) { dispose(); }
            });

            syncPosition();
            anchor.postDelayed(this::syncPosition, 100);
            anchor.postDelayed(this::syncPosition, 400);
        }

        void syncPosition() {
            if (disposed || btn.getParent() != host) return;
            if (!isAnchorVisibleOnScreen(anchor) || isBottomSheetShowing(anchor)) {
                btn.setVisibility(View.INVISIBLE);
                return;
            }
            if (applyOverlayPosition(host, anchor, btn, placement, gapPx)) {
                btn.setVisibility(View.VISIBLE);
            }
        }

        void dispose() {
            if (disposed) return;
            disposed = true;
            try { anchor.removeOnLayoutChangeListener(layoutListener); } catch (Throwable ignored) {}
            try { host.removeOnLayoutChangeListener(layoutListener); } catch (Throwable ignored) {}
            if (scrollParent != null) {
                try { scrollParent.removeOnLayoutChangeListener(layoutListener); } catch (Throwable ignored) {}
                try {
                    ViewTreeObserver vto = scrollParent.getViewTreeObserver();
                    if (vto.isAlive()) vto.removeOnScrollChangedListener(scrollListener);
                } catch (Throwable ignored) {}
            }
            try {
                ViewTreeObserver vto = root.getViewTreeObserver();
                if (vto.isAlive()) vto.removeOnScrollChangedListener(scrollListener);
            } catch (Throwable ignored) {}
            if (btn.getParent() == host) host.removeView(btn);
            synchronized (injectedHosts) { injectedHosts.remove(dedupeKey); }
            synchronized (activeOverlays) { activeOverlays.remove(dedupeKey); }
        }
    }

    private void tryInjectFeedDownload(View shareBtn, Context ctx) {
        if (isInjected(shareBtn)) return;
        if (isClipsContext(shareBtn)) return;

        View rowRoot = findFeedRowRoot(shareBtn);
        View mediaAnchor = shareBtn;
        if (rowRoot != null && sFeedLikeId != 0) {
            View like = rowRoot.findViewById(sFeedLikeId);
            if (like != null) mediaAnchor = like;
        }

        View positionAnchor = shareBtn;
        OverlayPlacement placement = OverlayPlacement.RIGHT_OF;
        if (rowRoot != null && sFeedSaveId != 0) {
            View save = rowRoot.findViewById(sFeedSaveId);
            if (save != null) {
                positionAnchor = save;
                placement = OverlayPlacement.LEFT_OF;
            }
        }

        View finalMediaAnchor = mediaAnchor;
        ImageButton btn = buildDownloadButton(ctx, shareBtn, false);
        btn.setOnClickListener(v -> triggerDownload(ctx, finalMediaAnchor, v));

        View finalPositionAnchor = positionAnchor;
        OverlayPlacement finalPlacement = placement;
        postWhenLaidOut(shareBtn, () -> placeOverlayButton(finalPositionAnchor, btn, shareBtn, finalPlacement));
    }

    private void tryInjectReelDownload(View likeBtn, Context ctx) {
        if (isInjected(likeBtn)) return;
        if (!isClipsContext(likeBtn)) return;

        ImageButton btn = buildDownloadButton(ctx, likeBtn, true);
        btn.setOnClickListener(v -> triggerDownload(ctx, likeBtn, v));
        postWhenLaidOut(likeBtn, () -> placeOverlayButton(likeBtn, btn, likeBtn, OverlayPlacement.ABOVE));
    }

    private void placeOverlayButton(View anchor, ImageButton btn, View dedupeKey, OverlayPlacement placement) {
        Context ctx = anchor.getContext();
        ViewGroup host = getDecorOverlayHost(ctx);
        if (host == null) {
            ModuleLog.line("(IE|DL) decor overlay host missing");
            return;
        }
        purgeStaleOverlays(host);

        int w = anchor.getWidth() > 0 ? anchor.getWidth() : dp(ctx, 40);
        int h = anchor.getHeight() > 0 ? anchor.getHeight() : w;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);

        Object media = findMediaForView(dedupeKey);
        if (media != null) btn.setTag(TAG_CACHED_MEDIA, media);

        synchronized (activeOverlays) {
            OverlayBinding existing = activeOverlays.get(dedupeKey);
            if (existing != null) existing.dispose();
        }

        host.addView(btn, lp);
        btn.bringToFront();
        btn.setElevation(dp(ctx, 12));
        btn.setVisibility(View.INVISIBLE);
        btn.setTag(TAG_OVERLAY_ANCHOR, dedupeKey);

        int gap = dp(ctx, placement == OverlayPlacement.ABOVE ? 10 : 4);
        OverlayBinding binding = new OverlayBinding(anchor, btn, host, dedupeKey, placement, gap);
        synchronized (activeOverlays) {
            activeOverlays.put(dedupeKey, binding);
        }
        setInjected(dedupeKey);
    }

    private static boolean applyOverlayPosition(ViewGroup host, View anchor, View btn,
                                                 OverlayPlacement placement, int gapPx) {
        if (anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return false;

        int[] anchorLoc = new int[2];
        int[] hostLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);
        host.getLocationInWindow(hostLoc);

        float relX = anchorLoc[0] - hostLoc[0];
        float relY = anchorLoc[1] - hostLoc[1];
        int bw = btn.getWidth() > 0 ? btn.getWidth() : btn.getLayoutParams().width;
        int bh = btn.getHeight() > 0 ? btn.getHeight() : btn.getLayoutParams().height;
        if (bw <= 0) bw = anchor.getWidth();
        if (bh <= 0) bh = anchor.getHeight();
        if (bw <= 0 || bh <= 0) return false;

        float x;
        float y = relY + (anchor.getHeight() - bh) / 2f;
        switch (placement) {
            case ABOVE:
                x = relX + (anchor.getWidth() - bw) / 2f;
                y = relY - bh - gapPx;
                break;
            case RIGHT_OF:
                x = relX + anchor.getWidth() + gapPx;
                break;
            case LEFT_OF:
            default:
                x = relX - bw - gapPx;
                break;
        }
        if (placement == OverlayPlacement.LEFT_OF && x < 0) {
            x = relX + anchor.getWidth() + gapPx;
        }

        btn.setTranslationX(x);
        btn.setTranslationY(y);
        return true;
    }

    private static View findVerticalScrollParent(View view) {
        android.view.ViewParent p = view.getParent();
        for (int i = 0; i < 20 && p instanceof View; i++) {
            View v = (View) p;
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) return v;
            String name = v.getClass().getName();
            if (name.contains("RecyclerView") || name.contains("ScrollView") || name.contains("NestedScrollView")) {
                return v;
            }
            p = v.getParent();
        }
        return null;
    }

    private static boolean isInjected(View key) {
        synchronized (injectedHosts) { return Boolean.TRUE.equals(injectedHosts.get(key)); }
    }

    private static void setInjected(View key) {
        synchronized (injectedHosts) { injectedHosts.put(key, true); }
    }

    private static void postWhenLaidOut(View view, Runnable action) {
        if (view.getWidth() > 0 && view.getHeight() > 0) view.post(action);
        else view.postDelayed(action, 120);
    }

    private void triggerDownload(Context ctx, View anchor, View btnView) {
        Object tagged = btnView != null ? btnView.getTag(TAG_CACHED_MEDIA) : null;

        if (isClipsContext(anchor)) {
            Object media = tagged != null ? tagged : findMediaForClipsView(anchor);
            List<String> urls = new ArrayList<>();
            if (media != null) {
                urls = extractAllUrlsFromMedia(ctx, media);
                if (urls.isEmpty()) {
                    String videoUrl = bestVideoUrlFromMedia(media);
                    if (videoUrl != null) urls = new ArrayList<>(List.of(videoUrl));
                }
            }
            if (urls.isEmpty()) urls = resolveClipsUrls(ctx, anchor);
            if (urls.isEmpty()) {
                Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_no_media_for_post), Toast.LENGTH_SHORT).show();
                return;
            }
            onDownloadClicked(ctx, urls, anchor, media);
            return;
        }

        Object media = tagged != null ? tagged : findMediaForView(anchor);
        if (media != null) {
            List<String> urls = extractAllUrlsFromMedia(ctx, media);
            if (!urls.isEmpty()) {
                onDownloadClicked(ctx, urls, anchor, media);
                return;
            }
        }

        List<String> urls = resolveUrls(anchor, btnView);
        if (urls.isEmpty()) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_no_media_for_post), Toast.LENGTH_SHORT).show();
            return;
        }
        onDownloadClicked(ctx, urls, anchor, media);
    }

    private Object findMediaForView(View anchor) {
        if (mediaClass == null) return null;
        if (isClipsContext(anchor)) return findMediaForClipsView(anchor);

        View current = anchor;
        for (int i = 0; i < 6 && current != null; i++) {
            Object media = getMediaFromListener(getOnClickListener(current));
            if (media != null) return media;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }

        View scope = findFeedRowRoot(anchor);
        if (scope == null) scope = findAncestorWithId(anchor, sClipsUfiId);
        if (scope == null) scope = anchor.getRootView();
        if (scope instanceof ViewGroup vg) {
            Object media = findMediaInButtonTree(vg);
            if (media != null) return media;
        }
        return null;
    }

    private View findClipsItemRoot(View anchor) {
        View ufi = findAncestorWithId(anchor, sClipsUfiId);
        if (ufi == null) return anchor;
        return ufi.getParent() instanceof View ? (View) ufi.getParent() : ufi;
    }

    private View resolveClipsLikeButton(View anchor) {
        View ufi = findAncestorWithId(anchor, sClipsUfiId);
        if (ufi instanceof ViewGroup vg && sReelLikeId != 0) {
            View like = vg.findViewById(sReelLikeId);
            if (like != null) return like;
        }
        return anchor;
    }

    private Object findMediaForClipsView(View anchor) {
        if (mediaClass == null) return null;

        View likeBtn = resolveClipsLikeButton(anchor);
        View current = likeBtn;
        for (int i = 0; i < 16 && current != null; i++) {
            Object media = getMediaFromListener(getOnClickListener(current));
            if (media != null) return media;
            if (current.getId() == sClipsUfiId) break;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }

        View root = findClipsItemRoot(anchor);
        if (root instanceof ViewGroup vg) {
            Object media = findMediaInButtonTree(vg);
            if (media != null) return media;
            media = scanViewGroupForMedia(vg, 0);
            if (media != null) return media;
        }
        return null;
    }

    private static int preferredClipsBufferIndex(int bufferSize) {
        return bufferSize >= 2 ? 1 : 0;
    }

    private static String pickClipsVideoFromBuffer(List<String> videoUrls) {
        if (videoUrls.isEmpty()) return null;
        return videoUrls.get(preferredClipsBufferIndex(videoUrls.size()));
    }

    private List<String> resolveClipsUrls(Context ctx, View anchor) {
        View likeBtn = resolveClipsLikeButton(anchor);
        Object media = findMediaForClipsView(anchor);

        if (media != null) {
            List<String> urls = extractAllUrlsFromMedia(ctx, media);
            if (!urls.isEmpty()) return urls;
            String videoUrl = bestVideoUrlFromMedia(media);
            if (videoUrl != null) return List.of(videoUrl);
        }

        List<String> listenerUrls = urlsFromSaveBtnListener(likeBtn);
        if (!listenerUrls.isEmpty()) {
            long since = System.currentTimeMillis() - 15_000;
            List<String> recentVideos = snapshotVideoUrlsSince(since);
            String buffered = pickClipsVideoFromBuffer(recentVideos);
            if (buffered != null) {
                for (String u : listenerUrls) {
                    if (u.equals(buffered) || u.contains(buffered) || buffered.contains(u)) {
                        return List.of(u);
                    }
                }
            }
            if (listenerUrls.size() == 1) return listenerUrls;
            List<String> videos = new ArrayList<>();
            for (String u : listenerUrls) if (isVideoUrl(u)) videos.add(u);
            if (videos.size() == 1) return videos;
            String fromBuffer = pickClipsVideoFromBuffer(videos);
            if (fromBuffer != null) return List.of(fromBuffer);
            if (!videos.isEmpty()) return List.of(videos.get(videos.size() - 1));
        }

        long since = System.currentTimeMillis() - 15_000;
        List<String> videoUrls = snapshotVideoUrlsSince(since);
        String picked = pickClipsVideoFromBuffer(videoUrls);
        if (picked != null) return List.of(picked);

        List<String> buffered2 = snapshotUrlsSince(since);
        List<String> reelVideos = new ArrayList<>();
        for (String u : buffered2) if (isVideoUrl(u)) reelVideos.add(u);
        String picked2 = pickClipsVideoFromBuffer(reelVideos);
        if (picked2 != null) return List.of(picked2);

        return new ArrayList<>();
    }

    private Object findMediaInButtonTree(ViewGroup root) {
        int[] ids = {sFeedLikeId, sFeedShareId, sFeedSaveId, sReelLikeId};
        for (int id : ids) {
            if (id == 0) continue;
            View button = root.findViewById(id);
            if (button == null) continue;
            Object media = getMediaFromListener(getOnClickListener(button));
            if (media != null) return media;
        }

        Context ctx = root.getContext();
        @SuppressLint("DiscouragedApi")
        int directShareId = ctx.getResources().getIdentifier("direct_share_button", "id", ctx.getPackageName());
        if (directShareId != 0) {
            View shareBtn = root.findViewById(directShareId);
            if (shareBtn != null) {
                Object media = getMediaFromListener(getOnClickListener(shareBtn));
                if (media != null) return media;
            }
        }
        return scanViewGroupForMedia(root, 0);
    }

    private Object scanViewGroupForMedia(ViewGroup group, int depth) {
        if (depth > 5) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object media = getMediaFromListener(getOnClickListener(child));
            if (media != null) return media;
            if (child instanceof ViewGroup vg) {
                media = scanViewGroupForMedia(vg, depth + 1);
                if (media != null) return media;
            }
        }
        return null;
    }

    private static ViewGroup getDecorOverlayHost(Context ctx) {
        Activity activity = getActivityFromContext(ctx);
        if (activity == null) return null;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup decorRoot)) return null;

        View existing = decorRoot.findViewWithTag(DECOR_OVERLAY_TAG);
        if (existing instanceof ViewGroup) return (ViewGroup) existing;

        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setTag(DECOR_OVERLAY_TAG);
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        decorRoot.addView(overlay, lp);
        return overlay;
    }

    private static Activity getActivityFromContext(Context ctx) {
        Context current = ctx;
        while (current instanceof android.content.ContextWrapper wrapper) {
            if (current instanceof Activity activity) return activity;
            current = wrapper.getBaseContext();
        }
        return null;
    }

    private static ImageButton buildDownloadButton(Context ctx, View reference, boolean reelStyle) {
        ImageButton btn = new ImageButton(ctx);
        int icon = resolveDownloadIcon(ctx);
        btn.setImageResource(icon != 0 ? icon : android.R.drawable.stat_sys_download);
        btn.setBackground(null);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setContentDescription(I18n.t(ctx, R.string.ig_dl_title));
        int pad = dp(ctx, reelStyle ? 8 : 10);
        btn.setPadding(pad, pad, pad, pad);
        if (reelStyle || isDarkMode(ctx)) {
            btn.setColorFilter(Color.WHITE);
        } else if (reference instanceof ImageView iv && iv.getColorFilter() != null) {
            btn.setColorFilter(iv.getColorFilter());
        }
        return btn;
    }

    private static int resolveDownloadIcon(Context ctx) {
        try {
            Class<?> optionClass = ctx.getClassLoader()
                    .loadClass("com.instagram.feed.media.mediaoption.MediaOption$Option");
            for (Object v : (Object[]) optionClass.getMethod("values").invoke(null)) {
                if (v.toString().contains("DOWNLOAD")) {
                    Field f = v.getClass().getField("iconDrawable");
                    return (int) f.get(v);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static boolean isDarkMode(Context ctx) {
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static View findFeedRowRoot(View anchor) {
        View current = anchor;
        for (int i = 0; i < 16 && current != null; i++) {
            if (sFeedLikeId != 0 && sFeedShareId != 0
                    && current.findViewById(sFeedLikeId) != null
                    && current.findViewById(sFeedShareId) != null) {
                return current;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    private static View findAncestorWithId(View view, int targetId) {
        if (targetId == 0) return null;
        android.view.ViewParent p = view.getParent();
        for (int i = 0; i < 12 && p instanceof View; i++) {
            View v = (View) p;
            if (v.getId() == targetId) return v;
            p = v.getParent();
        }
        return null;
    }

    private static boolean isClipsContext(View view) {
        return sClipsUfiId != 0 && hasAncestorWithId(view, sClipsUfiId);
    }

    private static boolean isAnchorVisibleOnScreen(View anchor) {
        if (!anchor.isAttachedToWindow()) return false;
        if (anchor.getVisibility() != View.VISIBLE || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            return false;
        }
        android.graphics.Rect visible = new android.graphics.Rect();
        if (!anchor.getGlobalVisibleRect(visible)) return false;
        if (visible.width() < anchor.getWidth() / 3 || visible.height() < anchor.getHeight() / 3) {
            return false;
        }
        android.util.DisplayMetrics dm = anchor.getResources().getDisplayMetrics();
        android.graphics.Rect screen = new android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels);
        return android.graphics.Rect.intersects(visible, screen);
    }

    private static boolean isBottomSheetShowing(View anchor) {
        try {
            Context ctx = anchor.getContext();
            ensureViewIdsCached(ctx);
            if (sBottomSheetContainerId != 0) {
                Activity activity = getActivityFromContext(ctx);
                if (activity == null) return false;

                View bsView;
                synchronized (bottomSheetContainers) {
                    bsView = bottomSheetContainers.get(activity);
                    if (bsView == null) {
                        View decor = activity.getWindow().getDecorView();
                        View found = decor.findViewById(sBottomSheetContainerId);
                        if (found != null) bottomSheetContainers.put(activity, found);
                        bsView = found;
                    }
                }

                if (bsView instanceof ViewGroup vg && vg.getVisibility() == View.VISIBLE) {
                    for (int i = 0; i < vg.getChildCount(); i++) {
                        View child = vg.getChildAt(i);
                        if (child != null && child.getVisibility() == View.VISIBLE) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void purgeStaleOverlays(ViewGroup host) {
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            Object tag = child.getTag(TAG_OVERLAY_ANCHOR);
            if (!(tag instanceof View anchor)) continue;
            if (isAnchorVisibleOnScreen(anchor)) continue;
            synchronized (activeOverlays) {
                OverlayBinding binding = activeOverlays.get(anchor);
                if (binding != null) binding.dispose();
                else if (child.getParent() == host) host.removeView(child);
            }
        }
    }

    // ── URL resolution — three-tier ───────────────────────────────────────────
    //
    // Tier 1: Reflect on the save button's click listener to find the exact Media
    //   object captured in its closure. Extract video URL via VideoVersionIntf.getUrl()
    //   or image URL via MediaExtKt helper. This is per-post with no timing ambiguity.
    //
    // Tier 2: Last 30 s of the Uri.parse buffer (catches lazy-loaded carousels).

    @SuppressLint("DiscouragedApi")
    private List<String> resolveUrls(View likeBtn, View downloadBtn) {
        // Tier-1a: like button's listener (works for standard feed posts)
        List<String> urls = urlsFromSaveBtnListener(likeBtn);
        ModuleLog.line("(IE|DL) Tier-1a urls=" + urls.size());
        if (!urls.isEmpty()) return urls;

        // Tier-1b: bookmark/save button's listener.
        // The save button always captures the Media object (it needs it for save-to-collection).
        // IMPORTANT: row_feed_button_save is NOT a sibling of the like button — it sits in
        // the action bar parent (one level above the left-buttons group). Walk up up to 4
        // parent levels so we reach the action bar container and find it there.
        Context ctx = likeBtn.getContext();
        int saveResId = ctx.getResources().getIdentifier(
                "row_feed_button_save", "id", ctx.getPackageName());
        if (saveResId != 0) {
            android.view.ViewParent p = likeBtn.getParent();
            for (int i = 0; i < 4 && p instanceof ViewGroup vg; i++, p = vg.getParent()) {
                View realSaveBtn = vg.findViewById(saveResId);
                if (realSaveBtn != null) {
                    ModuleLog.line("(IE|DL) Tier-1b found save btn at parent level " + i);
                    urls = urlsFromSaveBtnListener(realSaveBtn);
                    ModuleLog.line("(IE|DL) Tier-1b urls=" + urls.size());
                    if (!urls.isEmpty()) return urls;
                    break; // found the button but listener had no URLs — no point going wider
                }
            }
        }

        return new ArrayList<>();
    }

    // ── Tier 1: Save-button listener search ───────────────────────────────────
    //
    // Strategy:
    //   1. Get the OnClickListener set by Instagram on the save button.
    //   2. Find the captured Media object in its closure (depth-limited field scan).
    //   3. From the MutableMediaDictIntf on the Media object:
    //      a. Check if any () -> List candidate returns VideoVersionIntf items
    //         → single video post: extract URL via getUrl(), return it.
    //      b. Check if any () -> List candidate returns >= 2 non-video items
    //         → carousel: try to extract per-item URLs.
    //      c. Fall back to MediaExtKt image URL helper for single photo posts.

    private static List<String> urlsFromSaveBtnListener(View saveBtn) {
        try {
            Object listener = getOnClickListener(saveBtn);
            if (listener == null) return new ArrayList<>();

            List<String> urls = new ArrayList<>();
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            scanForCdnUrls(listener, urls, 0, visited);

            if (mediaClass != null) {
                Object media = findFieldOfType(listener, mediaClass, 4);
                if (media != null) {
                    List<String> extracted = extractAllUrlsFromMedia(saveBtn.getContext(), media);
                    ModuleLog.line("(IE|DL) saveBtn extractAll=" + extracted.size());
                    if (!extracted.isEmpty()) return extracted;
                }

                List<String> images = new ArrayList<>();
                for (String u : urls) { if (!isVideoUrl(u)) images.add(u); }
                if (!images.isEmpty()) return List.of(pickBestImageUrl(images));
            }

            return urls;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /**
     * Probes all no-parameter String-returning methods on {@code obj} (including superclass
     * declared methods) and returns the first one that yields an Instagram CDN URL.
     *
     * This is needed for Pando/LiveTree JNI nodes (LX/VPC carousel items, LX/5q9) whose
     * image URLs are only accessible via obfuscated JNI-backed methods, not via fields.
     */
    private static String probeCdnUrlViaStringMethods(Object obj) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            String cn = cls.getName();
            if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) break;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != String.class) continue;
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(obj);
                    if (r instanceof String s && isCdnMediaUrl(s)) return s;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Depth-limited field-graph scan for any VideoVersionIntf instance inside {@code obj}.
     * Returns the first CDN URL found via {@code VideoVersionIntf.getUrl()}, or null.
     *
     * This is the primary video-detection path. It is version-independent: it does not
     * depend on knowing the obfuscated name of the method that returns the video-version
     * list (DIS(), or whatever it is renamed to in newer Instagram builds).
     */
    static String findVideoUrlInObject(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || !visited.add(obj)) return null;
        if (videoVersionIntfClass == null || videoVersionGetUrl == null) return null;

        // Direct hit: obj itself implements VideoVersionIntf
        if (videoVersionIntfClass.isInstance(obj)) {
            try {
                String url = (String) videoVersionGetUrl.invoke(obj);
                if (url != null && isCdnMediaUrl(url)) return url;
            } catch (Throwable ignored) {}
        }

        Class<?> cls = obj.getClass();
        String cn = cls.getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) return null;

        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;

                    if (val instanceof List<?> list) {
                        // List field — check if any element is a VideoVersionIntf
                        for (Object elem : list) {
                            if (elem != null && videoVersionIntfClass.isInstance(elem)) {
                                try {
                                    String url = (String) videoVersionGetUrl.invoke(elem);
                                    if (url != null && isCdnMediaUrl(url)) return url;
                                } catch (Throwable ignored) {}
                            }
                        }
                    } else {
                        // Recurse into Instagram/Facebook objects only
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook.")) {
                            String found = findVideoUrlInObject(val, visited, depth + 1);
                            if (found != null) return found;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    /**
     * Collects ALL CDN video URLs found by walking the VideoVersionIntf graph inside {@code obj}.
     * Prefers m86 URLs (combined audio+video stream) — those are sorted to the front of the list.
     */
    static void collectAllVideoUrls(Object obj, List<String> out, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || !visited.add(obj)) return;
        if (videoVersionIntfClass == null || videoVersionGetUrl == null) return;

        if (videoVersionIntfClass.isInstance(obj)) {
            try {
                String url = (String) videoVersionGetUrl.invoke(obj);
                if (url != null && isCdnMediaUrl(url) && !out.contains(url)) out.add(url);
            } catch (Throwable ignored) {}
            return; // don't recurse into VideoVersionIntf objects
        }

        Class<?> cls = obj.getClass();
        String cn = cls.getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) return;

        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof List<?> list) {
                        for (Object elem : list) {
                            if (mediaClass != null && mediaClass.isInstance(elem)) continue;
                            collectAllVideoUrls(elem, out, visited, depth + 1);
                        }
                    } else {
                        if (mediaClass != null && mediaClass.isInstance(val)) continue;
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook."))
                            collectAllVideoUrls(val, out, visited, depth + 1);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    /** Returns the best video URL from the media object: prefers m86 (combined stream), then largest area. */
    static String bestVideoUrlFromMedia(Object media) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> all = new ArrayList<>();
        collectAllVideoUrls(media, all, visited, 0);
        collectVideoUrlsFromListGetters(media, all);
        if (all.isEmpty()) return null;
        return pickBestVideoUrl(all);
    }

    private static void collectVideoUrlsFromListGetters(Object media, List<String> out) {
        if (media == null || videoVersionIntfClass == null || videoVersionGetUrl == null) return;
        List<Object> hosts = new ArrayList<>();
        hosts.add(media);
        if (mutableMediaDictIntfClass != null) {
            Object dict = findFieldAssignableTo(media, mutableMediaDictIntfClass);
            if (dict != null) hosts.add(dict);
        }
        for (Object host : hosts) {
            Set<String> seen = new HashSet<>();
            List<Method> methods = new ArrayList<>();
            if (mutableMediaDictIntfClass != null && mutableMediaDictIntfClass.isInstance(host)) {
                for (Method m : carouselCandidates) {
                    if (seen.add(m.getName())) methods.add(m);
                }
            }
            Class<?> cls = host.getClass();
            while (cls != null && cls != Object.class) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && List.class.isAssignableFrom(m.getReturnType())
                            && seen.add(m.getName())) {
                        methods.add(m);
                    }
                }
                cls = cls.getSuperclass();
            }
            for (Method m : methods) {
                try {
                    m.setAccessible(true);
                    Object listObj = m.invoke(host);
                    if (!(listObj instanceof List<?> items) || items.isEmpty()) continue;
                    if (!videoVersionIntfClass.isInstance(items.get(0))) continue;
                    for (Object item : items) {
                        if (!videoVersionIntfClass.isInstance(item)) continue;
                        String u = (String) videoVersionGetUrl.invoke(item);
                        if (u != null && isCdnMediaUrl(u) && !out.contains(u)) out.add(u);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    static String pickBestVideoUrl(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        String bestM86 = null;
        int bestM86Area = -1;
        String bestAny = urls.get(0);
        int bestAnyArea = -1;
        for (String u : urls) {
            int area = parseUrlArea(u);
            if (u.contains("/m86/") || u.contains("%2Fm86%2F")) {
                if (area >= bestM86Area) { bestM86Area = area; bestM86 = u; }
            }
            if (area >= bestAnyArea) { bestAnyArea = area; bestAny = u; }
        }
        return bestM86 != null ? bestM86 : bestAny;
    }

    static int parseUrlArea(String url) {
        if (url == null) return 0;
        int maxArea = 0;
        int i = 0;
        while (i < url.length()) {
            if (!Character.isDigit(url.charAt(i))) { i++; continue; }
            int numStart = i;
            while (i < url.length() && Character.isDigit(url.charAt(i))) i++;
            if (i >= url.length() || url.charAt(i) != 'x') continue;
            i++;
            if (i >= url.length() || !Character.isDigit(url.charAt(i))) continue;
            int numMid = i;
            while (i < url.length() && Character.isDigit(url.charAt(i))) i++;
            try {
                int w = Integer.parseInt(url.substring(numStart, numMid - 1));
                int h = Integer.parseInt(url.substring(numMid, i));
                if (w >= 50 && w <= 20000 && h >= 50 && h <= 20000) {
                    int area = w * h;
                    if (area > maxArea) maxArea = area;
                }
            } catch (NumberFormatException ignored) {}
        }
        return maxArea;
    }

    /**
     * Tries to call getUrl() on an object if it's available (handles VideoVersionIntf
     * and any other object that exposes a stable getUrl() method).
     */
    private static String tryGetUrl(Object obj) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod("getUrl");
            Object result = m.invoke(obj);
            return result instanceof String ? (String) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Among multiple resolutions of the same image, prefer the full-size original. */
    private static String pickBestImageUrl(List<String> images) {
        if (images == null || images.isEmpty()) return null;
        String best = images.get(0);
        int bestArea = parseUrlArea(best);
        for (String url : images) {
            if (isVideoUrl(url)) continue;
            int area = parseUrlArea(url);
            boolean notThumb = !url.contains("/s150x") && !url.contains("/s240x") &&
                    !url.contains("/s320x") && !url.contains("/s480x") &&
                    !url.contains("/s640x") && !url.contains("_s.jpg");
            if (notThumb && area >= bestArea) {
                bestArea = area;
                best = url;
            } else if (area > bestArea) {
                bestArea = area;
                best = url;
            }
        }
        return best;
    }

    /** Reads View.mListenerInfo.mOnClickListener via reflection. */
    private static Object getOnClickListener(View view) {
        try {
            Field liField = View.class.getDeclaredField("mListenerInfo");
            liField.setAccessible(true);
            Object li = liField.get(view);
            if (li == null) return null;
            Field clField = li.getClass().getDeclaredField("mOnClickListener");
            clField.setAccessible(true);
            return clField.get(li);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Recursively scans an object's fields for Instagram CDN URL strings.
     * Only descends into X.* / com.instagram.* / com.facebook.* objects.
     */
    private static final int MAX_SCAN_DEPTH = 6;
    private static final int MAX_SCAN_URLS  = 20;

    private static void scanForCdnUrls(Object obj, List<String> out,
                                        int depth, Set<Object> visited) {
        if (obj == null || depth > MAX_SCAN_DEPTH || out.size() >= MAX_SCAN_URLS) return;
        if (!visited.add(obj)) return;

        Class<?> cls = obj.getClass();
        String cn = cls.getName();
        if (cn.startsWith("android.") || cn.startsWith("java.lang.")  ||
            cn.startsWith("java.util.concurrent.") || cn.startsWith("kotlin.")) return;

        // Also try getUrl() for Pando tree nodes that expose it via method (not field)
        String directUrl = tryGetUrl(obj);
        if (directUrl != null && isCdnMediaUrl(directUrl) && !out.contains(directUrl))
            out.add(directUrl);

        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;

                    if (val instanceof String s) {
                        if (isCdnMediaUrl(s) && !out.contains(s)) out.add(s);
                    } else if (val instanceof List<?> list) {
                        for (Object item : list) scanForCdnUrls(item, out, depth + 1, visited);
                    } else if (val instanceof Object[] arr) {
                        for (Object item : arr) scanForCdnUrls(item, out, depth + 1, visited);
                    } else {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.")               ||
                            vcn.startsWith("com.instagram.")   ||
                            vcn.startsWith("com.facebook.")) {
                            scanForCdnUrls(val, out, depth + 1, visited);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static Object findFieldOfType(Object obj, Class<?> target, int depth) {
        if (obj == null || target == null || depth < 0) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (target.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try { return f.get(obj); } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        if (depth > 0) {
            cls = obj.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        if (v == null) continue;
                        String vcn = v.getClass().getName();
                        if (!vcn.startsWith("X.") && !vcn.startsWith("com.instagram.") &&
                                !vcn.startsWith("com.facebook.")) continue;
                        Object r = findFieldOfType(v, target, depth - 1);
                        if (r != null) return r;
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private static void collectListGetters(Class<?> cls, Set<String> seen) {
        if (cls == null) return;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && List.class.isAssignableFrom(m.getReturnType())) {
                if (seen.add(m.getName())) {
                    m.setAccessible(true);
                    carouselCandidates.add(m);
                }
            }
        }
    }

    /**
     * Finds the first field on {@code obj} whose declared type is assignable to
     * {@code targetType}. Used to locate interface-typed fields.
     */
    static Object findFieldAssignableTo(Object obj, Class<?> targetType) {
        if (obj == null || targetType == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null && targetType.isInstance(v)) return v;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    // ── Buffer helpers ────────────────────────────────────────────────────────

    private static List<String> snapshotUrlsSince(long from) {
        List<String> r = new ArrayList<>();
        synchronized (urlBuffer) {
            for (UrlEntry e : urlBuffer) {
                if (e.time >= from) r.add(e.url);
                else break;
            }
        }
        return r;
    }

    private static List<String> snapshotVideoUrlsSince(long from) {
        List<String> r = new ArrayList<>();
        synchronized (videoUrlBuffer) {
            for (UrlEntry e : videoUrlBuffer) {
                if (e.time >= from) r.add(e.url);
                else break;
            }
        }
        return r;
    }

    /**
     * DexKit-based hook on {@code VideoVersionIntf.getUrl()} — installed once at startup.
     *
     * Finds all concrete classes implementing VideoVersionIntf at runtime using DexKit,
     * hooks their {@code getUrl()} method, and passively captures returned CDN URLs into
     * {@code videoUrlBuffer}. This is version-proof: it doesn't depend on knowing the
     * obfuscated method name that returns the video-versions list (DIS(), etc.).
     *
     * Used as a supplement to the Uri.parse buffer (Tier 3) when Tiers 1 and 2 fail.
     */
    public static void installVideoUrlCaptureHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook urlHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enablePostDownload) return;
                Object result = param.getResult();
                if (!(result instanceof String url)) return;
                if (!isCdnMediaUrl(url)) return;
                synchronized (videoUrlBuffer) {
                    if (!videoUrlBuffer.isEmpty() && videoUrlBuffer.peekFirst().url.equals(url)) return;
                    videoUrlBuffer.addFirst(new UrlEntry(url));
                    while (videoUrlBuffer.size() > MAX_URLS) videoUrlBuffer.removeLast();
                }
            }
        };

        // Cache hit: hook all previously-found getUrl() implementations directly
        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("VideoUrlCapture", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, urlHook);
                ModuleLog.line("(IE|DL|DexKit) VideoUrlCapture: " + cached.size() + " method(s) from cache");
                resolveUsernameGetter(bridge, classLoader);
                return;
            }
        }

        try {
            List<ClassData> classes = new ArrayList<>();
            for (String cn : VIDEO_VERSION_INTF_CLASSES) {
                classes = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create()
                                .addInterface(cn, StringMatchType.Equals, false)));
                if (!classes.isEmpty()) break;
            }

            ModuleLog.line("(IE|DL|DexKit) VideoVersionIntf implementors found: " + classes.size());

            List<Method> hooked = new ArrayList<>();
            for (ClassData classData : classes) {
                try {
                    List<MethodData> methods = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .declaredClass(classData.getName())
                                    .name("getUrl")
                                    .returnType("java.lang.String")
                                    .paramCount(0)));

                    for (MethodData methodData : methods) {
                        try {
                            Method m = methodData.getMethodInstance(classLoader);
                            XposedBridge.hookMethod(m, urlHook);
                            ModuleLog.line("(IE|DL|DexKit) ✅ Hooked getUrl() on "
                                    + classData.getName());
                            hooked.add(m);
                        } catch (Throwable e) {
                            ModuleLog.line("(IE|DL|DexKit) ❌ Hook failed for "
                                    + classData.getName() + ": " + e.getMessage());
                        }
                    }
                } catch (Throwable e) {
                    ModuleLog.line("(IE|DL|DexKit) ❌ findMethod failed for "
                            + classData.getName() + ": " + e.getMessage());
                }
            }
            if (!hooked.isEmpty()) DexKitCache.saveMethods("VideoUrlCapture", hooked);
        } catch (Throwable e) {
            ModuleLog.line("(IE|DL|DexKit) ❌ installVideoUrlCaptureHook: " + e.getMessage());
        }

        resolveUsernameGetter(bridge, classLoader);
    }

    /**
     * Uses DexKit to find the user class (via "username_missing_during_update") and then
     * locates the no-arg method on MutableMediaDictIntf (or its superinterfaces) that
     * returns an instance of that class. This gives us a stable way to get the post author
     * from the LiveTreeMediaDict without guessing obfuscated method names.
     */
    private static void resolveUsernameGetter(DexKitBridge bridge, ClassLoader classLoader) {
        // Cache hit: restore userClass and userUsernameGetter without DexKit
        if (DexKitCache.isCacheValid()) {
            String cachedClassName = DexKitCache.loadString("UserClass");
            Method cachedGetter    = DexKitCache.loadMethod("UsernameGetter", classLoader);
            if (cachedClassName != null) {
                try {
                    userClass = classLoader.loadClass(cachedClassName);
                    if (cachedGetter != null) {
                        UserUtils.userUsernameGetter = cachedGetter;
                    }
                    resolveDictUserGetter(bridge, classLoader);
                    return;
                } catch (Throwable ignored) {}
            }
        }

        try {
            // Step 1: find the user class via the stable validation string
            List<MethodData> userMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("username_missing_during_update")));

            if (userMethods.isEmpty()) {
                ModuleLog.line("(IE|DL|Username) ❌ username_missing_during_update not found");
                return;
            }

            userClass = userMethods.get(0).getMethodInstance(classLoader).getDeclaringClass();
            DexKitCache.saveString("UserClass", userClass.getName());
            ModuleLog.line("(IE|DL|Username) userClass=" + userClass.getName());

            // Resolve the username getter on User via the stable GraphQL field ID -265713450.
            try {
                List<MethodData> ugMethods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .declaredClass("com.instagram.user.model.User")
                                .returnType("java.lang.String")
                                .paramCount(0)
                                .usingNumbers(-265713450)));
                if (!ugMethods.isEmpty()) {
                    UserUtils.userUsernameGetter = ugMethods.get(0).getMethodInstance(classLoader);
                    UserUtils.userUsernameGetter.setAccessible(true);
                    DexKitCache.saveMethod("UsernameGetter", UserUtils.userUsernameGetter);
                    ModuleLog.line("(IE|DL|Username) userUsernameGetter=" + UserUtils.userUsernameGetter.getName());
                } else {
                    ModuleLog.line("(IE|DL|Username) ❌ userUsernameGetter not found via -265713450");
                }
            } catch (Throwable t) {
                ModuleLog.line("(IE|DL|Username) ❌ userUsernameGetter resolution: " + t);
            }

            resolveDictUserGetter(bridge, classLoader);

        } catch (Throwable t) {
            ModuleLog.line("(IE|DL|Username) ❌ resolveUsernameGetter: " + t);
        }
    }

    private static void resolveDictUserGetter(DexKitBridge bridge, ClassLoader classLoader) {
        if (mutableMediaDictIntfClass == null || userClass == null) return;

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("DictUserGetter", classLoader);
            if (cached != null) {
                dictUserGetter = cached;
                return;
            }
        }

        // Use a Breadth-First Search to find the getter in the interface hierarchy
        // Instagram 423+ often hides this in a parent interface like X.IdM
        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(mutableMediaDictIntfClass);

        while (!queue.isEmpty()) {
            Class<?> curr = queue.poll();
            if (curr == null || !visited.add(curr)) continue;

            for (Method m : curr.getDeclaredMethods()) {
                // We are looking for the method that returns the User class
                // we found via "username_missing_during_update"
                if (m.getParameterCount() == 0 && m.getReturnType().equals(userClass)) {
                    m.setAccessible(true);
                    dictUserGetter = m;
                    DexKitCache.saveMethod("DictUserGetter", m);
                    ModuleLog.line("(IE|DL|Username) ✅ Resolved dictUserGetter: " + m.getName());
                    return;
                }
            }
            // Add parent interfaces to the queue
            Collections.addAll(queue, curr.getInterfaces());
        }

        // Instagram 437+ moved nearly all Pando field accessors off the interface and
        // onto the concrete backing class (LiveTreeMediaDict, which implements
        // MutableMediaDictIntf) — same as the carousel-candidate accessors. That class
        // has SEVERAL zero-arg User-returning methods though (owner, group creator,
        // reshared-story author, previous submitter, ...) — a plain reflection scan
        // picks whichever comes first in declaration order, which isn't reliably the
        // post's actual author. Use DexKit to find the specific one that checks the
        // generic Pando "user" field (the one Instagram's own code uses for post
        // authorship, e.g. QpF's own-post check) rather than "owner"/"group"/etc.
        try {
            List<MethodData> results = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass("com.instagram.feed.media.LiveTreeMediaDict")
                            .paramCount(0)
                            .returnType(userClass)
                            .usingEqStrings(java.util.List.of("user"))));

            if (results.isEmpty()) {
                results = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramCount(0)
                                .returnType(userClass)
                                .usingEqStrings(java.util.List.of("user"))));
            }

            for (MethodData md : results) {
                try {
                    String cn = md.getClassName();
                    if (!cn.contains("MediaDict") && !cn.contains("LiveTree") && !cn.contains("feed.media")) continue;
                    Method m = md.getMethodInstance(classLoader);
                    m.setAccessible(true);
                    dictUserGetter = m;
                    DexKitCache.saveMethod("DictUserGetter", m);
                    ModuleLog.line("(IE|DL|Username) ✅ Resolved dictUserGetter (concrete class): " + m.getName());
                    return;
                } catch (Throwable ignored) {}
            }

            if (!results.isEmpty()) {
                Method m = results.get(0).getMethodInstance(classLoader);
                m.setAccessible(true);
                dictUserGetter = m;
                DexKitCache.saveMethod("DictUserGetter", m);
                ModuleLog.line("(IE|DL|Username) ✅ Resolved dictUserGetter (concrete class): " + m.getName());
                return;
            }
        } catch (Throwable t) {
            ModuleLog.line("(IE|DL|Username) ❌ dictUserGetter DexKit lookup: " + t);
        }

        ModuleLog.line("(IE|DL|Username) ❌ Failed to resolve dictUserGetter in hierarchy");
    }

    // ── Download dispatch ─────────────────────────────────────────────────────

    /**
     * Resolves the post author's username by scanning the media object already captured
     * in the save/like button's click listener closure.
     * Strategy: like button listener → if no media, walk up to save button → then scan
     * the media object graph (depth ≤ 2) for an object with getUsername().
     */
    @SuppressLint("DiscouragedApi")
    private String getUsernameFromView(View likeBtn) {
        if (likeBtn == null || mediaClass == null) return null;

        Object media = getMediaFromListener(getOnClickListener(likeBtn));

        // Fallback to save button if like button listener is empty
        if (media == null) {
            Context ctx = likeBtn.getContext();
            int saveResId = ctx.getResources().getIdentifier("row_feed_button_save", "id", ctx.getPackageName());
            if (saveResId != 0) {
                android.view.ViewParent p = likeBtn.getParent();
                for (int i = 0; i < 4 && p instanceof ViewGroup vg; i++, p = vg.getParent()) {
                    View saveBtn = vg.findViewById(saveResId);
                    if (saveBtn != null) {
                        media = getMediaFromListener(getOnClickListener(saveBtn));
                        if (media != null) break;
                    }
                }
            }
        }

        if (media == null) return null;

        // TIER 1: Use the resolved Dictionary Getter
        if (dictUserGetter != null && mutableMediaDictIntfClass != null) {
            try {
                Object dictIntf = findFieldAssignableTo(media, mutableMediaDictIntfClass);
                if (dictIntf != null) {
                    Object userObj = dictUserGetter.invoke(dictIntf);
                    if (userObj != null) {
                        String name = UserUtils.callUsernameGetter(userObj);
                        if (name != null) return name;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // TIER 2: Direct Class Bridge (Best for newer LiveTree versions)
        // If we can't find the dictionary, search the Media object for ANY field
        // that matches the User class directly.
        Object userObj = findFieldOfType(media, userClass, 3);
        if (userObj != null) {
            String name = UserUtils.callUsernameGetter(userObj);
            if (name != null) return name;
        }

        // TIER 3: Last resort recursive scan
        return scanObjectForUsername(media, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Object getMediaFromListener(Object listener) {
        if (listener == null || mediaClass == null) return null;
        return findFieldOfType(listener, mediaClass, 4);
    }

    /** Extracts the short media ID (first segment of the Instagram ID) from the view's media object. */
    @SuppressLint("DiscouragedApi")
    private String getMediaIdFromView(View likeBtn) {
        if (likeBtn == null || mediaClass == null) return null;
        try {
            Object media = getMediaFromListener(getOnClickListener(likeBtn));
            if (media == null) {
                Context ctx = likeBtn.getContext();
                int saveResId = ctx.getResources().getIdentifier("row_feed_button_save", "id", ctx.getPackageName());
                if (saveResId != 0) {
                    android.view.ViewParent p = likeBtn.getParent();
                    for (int i = 0; i < 4 && p instanceof ViewGroup vg; i++, p = vg.getParent()) {
                        View saveBtn = vg.findViewById(saveResId);
                        if (saveBtn != null) {
                            media = getMediaFromListener(getOnClickListener(saveBtn));
                            if (media != null) break;
                        }
                    }
                }
            }
            if (media == null) return null;
            Object id = media.getClass().getMethod("getId").invoke(media);
            if (id instanceof String s && !s.isEmpty()) return s.split("_")[0];
        } catch (Throwable ignored) {}
        return null;
    }

    // ── Filename + directory helpers (package-accessible for StoryDownloadHook) ──

    static String buildFilename(String username, String type, String mediaId, boolean isVideo) {
        return buildFilename(username, type, mediaId, isVideo, -1);
    }

    static String buildFilename(String username, String type, String mediaId, boolean isVideo, int slideIndex) {
        String u  = (username != null && !username.isEmpty()) ? username : "unknown";
        String id = (mediaId  != null && !mediaId.isEmpty())  ? mediaId  : String.valueOf(System.currentTimeMillis());
        String ext = isVideo ? ".mp4" : ".jpg";
        StringBuilder sb = new StringBuilder(u).append('_').append(type).append('_').append(id);
        if (slideIndex >= 0) sb.append('_').append(slideIndex + 1);
        if (FeatureFlags.downloaderAddTimestamp) {
            sb.append('_').append(new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
        }
        return sb.append(ext).toString();
    }

    /**
     * Opens a writable OutputStream for the download destination, handling all storage strategies:
     *   1. Raw file path (custom folder, avoids SAF authority issues when URI was granted to companion app)
     *   2. SAF tree URI (works when folder was picked from inside Instagram's own dialog)
     *   3. MediaStore Downloads (API 29+, default scoped-storage path)
     *   4. Legacy direct file (API < 29)
     */
    static OutputStream openOutputStream(Context ctx, String filename, boolean isVideo, String username)
            throws Exception {
        String mimeType = isVideo ? "video/mp4" : "image/jpeg";

        // 1. Raw path — preferred when set; bypasses SAF authority entirely
        if (!FeatureFlags.downloaderCustomPath.isEmpty()) {
            try {
                return openRawPathOutputStream(filename, username);
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|DL) Raw path failed, trying SAF: " + e.getMessage());
            }
        }

        // 2. SAF — only works when the folder was picked inside Instagram's process
        //    (so Instagram holds the persistable URI permission, not the companion app)
        if (!FeatureFlags.downloaderCustomUri.isEmpty()) {
            try {
                return openSafOutputStream(ctx, filename, mimeType, username);
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|DL) SAF failed, falling back to MediaStore: " + e.getMessage());
            }
        }

        // 3. MediaStore (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openMediaStoreOutputStream(ctx, filename, mimeType, username);
        }

        // 4. Legacy API < 29: direct file write
        File dir = new File(Environment.getExternalStorageDirectory(), "InstaEclipse");
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new FileOutputStream(new File(dir, filename));
    }

    private static OutputStream openRawPathOutputStream(String filename, String username) throws Exception {
        String rawPath = FeatureFlags.downloaderCustomPath;
        // Reject if path conversion failed and we got a content URI string as fallback
        if (rawPath.startsWith("content://")) {
            throw new Exception("Not a raw file path: " + rawPath);
        }
        File dir = new File(rawPath);
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Cannot create dir: " + dir.getAbsolutePath());
        }
        return new FileOutputStream(new File(dir, filename));
    }

    private static OutputStream openSafOutputStream(Context ctx, String filename, String mimeType, String username)
            throws Exception {
        Uri treeUri = Uri.parse(FeatureFlags.downloaderCustomUri);
        String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dirUri = findOrCreateSafDir(ctx, treeUri, rootDocId, username);
        }
        Uri fileUri = DocumentsContract.createDocument(ctx.getContentResolver(), dirUri, mimeType, filename);
        if (fileUri == null) throw new Exception("SAF createDocument returned null");
        OutputStream out = ctx.getContentResolver().openOutputStream(fileUri);
        if (out == null) throw new Exception("SAF openOutputStream returned null");
        return out;
    }

    private static Uri findOrCreateSafDir(Context ctx, Uri treeUri, String parentDocId, String dirName)
            throws Exception {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        try (Cursor c = ctx.getContentResolver().query(childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                             DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (dirName.equals(c.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
                }
            }
        }
        Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId);
        Uri newDir = DocumentsContract.createDocument(ctx.getContentResolver(), parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR, dirName);
        if (newDir == null) throw new Exception("SAF createDocument (dir) returned null");
        return newDir;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("NewApi")
    private static OutputStream openMediaStoreOutputStream(Context ctx, String filename, String mimeType, String username)
            throws Exception {
        String relPath = buildMediaStoreRelPath(username);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri itemUri = ctx.getContentResolver().insert(collection, values);
        if (itemUri == null) throw new Exception("MediaStore insert failed");
        OutputStream out = ctx.getContentResolver().openOutputStream(itemUri);
        if (out == null) throw new Exception("MediaStore openOutputStream returned null");
        return out;
    }

    // Standard top-level directories that MediaStore.Downloads accepts as RELATIVE_PATH roots
    private static final java.util.Set<String> MS_ROOTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Download", "Downloads", "Pictures", "DCIM", "Movies", "Music",
            "Ringtones", "Alarms", "Notifications", "Podcasts", "Audiobooks"));

    /**
     * Derives the MediaStore RELATIVE_PATH for the download.
     * - If the custom path falls under a known MediaStore root (Download, Pictures, …),
     *   it is used directly (e.g. Pictures/IG).
     * - Otherwise the path is nested under Download/ (e.g. /sdcard/Test55 → Download/Test55).
     * - Falls back to Download/InstaEclipse when no custom path is set.
     */
    private static String buildMediaStoreRelPath(String username) {
        String customPath = FeatureFlags.downloaderCustomPath;
        String base = "Download/InstaEclipse"; // default

        if (!customPath.isEmpty() && !customPath.startsWith("content://")) {
            String extBase = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (customPath.startsWith(extBase + "/")) {
                String relative = customPath.substring(extBase.length() + 1); // e.g. "Test55" or "Pictures/IG"
                String topLevel = relative.split("/")[0];
                base = MS_ROOTS.contains(topLevel) ? relative : ("Download/" + relative);
            }
        }

        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            base += "/" + username;
        }
        return base;
    }

    // ── Dedup: skip re-downloading a file that's already on disk ───────────────

    static boolean isDownloaded(Context ctx, String filename, boolean isVideo, String username) {
        try {
            File raw = resolveRawPathFile(filename, username);
            if (raw != null && raw.isFile() && raw.length() > 0) return true;
            File legacy = resolveLegacyFile(filename, username);
            if (legacy.isFile() && legacy.length() > 0) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && isInMediaStore(ctx, filename, username)) return true;
            if (!FeatureFlags.downloaderCustomUri.isEmpty()
                    && isInSaf(ctx, filename, username)) return true;
            String[] stemAndExt = stemAndExtFromFilename(filename);
            if (stemAndExt == null) return false;
            String stem = stemAndExt[0], ext = stemAndExt[1];
            if (hasTimestampVariantInDir(resolveRawPathDir(username), stem, ext)) return true;
            if (hasTimestampVariantInDir(resolveLegacyDir(username), stem, ext)) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && isTimestampVariantInMediaStore(ctx, stem, ext, username)) return true;
            if (!FeatureFlags.downloaderCustomUri.isEmpty()
                    && isTimestampVariantInSaf(ctx, stem, ext, username)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static File resolveRawPathFile(String filename, String username) {
        String rawPath = FeatureFlags.downloaderCustomPath;
        if (rawPath.isEmpty() || rawPath.startsWith("content://")) return null;
        File dir = new File(rawPath);
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        return new File(dir, filename);
    }

    private static File resolveLegacyFile(String filename, String username) {
        File dir = new File(Environment.getExternalStorageDirectory(), "InstaEclipse");
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        return new File(dir, filename);
    }

    private static File resolveRawPathDir(String username) {
        String rawPath = FeatureFlags.downloaderCustomPath;
        if (rawPath.isEmpty() || rawPath.startsWith("content://")) return null;
        File dir = new File(rawPath);
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        return dir.isDirectory() ? dir : null;
    }

    private static File resolveLegacyDir(String username) {
        File dir = new File(Environment.getExternalStorageDirectory(), "InstaEclipse");
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        return dir;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static boolean isInMediaStore(Context ctx, String filename, String username) {
        String relPath = buildMediaStoreRelPath(username) + "%";
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {filename, relPath};
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            return c != null && c.moveToFirst();
        }
    }

    private static boolean isInSaf(Context ctx, String filename, String username) {
        try {
            Uri treeUri = Uri.parse(FeatureFlags.downloaderCustomUri);
            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
            if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
                dirUri = findSafDirUri(ctx, treeUri, rootDocId, username);
                if (dirUri == null) return false;
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getDocumentId(dirUri));
            try (Cursor c = ctx.getContentResolver().query(childrenUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    if (filename.equals(c.getString(0))) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static boolean isTimestampVariantInMediaStore(Context ctx, String stem, String ext, String username) {
        String relPath = buildMediaStoreRelPath(username) + "%";
        String like = stem + "_%" + ext;
        String exact = stem + ext;
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = "(" + MediaStore.MediaColumns.DISPLAY_NAME + "=? OR "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?) AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {exact, like, relPath};
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            return c != null && c.moveToFirst();
        }
    }

    private static boolean isTimestampVariantInSaf(Context ctx, String stem, String ext, String username) {
        try {
            Uri treeUri = Uri.parse(FeatureFlags.downloaderCustomUri);
            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
            if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
                dirUri = findSafDirUri(ctx, treeUri, rootDocId, username);
                if (dirUri == null) return false;
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getDocumentId(dirUri));
            String exact = stem + ext;
            try (Cursor c = ctx.getContentResolver().query(childrenUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String name = c.getString(0);
                    if (name == null) continue;
                    if (exact.equals(name) || isTimestampedVariantName(name, stem, ext)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Uri findSafDirUri(Context ctx, Uri treeUri, String parentDocId, String dirName) {
        try {
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
            try (Cursor c = ctx.getContentResolver().query(childrenUri,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                 DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                while (c != null && c.moveToNext()) {
                    if (dirName.equals(c.getString(1))) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String[] stemAndExtFromFilename(String filename) {
        Matcher matcher = TIMESTAMPED_FILENAME_PATTERN.matcher(filename);
        if (matcher.matches()) {
            return new String[]{matcher.group(1), matcher.group(3)};
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return null;
        String ext = filename.substring(dot);
        String base = filename.substring(0, dot);
        int lastUnderscore = base.lastIndexOf('_');
        if (lastUnderscore > 0 && isTimestampToken(base.substring(lastUnderscore + 1))) {
            return new String[]{base.substring(0, lastUnderscore), ext};
        }
        return new String[]{base, ext};
    }

    private static boolean isTimestampToken(String token) {
        if (token.length() != 15) return false;
        if (token.charAt(8) != '_') return false;
        for (int i = 0; i < token.length(); i++) {
            if (i == 8) continue;
            char ch = token.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static boolean isTimestampedVariantName(String name, String stem, String ext) {
        if (!name.endsWith(ext) || !name.startsWith(stem + "_")) return false;
        int start = stem.length() + 1;
        int end = name.length() - ext.length();
        if (start >= end) return false;
        return isTimestampToken(name.substring(start, end));
    }

    private static boolean hasTimestampVariantInDir(File dir, String stem, String ext) {
        if (dir == null || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        String exact = stem + ext;
        for (File file : files) {
            if (!file.isFile() || file.length() <= 0) continue;
            String name = file.getName();
            if (exact.equals(name) || isTimestampedVariantName(name, stem, ext)) return true;
        }
        return false;
    }

    // ── Dedup for profile pictures: no timestamp suffix, ambiguous extension ───

    static String profileFilenameStem(String username, String mediaId) {
        String u = (username != null && !username.isEmpty()) ? username : "unknown";
        String id = (mediaId != null && !mediaId.isEmpty()) ? mediaId : u;
        return u + "_profile_" + id;
    }

    static boolean isProfileDownloaded(Context ctx, String username, String mediaId) {
        String filename = buildFilename(username, "profile", mediaId, false);
        if (isDownloaded(ctx, filename, false, username)) return true;
        String stem = profileFilenameStem(username, mediaId);
        try {
            File rawDir = resolveRawPathDir(username);
            if (rawDir != null && profileStemExistsInDir(rawDir, stem)) return true;
            File legacyDir = resolveLegacyDir(username);
            if (profileStemExistsInDir(legacyDir, stem)) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && profileStemInMediaStore(ctx, stem, username)) return true;
            if (!FeatureFlags.downloaderCustomUri.isEmpty()
                    && profileStemInSaf(ctx, stem, username)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean profileStemExistsInDir(File dir, String stem) {
        if (dir == null || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        String stemLower = stem.toLowerCase(Locale.US);
        for (File f : files) {
            if (!f.isFile() || f.length() <= 0) continue;
            String name = f.getName().toLowerCase(Locale.US);
            if (name.startsWith(stemLower) && isProfileImageName(name)) return true;
        }
        return false;
    }

    private static boolean isProfileImageName(String lowerName) {
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png");
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static boolean profileStemInMediaStore(Context ctx, String stem, String username) {
        String relPath = buildMediaStoreRelPath(username) + "%";
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {stem + "%", relPath};
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)) {
            return c != null && c.moveToFirst();
        }
    }

    private static boolean profileStemInSaf(Context ctx, String stem, String username) {
        try {
            Uri treeUri = Uri.parse(FeatureFlags.downloaderCustomUri);
            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId);
            if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
                dirUri = findSafDirUri(ctx, treeUri, rootDocId, username);
                if (dirUri == null) return false;
            }
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getDocumentId(dirUri));
            String stemLower = stem.toLowerCase(Locale.US);
            try (Cursor c = ctx.getContentResolver().query(childrenUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    String name = c.getString(0);
                    if (name == null) continue;
                    String lower = name.toLowerCase(Locale.US);
                    if (lower.startsWith(stemLower) && isProfileImageName(lower)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Copies tempFile to the download destination (only used when no custom SAF URI is set). */
    static void saveFileToDestination(Context ctx, File tempFile, String filename,
                                      boolean isVideo, String username) throws Exception {
        try (FileInputStream in = new FileInputStream(tempFile);
             OutputStream out = openOutputStream(ctx, filename, isVideo, username)) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    /**
     * Reads the companion app's latest SAF URI from its shared prefs WITHOUT overwriting
     * FeatureFlags — callers decide what to do with the value.
     */
    private static String readCompanionUri() {
        try {
            de.robv.android.xposed.XSharedPreferences cp =
                    new de.robv.android.xposed.XSharedPreferences(
                            "ps.reso.instaeclipse", "instaeclipse_cache");
            cp.reload();
            return cp.getString("downloaderCustomUri", "");
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Downloads {@code url} and saves it with the configured destination.
     *
     * When a custom SAF URI is configured, the CDN URL is forwarded to
     * {@link DownloadSaveService} in the companion-app process — it holds the SAF
     * permission (granted when the user picked the folder in FeaturesFragment) and writes
     * the file directly.  No file-descriptor passing across UIDs is required.
     *
     * @return {@code true} when delegated (async — service shows its own toast).
     */
    static boolean downloadAndSave(Context ctx, String url, String filename,
                                   boolean isVideo, String username) throws Exception {
        if (isDownloaded(ctx, filename, isVideo, username)) {
            mainHandler.post(() -> Toast.makeText(ctx,
                    I18n.t(ctx, R.string.ig_toast_already_downloaded), Toast.LENGTH_SHORT).show());
            return true;
        }

        recordDownloadHistory(filename, username);

        // Prefer FeatureFlags (live value synced from companion via broadcast).
        // Fall back to reading companion cache directly (missed-broadcast / cold-start case).
        String uri = FeatureFlags.downloaderCustomUri.isEmpty()
                ? readCompanionUri()
                : FeatureFlags.downloaderCustomUri;

        if (!uri.isEmpty()) {
            delegateUrlToCompanionApp(ctx, url, null, filename, isVideo, username);
            return true;
        }

        // No custom folder configured → MediaStore / raw path.
        try (OutputStream out = openOutputStream(ctx, filename, isVideo, username)) {
            downloadToStream(url, out);
        }
        return false;
    }

    /**
     * Derives the download "type" (post/story/profile) from the filename buildFilename()
     * produced — "{username}_{type}_{id}[_timestamp].ext" — by stripping the known
     * username prefix rather than splitting on '_' blindly, since usernames can contain
     * underscores themselves.
     */
    private static void recordDownloadHistory(String filename, String username) {
        try {
            String uname = (username != null && !username.isEmpty()) ? username : "unknown";
            String prefix = uname + "_";
            String type;
            if (filename.startsWith(prefix)) {
                String rest = filename.substring(prefix.length());
                int underscore = rest.indexOf('_');
                type = underscore >= 0 ? rest.substring(0, underscore) : rest;
            } else {
                type = "post";
            }
            DownloadHistory.record(type, username, filename);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Starts {@link DownloadSaveService} in the companion-app process, passing the CDN
     * URL(s) as plain string extras — no file descriptors cross the process boundary.
     * The service downloads the media itself and writes to the SAF folder it already owns.
     *
     * @param audioUrl non-null to request a video+audio merge inside the service
     */
    private static void delegateUrlToCompanionApp(Context ctx,
                                                   String url,
                                                   String audioUrl,
                                                   String filename, boolean isVideo,
                                                   String username) throws Exception {
        android.content.Intent intent = new android.content.Intent();
        intent.setClassName("ps.reso.instaeclipse",
                            "ps.reso.instaeclipse.mods.media.DownloadSaveService");
        intent.putExtra("url",      url);
        if (audioUrl != null) intent.putExtra("audioUrl", audioUrl);
        intent.putExtra("filename", filename);
        intent.putExtra("mimeType", isVideo ? "video/mp4" : "image/jpeg");
        intent.putExtra("username", username);
        ctx.startForegroundService(intent);
        ModuleLog.line("(IE|DL) Delegated to DownloadSaveService: " + filename);
    }

    /**
     * Package-accessible: collects Instagram CDN media URLs from the given object graph.
     * Used by PostDownloadContextMenuHook as a fallback URL source.
     */
    static List<String> collectCdnUrls(Object obj) {
        List<String> out = new ArrayList<>();
        scanForCdnUrls(obj, out, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        return out;
    }

    /**
     * Package-accessible: extracts the image URL from a Media object using MediaExtKt helper.
     * Returns null if not available (e.g. MediaExtKt not resolved or media is a video-only post).
     */
    static String imageUrlFromMedia(Context ctx, Object media) {
        if (methodImageUrl == null || ctx == null || media == null) return null;
        try {
            Object r = methodImageUrl.invoke(null, ctx, media);
            return (r instanceof String s && isCdnMediaUrl(s)) ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static List<String> extractAllUrlsFromMedia(Context ctx, Object media) {
        if (media == null) return new ArrayList<>();

        List<String> bestUrls = null;
        int bestScore = 0;
        for (List<?> items : collectCarouselCandidateLists(media)) {
            List<String> urls = urlsFromCarouselItems(ctx, items);
            int unique = uniqueMediaIdentityCount(urls);
            ModuleLog.line("(IE|Car) listSize=" + items.size()
                    + " urls=" + urls.size() + " unique=" + unique
                    + " mediaTyped=" + isMediaTypedList(items));
            if (unique < 2) continue;
            int score = unique * 100 + urls.size();
            if (isMediaTypedList(items)) score += 50;
            if (score > bestScore) {
                bestScore = score;
                bestUrls = urls;
            }
        }
        if (bestUrls != null) return bestUrls;

        String single = extractSingleUrlFromMedia(ctx, media);
        if (single != null) return new ArrayList<>(List.of(single));

        List<String> cdnUrls = collectCdnUrls(media);
        if (!cdnUrls.isEmpty()) {
            String bestVid = pickBestVideoUrl(cdnUrls);
            if (bestVid != null && isVideoUrl(bestVid)) return new ArrayList<>(List.of(bestVid));
            return new ArrayList<>(List.of(pickBestImageUrl(cdnUrls)));
        }
        return new ArrayList<>();
    }

    private static List<String> urlsFromCarouselItems(Context ctx, List<?> items) {
        List<String> urls = new ArrayList<>();
        int n = Math.min(items.size(), CAROUSEL_MAX_ITEMS);
        for (int i = 0; i < n; i++) {
            Object item = items.get(i);
            if (item == null) continue;
            String url = extractSingleUrlFromMedia(ctx, item);
            if (url != null) urls.add(url);
        }
        return urls;
    }

    private static int uniqueMediaIdentityCount(List<String> urls) {
        Set<String> ids = new HashSet<>();
        for (String u : urls) {
            String id = mediaUrlIdentity(u);
            if (!id.isEmpty()) ids.add(id);
        }
        return ids.size();
    }

    private static String mediaUrlIdentity(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    static String extractSingleUrlFromMedia(Context ctx, Object media) {
        if (media == null) return null;
        String url = extractSingleUrlDirect(ctx, media);
        if (url != null) return url;
        touchListGetters(media);
        url = extractSingleUrlDirect(ctx, media);
        if (url != null) return url;
        if (mediaClass != null && !mediaClass.isInstance(media)) {
            Object nested = findFieldOfType(media, mediaClass, 2);
            if (nested != null && nested != media) {
                url = extractSingleUrlDirect(ctx, nested);
                if (url != null) return url;
            }
        }
        return null;
    }

    private static String extractSingleUrlDirect(Context ctx, Object media) {
        if (media == null) return null;
        String videoUrl = bestVideoUrlFromMedia(media);
        if (videoUrl != null) return videoUrl;
        String imageUrl = bestImageUrlFromMedia(ctx, media);
        if (imageUrl != null) return imageUrl;
        String probed = probeCdnUrlViaStringMethods(media);
        if (probed != null) return probed;
        List<String> scanned = collectCdnUrls(media);
        if (scanned.isEmpty()) return null;
        return pickBestImageUrl(scanned);
    }

    private static void touchListGetters(Object host) {
        if (host == null) return;
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            String cn = cls.getName();
            if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) break;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !List.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(host);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    static List<?> findCarouselItemList(Object media) {
        List<?> best = null;
        int bestSize = 0;
        boolean bestIsMedia = false;
        for (List<?> list : collectCarouselCandidateLists(media)) {
            boolean isMedia = isMediaTypedList(list);
            int size = list.size();
            if (best == null
                    || (isMedia && !bestIsMedia)
                    || (isMedia == bestIsMedia && size > bestSize)) {
                best = list;
                bestSize = size;
                bestIsMedia = isMedia;
            }
        }
        return best;
    }

    private static List<List<?>> collectCarouselCandidateLists(Object media) {
        List<List<?>> out = new ArrayList<>();
        if (media == null) return out;
        Set<Object> seenLists = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> hosts = new ArrayList<>();
        hosts.add(media);
        if (mutableMediaDictIntfClass != null) {
            Object dict = findFieldAssignableTo(media, mutableMediaDictIntfClass);
            if (dict != null) hosts.add(dict);
        }
        for (Object host : hosts) {
            addCarouselListsFromMethods(host, out, seenLists);
            addCarouselListsFromFields(host, out, seenLists);
        }
        return out;
    }

    private static void addCarouselListsFromMethods(Object host, List<List<?>> out, Set<Object> seenLists) {
        if (host == null) return;
        Set<String> seen = new HashSet<>();
        if (!carouselCandidates.isEmpty() && mutableMediaDictIntfClass != null
                && mutableMediaDictIntfClass.isInstance(host)) {
            for (Method candidate : carouselCandidates) {
                if (!seen.add(candidate.getName())) continue;
                addIfCarouselList(invokeAsCarouselList(candidate, host), out, seenLists);
            }
        }
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !List.class.isAssignableFrom(m.getReturnType())) continue;
                if (!seen.add(m.getName())) continue;
                try {
                    m.setAccessible(true);
                    addIfCarouselList(invokeAsCarouselList(m, host), out, seenLists);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void addCarouselListsFromFields(Object host, List<List<?>> out, Set<Object> seenLists) {
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(host);
                    if (v instanceof List<?> list) addIfCarouselList(list, out, seenLists);
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void addIfCarouselList(List<?> list, List<List<?>> out, Set<Object> seenLists) {
        if (list == null || !isCarouselItemList(list) || !seenLists.add(list)) return;
        out.add(list);
    }

    private static List<?> invokeAsCarouselList(Method m, Object host) {
        try {
            Object listObj = m.invoke(host);
            if (listObj instanceof List<?> list && isCarouselItemList(list)) return list;
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isMediaTypedList(List<?> items) {
        if (items == null || mediaClass == null) return false;
        for (Object item : items) {
            if (item != null) return mediaClass.isInstance(item);
        }
        return false;
    }

    private static boolean isCarouselItemList(List<?> items) {
        if (items == null || items.size() < 2 || items.size() > CAROUSEL_MAX_ITEMS) return false;
        Object first = null;
        for (Object item : items) {
            if (item != null) { first = item; break; }
        }
        if (first == null) return false;
        if (videoVersionIntfClass != null && videoVersionIntfClass.isInstance(first)) return false;
        if (imageUrlClass != null && imageUrlClass.isInstance(first)) return false;
        if (imageInfoClass != null && imageInfoClass.isInstance(first)) return false;
        if (userClass != null && userClass.isInstance(first)) return false;
        if (mediaClass != null && mediaClass.isInstance(first)) return true;
        if (mutableMediaDictIntfClass != null && mutableMediaDictIntfClass.isInstance(first)) return true;
        String cn = first.getClass().getName();
        return cn.startsWith("X.") || cn.startsWith("com.instagram.") || cn.startsWith("com.facebook.");
    }

    static String bestImageUrlFromMedia(Context ctx, Object media) {
        if (media == null) return null;
        List<String> urls = new ArrayList<>();
        List<Integer> areas = new ArrayList<>();
        collectImageUrlCandidates(media, urls, areas,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        String ext = imageUrlFromMedia(ctx, media);
        if (ext != null) {
            urls.add(ext);
            areas.add(parseUrlArea(ext));
        }
        if (urls.isEmpty()) return null;
        int best = 0;
        for (int i = 1; i < urls.size(); i++) {
            if (areas.get(i) > areas.get(best)) best = i;
        }
        return urls.get(best);
    }

    private static void collectImageUrlCandidates(Object obj, List<String> urls, List<Integer> areas,
                                                   Set<Object> visited, int depth) {
        if (obj == null || depth > 6 || urls.size() >= 40) return;
        if (depth > 0 && mediaClass != null && mediaClass.isInstance(obj)) return;
        if (!visited.add(obj)) return;

        if (imageUrlClass != null && imageUrlClass.isInstance(obj)) {
            String url = tryGetUrl(obj);
            if (url != null && isCdnMediaUrl(url) && !isVideoUrl(url)) {
                urls.add(url);
                areas.add(imageUrlArea(obj, url));
            }
            return;
        }

        if (imageInfoClass != null && imageInfoClass.isInstance(obj)) {
            collectFromImageInfo(obj, urls, areas);
        }

        String cn = obj.getClass().getName();
        if (cn.startsWith("android.") || cn.startsWith("java.lang.") || cn.startsWith("kotlin.")) return;
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) return;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof List<?> list) {
                        for (Object item : list)
                            collectImageUrlCandidates(item, urls, areas, visited, depth + 1);
                    } else {
                        collectImageUrlCandidates(val, urls, areas, visited, depth + 1);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void collectFromImageInfo(Object imageInfo, List<String> urls, List<Integer> areas) {
        Class<?> cls = imageInfo.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || !List.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(imageInfo);
                    if (!(r instanceof List<?> list)) continue;
                    for (Object item : list) {
                        if (item == null) continue;
                        if (imageUrlClass != null && imageUrlClass.isInstance(item)) {
                            String url = tryGetUrl(item);
                            if (url != null && isCdnMediaUrl(url) && !isVideoUrl(url)) {
                                urls.add(url);
                                areas.add(imageUrlArea(item, url));
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static int imageUrlArea(Object imageUrl, String url) {
        try {
            int w = (int) imageUrl.getClass().getMethod("getWidth").invoke(imageUrl);
            int h = (int) imageUrl.getClass().getMethod("getHeight").invoke(imageUrl);
            if (w >= 50 && h >= 50 && w <= 20000 && h <= 20000) return w * h;
        } catch (Throwable ignored) {}
        return parseUrlArea(url);
    }

    static void showPostDownloadDialog(Context ctx, List<String> urls,
                                       String username, String mediaId, int currentIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> showPostDownloadDialog(ctx, urls, username, mediaId, currentIndex));
            return;
        }
        if (urls.isEmpty()) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_post_url_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
        int n = urls.size();
        int safeIdx = (currentIndex >= 0 && currentIndex < n) ? currentIndex : 0;

        if (n == 1 || !FeatureFlags.enableBatchDownload) {
            downloadSinglePostUrl(ctx, urls.get(safeIdx), username, mediaId, n > 1 ? safeIdx : -1);
            return;
        }

        showCarouselChoiceDialog(ctx, urls, username, mediaId, safeIdx);
    }

    private static void showCarouselChoiceDialog(Context ctx, List<String> urls,
                                                 String username, String mediaId, int currentIndex) {
        int n = urls.size();
        StringBuilder message = new StringBuilder(I18n.t(ctx, R.string.ig_dl_carousel_subtitle, n));
        for (int i = 0; i < n; i++) {
            String type = isVideoUrl(urls.get(i))
                    ? I18n.t(ctx, R.string.ig_dl_type_video)
                    : I18n.t(ctx, R.string.ig_dl_type_photo);
            message.append('\n').append(i + 1).append(". ").append(type);
        }

        Context dialogCtx = resolveDialogContext(ctx);
        try {
            new AlertDialog.Builder(dialogCtx)
                    .setTitle(I18n.t(ctx, R.string.ig_dl_title))
                    .setMessage(message.toString())
                    .setPositiveButton(I18n.t(ctx, R.string.ig_dl_carousel_all, n),
                            (d, w) -> downloadAllPostUrls(ctx, urls, username, mediaId))
                    .setNeutralButton(I18n.t(ctx, R.string.ig_dl_carousel_current, currentIndex + 1, n),
                            (d, w) -> downloadSinglePostUrl(ctx, urls.get(currentIndex), username, mediaId, currentIndex))
                    .setNegativeButton(I18n.t(ctx, R.string.ig_dialog_cancel), null)
                    .setCancelable(true)
                    .show();
        } catch (Throwable t) {
            ModuleLog.line("(IE|Post|DL) dialog failed: " + t);
            downloadSinglePostUrl(ctx, urls.get(currentIndex), username, mediaId, currentIndex);
        }
    }

    private static Context resolveDialogContext(Context ctx) {
        Activity a = getActivityFromContext(ctx);
        if (a != null && !a.isFinishing()) return a;
        a = UIHookManager.getCurrentActivity();
        if (a != null && !a.isFinishing()) return a;
        return ctx;
    }

    private static void downloadSinglePostUrl(Context ctx, String url, String username, String mediaId, int slideIndex) {
        boolean isVid = isVideoUrl(url);
        String fn = buildFilename(username, "post", mediaId, isVid, slideIndex);
        Toast.makeText(ctx, isVid ? I18n.t(ctx, R.string.ig_toast_downloading_video) : I18n.t(ctx, R.string.ig_toast_downloading_photo), Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            try {
                boolean delegated = downloadAndSave(ctx, url, fn, isVid, username);
                if (!delegated) {
                    mainHandler.post(() -> Toast.makeText(ctx,
                            isVid ? I18n.t(ctx, R.string.ig_toast_video_saved) : I18n.t(ctx, R.string.ig_toast_photo_saved),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                ModuleLog.line("(IE|Post|DL) single failed: " + e);
                mainHandler.post(() -> Toast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void downloadAllPostUrls(Context ctx, List<String> urls, String username, String mediaId) {
        int n = urls.size();
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading_all_n_items, n), Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            int failed = 0;
            int i = 0;
            for (String url : urls) {
                boolean isVid = isVideoUrl(url);
                String fn = buildFilename(username, "post", mediaId, isVid, i++);
                try {
                    downloadAndSave(ctx, url, fn, isVid, username);
                } catch (Throwable e) {
                    failed++;
                    ModuleLog.line("(IE|Post|DL) item failed: " + e);
                }
            }
            final int finalFailed = failed;
            mainHandler.post(() -> {
                if (finalFailed == 0) {
                    Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_all_items_saved, n),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_items_partial_saved,
                            n - finalFailed, n, finalFailed), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Package-accessible: extracts username from a com.instagram.feed.media.Media object
     * using the DexKit-resolved dictUserGetter. Used by StoryDownloadHook.
     */
    static String extractUsernameFromMediaObject(Object media) {
        if (media == null) return null;
        ensureUserClass(media);

        List<Object> hosts = new ArrayList<>();
        hosts.add(media);
        if (mutableMediaDictIntfClass != null && !mutableMediaDictIntfClass.isInstance(media)) {
            Object dict = findFieldAssignableTo(media, mutableMediaDictIntfClass);
            if (dict != null) hosts.add(dict);
        }

        if (dictUserGetter != null) {
            for (Object host : hosts) {
                try {
                    Object user = dictUserGetter.invoke(host);
                    String name = UserUtils.callUsernameGetter(user);
                    if (name != null) return name;
                } catch (Throwable ignored) {}
            }
        }

        for (Object host : hosts) {
            String fromGetter = usernameFromUserGetters(host);
            if (fromGetter != null) return fromGetter;
            if (userClass != null) {
                Object userObj = findFieldOfType(host, userClass, 3);
                if (userObj != null) {
                    String name = UserUtils.callUsernameGetter(userObj);
                    if (name != null) return name;
                }
            }
        }
        return scanObjectForUsername(media, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void ensureUserClass(Object media) {
        if (userClass != null || media == null) return;
        try {
            userClass = media.getClass().getClassLoader().loadClass("com.instagram.user.model.User");
        } catch (Throwable ignored) {}
    }

    private static boolean isUserType(Class<?> type) {
        if (type == null) return false;
        if (userClass != null && (type == userClass || userClass.isAssignableFrom(type))) return true;
        String n = type.getName();
        return n.equals("com.instagram.user.model.User") || n.endsWith(".user.model.User");
    }

    private static String usernameFromUserGetters(Object host) {
        if (host == null) return null;
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!isUserType(m.getReturnType())) continue;
                try {
                    m.setAccessible(true);
                    Object user = m.invoke(host);
                    String name = UserUtils.callUsernameGetter(user);
                    if (name != null) return name;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    static String extractMediaIdFromMediaObject(Object media) {
        if (media == null) return null;
        try {
            Object id = media.getClass().getMethod("getId").invoke(media);
            if (id instanceof String s && !s.isEmpty()) return s.split("_")[0];
        } catch (Throwable ignored) {}
        return null;
    }

    /** @deprecated Use {@link UserUtils#callUsernameGetter(Object)} directly. */
    @Deprecated
    public static String callUsernameGetter(Object user) {
        return UserUtils.callUsernameGetter(user);
    }

    /**
     * Walks the object graph up to depth 3 looking for any object that has a
     * no-arg getUsername() method returning a valid Instagram username string.
     * At depth 0 (the Media object itself), logs all field names + types to
     * help diagnose where the user object is nested.
     */
    private static String scanObjectForUsername(Object obj, int depth,
                                                 Set<Object> visited) {
        if (obj == null || depth > 3 || visited.contains(obj)) return null;
        visited.add(obj);

        // Try getUsername() on this object directly
        try {
            Object result = obj.getClass().getMethod("getUsername").invoke(obj);
            if (result instanceof String s && !s.isEmpty() && s.matches("[a-zA-Z0-9._]{1,30}")) {
                return s;
            }
        } catch (Throwable ignored) {}

        if (depth >= 3) return null;

        // Scan all non-primitive, non-String, non-array fields — no class filter,
        // rely on depth limit + visited set to prevent runaway recursion
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (ft.isPrimitive() || ft == String.class || ft.isArray()) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (val == null) continue;
                    String u = scanObjectForUsername(val, depth + 1, visited);
                    if (u != null) return u;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private void onDownloadClicked(Context ctx, List<String> urls, View saveBtn) {
        onDownloadClicked(ctx, urls, saveBtn, null);
    }

    private void onDownloadClicked(Context ctx, List<String> urls, View saveBtn, Object media) {
        if (media == null && saveBtn != null) media = saveBtn.getTag(TAG_CACHED_MEDIA);
        if (media == null && saveBtn != null) media = findMediaForView(saveBtn);

        String username = extractUsernameFromMediaObject(media);
        String mediaId = extractMediaIdFromMediaObject(media);
        if (username == null) username = getUsernameFromView(saveBtn);
        if (mediaId == null) mediaId = getMediaIdFromView(saveBtn);
        currentDownloadUsername = username;
        currentDownloadMediaId = mediaId;
        ModuleLog.line("(IE|DL) onDownloadClicked username=" + currentDownloadUsername + " mediaId=" + currentDownloadMediaId);
        List<String> videos = new ArrayList<>();
        List<String> images = new ArrayList<>();
        for (String url : urls) {
            if (isVideoUrl(url)) videos.add(url);
            else                 images.add(url);
        }
        ModuleLog.line("(IE|DL) total=" + urls.size()
                + " videos=" + videos.size() + " images=" + images.size());
        for (int i = 0; i < videos.size(); i++)
            ModuleLog.line("(IE|DL) video[" + i + "]=" + videos.get(i));
        for (int i = 0; i < images.size(); i++)
            ModuleLog.line("(IE|DL) image[" + i + "]=" + images.get(i));

        if (!videos.isEmpty() && !images.isEmpty()) {
            handleMixedContent(ctx, urls, videos, images, saveBtn);
        } else if (!videos.isEmpty()) {
            handleVideoDownload(ctx, videos, saveBtn);
        } else if (images.size() > 1) {
            showCarouselDialog(ctx, images, saveBtn);
        } else if (!images.isEmpty()) {
            startDirectDownload(ctx, images.get(0), false);
        }
    }

    private void handleMixedContent(Context ctx, List<String> allUrls,
                                     List<String> videos, List<String> images, View saveBtn) {
        executor.submit(() -> {
            String videoUrl = videos.get(0);
            TrackInfo t = probeUrl(videoUrl);
            ModuleLog.line("(IE|DL) probeUrl=" + videoUrl
                    + " hasVideo=" + t.hasVideo + " hasAudio=" + t.hasAudio);
            mainHandler.post(() -> {
                if (!t.hasVideo && t.hasAudio) {
                    // Audio-only background track — download the image instead
                    startDirectDownload(ctx, images.get(0), false);
                } else {
                    // Real video mixed with images — show carousel dialog for all items
                    showCarouselDialog(ctx, allUrls, saveBtn);
                }
            });
        });
    }

    private void handleVideoDownload(Context ctx, List<String> videos, View saveBtn) {
        if (videos.size() == 1) {
            startDirectDownload(ctx, videos.get(0), true);
            return;
        }
        // Multiple video URLs → video carousel, show selection dialog immediately.
        // (DASH streams only ever produce a single URL via our Step-A resolver;
        //  multiple URLs always come from Step-B carousel item extraction.)
        showCarouselDialog(ctx, videos, saveBtn);
    }

    private void showCarouselDialog(Context ctx, List<String> urls, View saveBtn) {
        int idx = saveBtn != null ? findCarouselPosition(saveBtn) : 0;
        if (idx >= urls.size()) idx = 0;
        showPostDownloadDialog(ctx, urls, currentDownloadUsername, currentDownloadMediaId, idx);
    }

    private static int findCarouselPosition(View anchor) {
        View container = anchor;
        for (int i = 0; i < 8 && container.getParent() instanceof View; i++) {
            container = (View) container.getParent();
        }
        if (!(container instanceof ViewGroup vg)) return 0;
        int pos = searchForPager(vg, 0);
        return pos >= 0 ? pos : 0;
    }

    private static int searchForPager(ViewGroup group, int depth) {
        if (depth > 8) return -1;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            for (String methodName : new String[]{"getCurrentItem", "getCurrentDataIndex"}) {
                try {
                    Method m = child.getClass().getMethod(methodName);
                    Object r = m.invoke(child);
                    if (r instanceof Integer val && val >= 0) return val;
                } catch (Throwable ignored) {}
            }
            if (child instanceof ViewGroup vg) {
                int r = searchForPager(vg, depth + 1);
                if (r >= 0) return r;
            }
        }
        return -1;
    }

    private void startDirectDownload(Context ctx, String url, boolean isVideo) {
        startDirectDownload(ctx, url, isVideo, -1);
    }

    private void startDirectDownload(Context ctx, String url, boolean isVideo, int slideIndex) {
        String fn = buildFilename(currentDownloadUsername, "post", currentDownloadMediaId, isVideo, slideIndex);
        ModuleLog.line("(IE|DL) startDirectDownload file=" + fn);
        Toast.makeText(ctx, isVideo ? I18n.t(ctx, R.string.ig_toast_downloading_video) : I18n.t(ctx, R.string.ig_toast_downloading_photo), Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            try {
                boolean delegated = downloadAndSave(ctx, url, fn, isVideo, currentDownloadUsername);
                if (!delegated) {
                    mainHandler.post(() -> Toast.makeText(ctx,
                            isVideo ? I18n.t(ctx, R.string.ig_toast_video_saved) : I18n.t(ctx, R.string.ig_toast_photo_saved),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                ModuleLog.line("(IE|DL) download failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                mainHandler.post(() -> Toast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void downloadAndMerge(Context ctx, String videoUrl, String audioUrl) {
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_merging_video_audio), Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            // Companion always holds the SAF permission — delegate whenever a URI is set.
            String uri = FeatureFlags.downloaderCustomUri.isEmpty()
                    ? readCompanionUri()
                    : FeatureFlags.downloaderCustomUri;

            if (!uri.isEmpty()) {
                String fn = buildFilename(currentDownloadUsername, "post", currentDownloadMediaId, true);
                try {
                    delegateUrlToCompanionApp(ctx, videoUrl, audioUrl, fn, true, currentDownloadUsername);
                } catch (Throwable e) {
                    ModuleLog.line("(IE|DL) merge delegate failed: " + e.getMessage());
                    mainHandler.post(() -> startDirectDownload(ctx, videoUrl, true));
                }
                return;
            }

            // No custom folder — merge locally and save via openOutputStream.
            File tv = null, ta = null, merged = null;
            try {
                File cache = ctx.getCacheDir();
                long ts = System.currentTimeMillis();
                tv     = new File(cache, "ie_v_" + ts + ".mp4");
                ta     = new File(cache, "ie_a_" + ts + ".mp4");
                merged = new File(cache, "ie_m_" + ts + ".mp4");
                downloadToFile(videoUrl, tv);
                downloadToFile(audioUrl, ta);
                String fn = buildFilename(currentDownloadUsername, "post", currentDownloadMediaId, true);
                mergeVideoAudio(tv.getAbsolutePath(), ta.getAbsolutePath(), merged.getAbsolutePath());
                saveFileToDestination(ctx, merged, fn, true, currentDownloadUsername);
                mainHandler.post(() -> Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_video_saved),
                        Toast.LENGTH_SHORT).show());
            } catch (Throwable e) {
                mainHandler.post(() -> startDirectDownload(ctx, videoUrl, true));
            } finally {
                if (tv     != null) //noinspection ResultOfMethodCallIgnored
                    tv.delete();
                if (ta     != null) //noinspection ResultOfMethodCallIgnored
                    ta.delete();
                if (merged != null) //noinspection ResultOfMethodCallIgnored
                    merged.delete();
            }
        });
    }

    private static void downloadToFile(String url, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
        conn.connect();
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        } finally { conn.disconnect(); }
    }

    static void downloadToStream(String url, OutputStream out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
        conn.connect();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally { conn.disconnect(); }
    }

    private static void mergeVideoAudio(String vp, String ap, String op) throws Exception {
        MediaExtractor vEx = new MediaExtractor(), aEx = new MediaExtractor();
        MediaMuxer mux = new MediaMuxer(op, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            vEx.setDataSource(vp); aEx.setDataSource(ap);
            int vi = selectTrack(vEx, "video/"), ai = selectTrack(aEx, "audio/");
            if (vi < 0 || ai < 0) throw new Exception("Missing tracks");
            int vo = mux.addTrack(vEx.getTrackFormat(vi)), ao = mux.addTrack(aEx.getTrackFormat(ai));
            mux.start();
            ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            copyTrack(vEx, mux, vo, buf, info); copyTrack(aEx, mux, ao, buf, info);
            mux.stop();
        } finally { vEx.release(); aEx.release(); mux.release(); }
    }

    private static int selectTrack(MediaExtractor ex, String mime) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(mime)) { ex.selectTrack(i); return i; }
        }
        return -1;
    }

    @SuppressLint("WrongConstant")
    private static void copyTrack(MediaExtractor ex, MediaMuxer mux, int out,
                                  ByteBuffer buf, MediaCodec.BufferInfo info) {
        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        while (true) {
            int sz = ex.readSampleData(buf, 0);
            if (sz < 0) break;
            info.offset = 0; info.size = sz;
            info.presentationTimeUs = ex.getSampleTime();
            info.flags = ex.getSampleFlags();
            mux.writeSampleData(out, buf, info);
            ex.advance();
        }
    }

    private static TrackInfo probeUrl(String url) {
        MediaExtractor ex = new MediaExtractor();
        boolean hv = false, ha = false;
        try {
            ex.setDataSource(url);
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (m == null) continue;
                if (m.startsWith("video/")) hv = true;
                if (m.startsWith("audio/")) ha = true;
            }
        } catch (Throwable ignored) { } finally { ex.release(); }
        return new TrackInfo(hv, ha);
    }

    private static final class TrackInfo {
        final boolean hasVideo, hasAudio;
        TrackInfo(boolean v, boolean a) { hasVideo = v; hasAudio = a; }
    }

    /**
     * Returns true if this CDN URL points to an Instagram feed media item
     * (photo or video) — not a profile picture, UI asset, or other non-media content.
     *
     * Key CDN path segments:
     *   t51.2885-15  = feed photo (INCLUDE)
     *   t51.2885-19  = profile picture (EXCLUDE)
     *   t50.2886-16  = feed video (INCLUDE)
     *   t51.39750    = exclude (story thumbnails / non-feed content)
     */
    static boolean isCdnMediaUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        if (!url.contains("cdninstagram.com") && !url.contains("fbcdn.net")) return false;
        // Exclude profile pictures: the t51 CDN path always uses suffix -19 for avatars
        // regardless of the bucket number (t51.2885-19, t51.82787-19, etc.)
        // Pattern: /t51.<digits>-19/
        if (url.contains("/t51.") && url.contains("-19/")) return false;
        // Exclude other known non-feed content
        if (url.contains("t51.39750")) return false;
        return true;
    }

    /**
     * Returns true if this CDN URL is a video (not a still image or audio-only track).
     *
     * Instagram CDN naming convention:
     *   t50.xxxx = all video CDN path segments (t50.2886-16, t50.29441-2, t50.16800-16, etc.)
     *   t51.xxxx = image content
     *   /o1/     = Reels/Clips video (path may omit t50 segment)
     *
     * Known audio-only (exclude):
     *   /o1/v/t2/ = background music track for Reels
     */
    static boolean isVideoUrl(String url) {
        if (url == null) return false;
        if (url.contains("t50.")) return true;
        if (url.contains("/o1/")) return true;
        if (url.contains(".mp4")) return true;
        return false;
    }

    private static boolean hasAncestorWithId(View view, int targetId) {
        if (targetId == 0) return false;
        android.view.ViewParent p = view.getParent();
        for (int i = 0; i < 6 && p instanceof View v; i++, p = v.getParent()) {
            if (v.getId() == targetId) return true;
        }
        return false;
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
