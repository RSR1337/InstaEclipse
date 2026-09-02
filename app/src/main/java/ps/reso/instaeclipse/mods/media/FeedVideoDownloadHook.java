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
import android.widget.LinearLayout;
import android.widget.TextView;
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
import ps.reso.instaeclipse.utils.core.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.history.DownloadHistory;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.users.UserUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class FeedVideoDownloadHook {

    private static final Pattern TIMESTAMPED_FILENAME_PATTERN =
            Pattern.compile("^(.*)_([0-9]{8}_[0-9]{6})(\\.[^.]+)$");

    static final String[] VIDEO_VERSION_INTF_CLASSES = {
            "com.instagram.api.schemas.VideoVersionIntf",
            "com.instagram.model.mediasize.VideoVersionIntf"
    };

    private static final String DECOR_OVERLAY_TAG = "ie_decor_overlay";
    private static final int TAG_CACHED_MEDIA = "ie_dl_media".hashCode();
    private static final int TAG_OVERLAY_ANCHOR = "ie_dl_overlay_anchor".hashCode();
    private static final int TAG_INJECTED = "ie_dl_injected".hashCode();
    private static final int TAG_OVERLAY_BINDING = "ie_dl_overlay_binding".hashCode();
    private static final int TAG_BOTTOM_SHEET = "ie_dl_bottom_sheet".hashCode();

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

    private static Class<?> mediaExtKtClass;
    private static Class<?> mediaClass;
    static Class<?> mutableMediaDictIntfClass;
    private static Method   methodImageUrl;

    static Class<?> videoVersionIntfClass;
    static Method   videoVersionGetUrl;

    static Class<?> imageUrlClass;
    static Class<?> imageInfoClass;

    static final List<Method> carouselCandidates = new ArrayList<>();

    private static Class<?> userClass;
    private static Method   dictUserGetter;

    private static final class UrlEntry {
        final String url; final long time;
        UrlEntry(String u) { url = u; time = System.currentTimeMillis(); }
    }
    private static final int MAX_URLS = 200;
    private static final int CAROUSEL_MAX_ITEMS = 20;
    private static final Deque<UrlEntry> urlBuffer      = new ArrayDeque<>();
    private static final Deque<UrlEntry> videoUrlBuffer = new ArrayDeque<>();
    static final ExecutorService executor    = Executors.newCachedThreadPool();
    static final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private volatile String currentDownloadUsername = null;
    private volatile String currentDownloadMediaId  = null;

    public void install(ClassLoader classLoader) {
        try {
            mediaClass      = classLoader.loadClass("com.instagram.feed.media.Media");
            mediaExtKtClass = classLoader.loadClass("com.instagram.feed.media.MediaExtKt");
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
        try {
            ViewAttachDispatcher.add(this::scheduleInjectionForView);
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
            dedupeKey.setTag(TAG_INJECTED, null);
            dedupeKey.setTag(TAG_OVERLAY_BINDING, null);
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

        Object existingTag = dedupeKey.getTag(TAG_OVERLAY_BINDING);
        if (existingTag instanceof OverlayBinding existing) existing.dispose();

        host.addView(btn, lp);
        btn.bringToFront();
        btn.setElevation(dp(ctx, 12));
        btn.setVisibility(View.INVISIBLE);
        btn.setTag(TAG_OVERLAY_ANCHOR, dedupeKey);

        int gap = dp(ctx, placement == OverlayPlacement.ABOVE ? 10 : 4);
        OverlayBinding binding = new OverlayBinding(anchor, btn, host, dedupeKey, placement, gap);
        dedupeKey.setTag(TAG_OVERLAY_BINDING, binding);
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
        return Boolean.TRUE.equals(key.getTag(TAG_INJECTED));
    }

    private static void setInjected(View key) {
        key.setTag(TAG_INJECTED, Boolean.TRUE);
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

                View decor = activity.getWindow().getDecorView();
                View bsView = (View) decor.getTag(TAG_BOTTOM_SHEET);
                if (bsView == null) {
                    View found = decor.findViewById(sBottomSheetContainerId);
                    if (found != null) {
                        decor.setTag(TAG_BOTTOM_SHEET, found);
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
            if (!(tag instanceof View key)) continue;
            if (isAnchorVisibleOnScreen(key)) continue;
            Object bindingTag = key.getTag(TAG_OVERLAY_BINDING);
            if (bindingTag instanceof OverlayBinding binding) binding.dispose();
            else if (child.getParent() == host) host.removeView(child);
        }
    }

    @SuppressLint("DiscouragedApi")
    private List<String> resolveUrls(View likeBtn, View downloadBtn) {
        List<String> urls = urlsFromSaveBtnListener(likeBtn);
        ModuleLog.line("(IE|DL) Tier-1a urls=" + urls.size());
        if (!urls.isEmpty()) return urls;

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
                    break;
                }
            }
        }

        return new ArrayList<>();
    }

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

    static String findVideoUrlInObject(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || !visited.add(obj)) return null;
        if (videoVersionIntfClass == null || videoVersionGetUrl == null) return null;

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
                        for (Object elem : list) {
                            if (elem != null && videoVersionIntfClass.isInstance(elem)) {
                                try {
                                    String url = (String) videoVersionGetUrl.invoke(elem);
                                    if (url != null && isCdnMediaUrl(url)) return url;
                                } catch (Throwable ignored) {}
                            }
                        }
                    } else {
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

    static void collectAllVideoUrls(Object obj, List<String> out, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || !visited.add(obj)) return;
        if (videoVersionIntfClass == null || videoVersionGetUrl == null) return;

        if (videoVersionIntfClass.isInstance(obj)) {
            try {
                String url = (String) videoVersionGetUrl.invoke(obj);
                if (url != null && isCdnMediaUrl(url) && !out.contains(url)) out.add(url);
            } catch (Throwable ignored) {}
            return;
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

    private static void resolveUsernameGetter(DexKitBridge bridge, ClassLoader classLoader) {
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

        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(mutableMediaDictIntfClass);

        while (!queue.isEmpty()) {
            Class<?> curr = queue.poll();
            if (curr == null || !visited.add(curr)) continue;

            for (Method m : curr.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().equals(userClass)) {
                    m.setAccessible(true);
                    dictUserGetter = m;
                    DexKitCache.saveMethod("DictUserGetter", m);
                    ModuleLog.line("(IE|DL|Username) ✅ Resolved dictUserGetter: " + m.getName());
                    return;
                }
            }
            Collections.addAll(queue, curr.getInterfaces());
        }

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

    @SuppressLint("DiscouragedApi")
    private String getUsernameFromView(View likeBtn) {
        if (likeBtn == null || mediaClass == null) return null;

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

        Object userObj = findFieldOfType(media, userClass, 3);
        if (userObj != null) {
            String name = UserUtils.callUsernameGetter(userObj);
            if (name != null) return name;
        }

        return scanObjectForUsername(media, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Object getMediaFromListener(Object listener) {
        if (listener == null || mediaClass == null) return null;
        return findFieldOfType(listener, mediaClass, 4);
    }

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

    static String buildFilename(String username, String type, String mediaId, boolean isVideo) {
        return buildFilename(username, type, mediaId, isVideo, -1);
    }

    static String buildFilename(String username, String type, String mediaId, boolean isVideo, int slideIndex) {
        return buildFilename(username, type, mediaId, isVideo, slideIndex, null);
    }

    static String buildFilename(String username, String type, String mediaId, boolean isVideo, int slideIndex, String extOverride) {
        String u  = (username != null && !username.isEmpty()) ? username : "unknown";
        String id = (mediaId  != null && !mediaId.isEmpty())  ? mediaId  : String.valueOf(System.currentTimeMillis());
        String ext = extOverride != null ? extOverride : (isVideo ? ".mp4" : ".jpg");
        StringBuilder sb = new StringBuilder(u).append('_').append(type).append('_').append(id);
        if (slideIndex >= 0) sb.append('_').append(slideIndex + 1);
        if (FeatureFlags.downloaderAddTimestamp) {
            sb.append('_').append(new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
        }
        return sb.append(ext).toString();
    }

    static OutputStream openOutputStream(Context ctx, String filename, boolean isVideo, String username)
            throws Exception {
        return openOutputStream(ctx, filename, isVideo, username, null);
    }

    static OutputStream openOutputStream(Context ctx, String filename, boolean isVideo, String username,
                                         String mimeTypeOverride)
            throws Exception {
        String mimeType = mimeTypeOverride != null ? mimeTypeOverride : (isVideo ? "video/mp4" : "image/jpeg");

        if (!FeatureFlags.downloaderCustomPath.isEmpty()) {
            try {
                return openRawPathOutputStream(filename, username);
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|DL) Raw path failed, trying SAF: " + e.getMessage());
            }
        }

        if (!FeatureFlags.downloaderCustomUri.isEmpty()) {
            try {
                return openSafOutputStream(ctx, filename, mimeType, username);
            } catch (Exception e) {
                ModuleLog.line("(InstaEclipse|DL) SAF failed, falling back to MediaStore: " + e.getMessage());
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return openMediaStoreOutputStream(ctx, filename, mimeType, username);
        }

        File dir = new File(Environment.getExternalStorageDirectory(), "InstaEclipse");
        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            dir = new File(dir, username);
        }
        dir.mkdirs();
        return new FileOutputStream(new File(dir, filename));
    }

    private static OutputStream openRawPathOutputStream(String filename, String username) throws Exception {
        String rawPath = FeatureFlags.downloaderCustomPath;
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

    private static final java.util.Set<String> MS_ROOTS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Download", "Downloads", "Pictures", "DCIM", "Movies", "Music",
            "Ringtones", "Alarms", "Notifications", "Podcasts", "Audiobooks"));

    private static String buildMediaStoreRelPath(String username) {
        String customPath = FeatureFlags.downloaderCustomPath;
        String base = "Download/InstaEclipse";

        if (!customPath.isEmpty() && !customPath.startsWith("content://")) {
            String extBase = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (customPath.startsWith(extBase + "/")) {
                String relative = customPath.substring(extBase.length() + 1);
                String topLevel = relative.split("/")[0];
                base = MS_ROOTS.contains(topLevel) ? relative : ("Download/" + relative);
            }
        }

        if (FeatureFlags.downloaderUsernameFolder && username != null && !username.isEmpty()) {
            base += "/" + username;
        }
        return base;
    }

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

    static void saveFileToDestination(Context ctx, File tempFile, String filename,
                                      boolean isVideo, String username) throws Exception {
        try (FileInputStream in = new FileInputStream(tempFile);
             OutputStream out = openOutputStream(ctx, filename, isVideo, username)) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

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

    static boolean downloadAndSave(Context ctx, String url, String filename,
                                   boolean isVideo, String username) throws Exception {
        return downloadAndSave(ctx, url, filename, isVideo, username, null);
    }

    static boolean downloadAndSave(Context ctx, String url, String filename,
                                   boolean isVideo, String username, String mimeTypeOverride) throws Exception {
        if (isDownloaded(ctx, filename, isVideo, username)) {
            mainHandler.post(() -> Toast.makeText(ctx,
                    I18n.t(ctx, R.string.ig_toast_already_downloaded), Toast.LENGTH_SHORT).show());
            return true;
        }

        String uri = FeatureFlags.downloaderCustomUri.isEmpty()
                ? readCompanionUri()
                : FeatureFlags.downloaderCustomUri;

        if (!uri.isEmpty()) {
            delegateUrlToCompanionApp(ctx, url, null, filename, isVideo, username, mimeTypeOverride);
            recordDownloadHistory(filename, username);
            return true;
        }

        try (OutputStream out = openOutputStream(ctx, filename, isVideo, username, mimeTypeOverride)) {
            downloadToStream(url, out);
        }
        recordDownloadHistory(filename, username);
        return false;
    }

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

    private static void delegateUrlToCompanionApp(Context ctx,
                                                   String url,
                                                   String audioUrl,
                                                   String filename, boolean isVideo,
                                                   String username) throws Exception {
        delegateUrlToCompanionApp(ctx, url, audioUrl, filename, isVideo, username, null);
    }

    private static void delegateUrlToCompanionApp(Context ctx,
                                                   String url,
                                                   String audioUrl,
                                                   String filename, boolean isVideo,
                                                   String username, String mimeTypeOverride) throws Exception {
        android.content.Intent intent = new android.content.Intent();
        intent.setClassName("ps.reso.instaeclipse",
                            "ps.reso.instaeclipse.mods.media.DownloadSaveService");
        intent.putExtra("url",      url);
        if (audioUrl != null) intent.putExtra("audioUrl", audioUrl);
        intent.putExtra("filename", filename);
        intent.putExtra("mimeType", mimeTypeOverride != null ? mimeTypeOverride : (isVideo ? "video/mp4" : "image/jpeg"));
        intent.putExtra("username", username);
        ctx.startForegroundService(intent);
        ModuleLog.line("(IE|DL) Delegated to DownloadSaveService: " + filename);
    }

    static void delegateAudioOnlyToCompanionApp(Context ctx, String videoUrl, String filename, String username) {
        android.content.Intent intent = new android.content.Intent();
        intent.setClassName("ps.reso.instaeclipse",
                            "ps.reso.instaeclipse.mods.media.DownloadSaveService");
        intent.putExtra("url",       videoUrl);
        intent.putExtra("filename",  filename);
        intent.putExtra("mimeType",  "audio/mp4");
        intent.putExtra("username",  username);
        intent.putExtra("audioOnly", true);
        ctx.startForegroundService(intent);
        ModuleLog.line("(IE|DL) Delegated audio-only extraction to DownloadSaveService: " + filename);
    }

    static List<String> collectCdnUrls(Object obj) {
        List<String> out = new ArrayList<>();
        scanForCdnUrls(obj, out, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        return out;
    }

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
        int videoCount = 0;
        for (String url : urls) if (isVideoUrl(url)) videoCount++;
        String message = I18n.t(ctx, R.string.ig_dl_carousel_subtitle, n)
                + '\n' + I18n.t(ctx, R.string.ig_dl_carousel_summary, n - videoCount, videoCount);

        Context dialogCtx = resolveDialogContext(ctx);
        try {
            boolean dark = BulkDownloadDialogStyle.isDarkMode(dialogCtx);

            LinearLayout content = new LinearLayout(dialogCtx);
            content.setOrientation(LinearLayout.VERTICAL);
            int padH = BulkDownloadDialogStyle.dp(dialogCtx, 20f);
            content.setPadding(padH, 0, padH, BulkDownloadDialogStyle.dp(dialogCtx, 20f));

            TextView messageView = new TextView(dialogCtx);
            messageView.setText(message);
            messageView.setTextColor(BulkDownloadDialogStyle.secondaryTextColor(dark));
            messageView.setTextSize(15f);
            messageView.setLineSpacing(BulkDownloadDialogStyle.dp(dialogCtx, 4f), 1f);
            LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            msgLp.bottomMargin = BulkDownloadDialogStyle.dp(dialogCtx, 20f);
            content.addView(messageView, msgLp);

            AlertDialog[] holder = new AlertDialog[1];

            View allBtn = BulkDownloadDialogStyle.buildFilledButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dl_carousel_all, n),
                    () -> {
                        downloadAllPostUrls(ctx, urls, username, mediaId);
                        if (holder[0] != null) holder[0].dismiss();
                    });
            content.addView(allBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View currentBtn = BulkDownloadDialogStyle.buildOutlinedButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dl_carousel_current, currentIndex + 1, n),
                    () -> {
                        downloadSinglePostUrl(ctx, urls.get(currentIndex), username, mediaId, currentIndex);
                        if (holder[0] != null) holder[0].dismiss();
                    });
            LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            currentLp.topMargin = BulkDownloadDialogStyle.dp(dialogCtx, 10f);
            content.addView(currentBtn, currentLp);

            View cancelBtn = BulkDownloadDialogStyle.buildTextButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dialog_cancel), dark,
                    () -> {
                        if (holder[0] != null) holder[0].dismiss();
                    });
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelLp.topMargin = BulkDownloadDialogStyle.dp(dialogCtx, 4f);
            content.addView(cancelBtn, cancelLp);

            AlertDialog dialog = new AlertDialog.Builder(dialogCtx)
                    .setCustomTitle(BulkDownloadDialogStyle.buildTitleView(dialogCtx, I18n.t(ctx, R.string.ig_dl_title)))
                    .setView(content)
                    .setCancelable(true)
                    .create();
            holder[0] = dialog;
            BulkDownloadDialogStyle.applyCardWindow(dialog, dialogCtx);
            dialog.show();
        } catch (Throwable t) {
            ModuleLog.line("(IE|Post|DL) dialog failed: " + t);
            downloadSinglePostUrl(ctx, urls.get(currentIndex), username, mediaId, currentIndex);
        }
    }

    static Context resolveDialogContext(Context ctx) {
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
        BulkDownloadProgressDialog progress = BulkDownloadProgressDialog.show(ctx, mainHandler, n);
        executor.submit(() -> {
            int saved = 0;
            int failed = 0;
            int i = 0;
            for (String url : urls) {
                if (progress.isCancelled()) break;
                boolean isVid = isVideoUrl(url);
                String fn = buildFilename(username, "post", mediaId, isVid, i++);
                try {
                    downloadAndSave(ctx, url, fn, isVid, username);
                    saved++;
                } catch (Throwable e) {
                    failed++;
                    ModuleLog.line("(IE|Post|DL) item failed: " + e);
                }
                progress.updateProgress(saved + failed, saved, failed);
            }
            if (progress.isCancelled()) {
                progress.dismissIfShowing();
                final int finalSaved = saved;
                final int finalFailed = failed;
                mainHandler.post(() -> Toast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_cancelled, finalSaved, finalFailed),
                        Toast.LENGTH_SHORT).show());
            } else {
                progress.finish(saved, failed);
            }
        });
    }

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

    @Deprecated
    public static String callUsernameGetter(Object user) {
        return UserUtils.callUsernameGetter(user);
    }

    private static String scanObjectForUsername(Object obj, int depth,
                                                 Set<Object> visited) {
        if (obj == null || depth > 3 || visited.contains(obj)) return null;
        visited.add(obj);

        try {
            Object result = obj.getClass().getMethod("getUsername").invoke(obj);
            if (result instanceof String s && !s.isEmpty() && s.matches("[a-zA-Z0-9._]{1,30}")) {
                return s;
            }
        } catch (Throwable ignored) {}

        if (depth >= 3) return null;

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
            handleVideoDownload(ctx, videos, saveBtn, media);
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
                    startDirectDownload(ctx, images.get(0), false);
                } else {
                    showCarouselDialog(ctx, allUrls, saveBtn);
                }
            });
        });
    }

    private void handleVideoDownload(Context ctx, List<String> videos, View saveBtn, Object media) {
        if (videos.size() == 1) {
            if (isClipsContext(saveBtn)) {
                showReelDownloadChoiceDialog(ctx, videos.get(0), media);
                return;
            }
            startDirectDownload(ctx, videos.get(0), true);
            return;
        }
        showCarouselDialog(ctx, videos, saveBtn);
    }

    private void showReelDownloadChoiceDialog(Context ctx, String videoUrl, Object media) {
        Context dialogCtx = resolveDialogContext(ctx);
        try {
            boolean dark = BulkDownloadDialogStyle.isDarkMode(dialogCtx);

            LinearLayout content = new LinearLayout(dialogCtx);
            content.setOrientation(LinearLayout.VERTICAL);
            int padH = BulkDownloadDialogStyle.dp(dialogCtx, 20f);
            content.setPadding(padH, 0, padH, BulkDownloadDialogStyle.dp(dialogCtx, 20f));

            AlertDialog[] holder = new AlertDialog[1];

            View videoBtn = BulkDownloadDialogStyle.buildFilledButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dl_reel_choice_video),
                    () -> {
                        startDirectDownload(ctx, videoUrl, true);
                        if (holder[0] != null) holder[0].dismiss();
                    });
            content.addView(videoBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View coverBtn = BulkDownloadDialogStyle.buildOutlinedButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dl_reel_choice_cover),
                    () -> {
                        startReelCoverDownload(ctx, media);
                        if (holder[0] != null) holder[0].dismiss();
                    });
            LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            coverLp.topMargin = BulkDownloadDialogStyle.dp(dialogCtx, 10f);
            content.addView(coverBtn, coverLp);

            View audioBtn = BulkDownloadDialogStyle.buildOutlinedButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dl_reel_choice_audio),
                    () -> {
                        startReelAudioDownload(ctx, videoUrl);
                        if (holder[0] != null) holder[0].dismiss();
                    });
            LinearLayout.LayoutParams audioLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            audioLp.topMargin = BulkDownloadDialogStyle.dp(dialogCtx, 10f);
            content.addView(audioBtn, audioLp);

            View cancelBtn = BulkDownloadDialogStyle.buildTextButton(dialogCtx,
                    I18n.t(ctx, R.string.ig_dialog_cancel), dark,
                    () -> {
                        if (holder[0] != null) holder[0].dismiss();
                    });
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelLp.topMargin = BulkDownloadDialogStyle.dp(dialogCtx, 4f);
            content.addView(cancelBtn, cancelLp);

            AlertDialog dialog = new AlertDialog.Builder(dialogCtx)
                    .setCustomTitle(BulkDownloadDialogStyle.buildTitleView(dialogCtx, I18n.t(ctx, R.string.ig_dl_title)))
                    .setView(content)
                    .setCancelable(true)
                    .create();
            holder[0] = dialog;
            BulkDownloadDialogStyle.applyCardWindow(dialog, dialogCtx);
            dialog.show();
        } catch (Throwable t) {
            ModuleLog.line("(IE|DL) reel choice dialog failed: " + t);
            startDirectDownload(ctx, videoUrl, true);
        }
    }

    private void startReelCoverDownload(Context ctx, Object media) {
        String coverUrl = media != null ? bestImageUrlFromMedia(ctx, media) : null;
        if (coverUrl == null) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_reel_url_not_found), Toast.LENGTH_SHORT).show();
            return;
        }
        final String finalUrl = coverUrl;
        String fn = buildFilename(currentDownloadUsername, "reel_cover", currentDownloadMediaId, false);
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading), Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            try {
                boolean delegated = downloadAndSave(ctx, finalUrl, fn, false, currentDownloadUsername);
                if (!delegated) {
                    mainHandler.post(() ->
                            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_reel_cover_saved), Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                mainHandler.post(() ->
                        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startReelAudioDownload(Context ctx, String videoUrl) {
        String fn = buildFilename(currentDownloadUsername, "reel_audio", currentDownloadMediaId, false, -1, ".m4a");
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading), Toast.LENGTH_SHORT).show();
        delegateAudioOnlyToCompanionApp(ctx, videoUrl, fn, currentDownloadUsername);
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
                if (tv     != null)
                    tv.delete();
                if (ta     != null)
                    ta.delete();
                if (merged != null)
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

    static boolean isCdnMediaUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        if (!url.contains("cdninstagram.com") && !url.contains("fbcdn.net")) return false;
        if (url.contains("/t51.") && url.contains("-19/")) return false;
        if (url.contains("t51.39750")) return false;
        return true;
    }

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
