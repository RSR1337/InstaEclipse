package ps.reso.instaeclipse.mods.media;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.users.UserUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class StoryDownloadHook {

    private static Class<?> videoVersionIntfClass;
    private static Method   videoVersionGetUrl;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static volatile Object lastReelItem;
    private static Class<?> reelItemClass;
    private static Class<?> mediaClass;
    private static Class<?> reelClass;
    private static Class<?> userSessionClass;
    private static volatile String injectedDownloadLabel;
    private static final int REEL_CACHE_LIMIT = 16;
    private static final Map<Object, List<Object>> reelItemsCache =
            Collections.synchronizedMap(new LinkedHashMap<Object, List<Object>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Object, List<Object>> eldest) {
                    return size() > REEL_CACHE_LIMIT;
                }
            });
    private static volatile List<Object> lastStorySequence = new ArrayList<>();

    private volatile String currentStoryUsername = null;
    private volatile String currentStoryMediaId  = null;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        for (String cn : FeedVideoDownloadHook.VIDEO_VERSION_INTF_CLASSES) {
            try {
                videoVersionIntfClass = classLoader.loadClass(cn);
                videoVersionGetUrl = videoVersionIntfClass.getMethod("getUrl");
                break;
            } catch (Throwable ignored) {}
        }

        try {
            reelItemClass = classLoader.loadClass("com.instagram.model.reels.ReelItem");
        } catch (Throwable ignored) {}
        try {
            mediaClass = classLoader.loadClass("com.instagram.feed.media.Media");
        } catch (Throwable ignored) {}
        try {
            reelClass = classLoader.loadClass("com.instagram.model.reels.Reel");
        } catch (Throwable ignored) {}
        try {
            userSessionClass = classLoader.loadClass("com.instagram.common.session.UserSession");
        } catch (Throwable ignored) {}

        installReelItemsCacheHook();
        installButtonInjectorHook(bridge, classLoader);
        installClickHandlerHook(bridge, classLoader);
    }

    private void installButtonInjectorHook(DexKitBridge bridge, ClassLoader classLoader) {
        Method method = DexKitCache.isCacheValid()
                ? DexKitCache.loadMethod("StoryDownload_button_v2", classLoader) : null;

        if (method == null) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .usingStrings("[INTERNAL] Pause Playback")
                                .paramCount(1)));

                if (methods.isEmpty()) {
                    methods = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .usingStrings("explore_viewer")
                                    .paramCount(1)));
                }

                for (MethodData md : methods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        if (m.getReturnType().isArray() &&
                                CharSequence.class.isAssignableFrom(m.getReturnType().getComponentType())
                                && classHasReelItemField(m.getDeclaringClass())) {
                            method = m;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }

                if (method == null) {
                    for (MethodData md : methods) {
                        try {
                            Method m = md.getMethodInstance(classLoader);
                            if (m.getReturnType().isArray() &&
                                    CharSequence.class.isAssignableFrom(m.getReturnType().getComponentType())) {
                                method = m;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t) {
                ModuleLog.line("(IE|Story) ❌ Button builder DexKit: " + t);
                return;
            }
        }

        if (method == null) {
            ModuleLog.line("(IE|Story) ❌ No CharSequence[] return type candidate found");
            return;
        }
        DexKitCache.saveMethod("StoryDownload_button_v2", method);

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.enableStoryDownload) return;
                    CharSequence[] original = (CharSequence[]) param.getResult();
                    if (original == null) return;

                    Context app = AndroidAppHelper.currentApplication();
                    String dlLabel = I18n.t(app, R.string.ig_dl_title);
                    int seqCount = 1;
                    try {
                        seqCount = collectStoryHosts(param.thisObject).size();
                        if (seqCount < 2 && param.args.length > 0 && hasReelItemField(param.args[0])) {
                            seqCount = Math.max(seqCount, collectStoryHosts(param.args[0]).size());
                        }
                        if (seqCount < 2) seqCount = Math.max(seqCount, collectStoryHosts(lastReelItem).size());
                    } catch (Throwable ignored) {}
                    String label = seqCount >= 2 && FeatureFlags.enableBatchDownload
                            ? I18n.t(app, R.string.ig_dl_carousel_all, seqCount)
                            : dlLabel;
                    for (CharSequence cs : original) {
                        if (dlLabel.contentEquals(cs) || label.contentEquals(cs)) return;
                    }

                    CharSequence[] extended = new CharSequence[original.length + 1];
                    System.arraycopy(original, 0, extended, 0, original.length);
                    injectedDownloadLabel = label;
                    extended[original.length] = label;
                    param.setResult(extended);
                }
            });
            FeatureStatusTracker.setHooked("StoryDownload");
            ModuleLog.line("(IE|Story) ✅ button hook installed: "
                    + method.getDeclaringClass().getName() + "." + method.getName());

        } catch (Throwable t) {
            ModuleLog.line("(IE|Story) ❌ Button builder hook: " + t);
        }
    }

    private void installClickHandlerHook(DexKitBridge bridge, ClassLoader classLoader) {
        Method method = DexKitCache.isCacheValid()
                ? DexKitCache.loadMethod("StoryDownload_click_v2", classLoader) : null;

        if (method == null) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .returnType("void")
                                .usingStrings("explore_viewer",
                                        "friendships/mute_friend_reel/%s/",
                                        "[INTERNAL] Pause Playback")));

                if (methods.isEmpty()) {
                    methods = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                    .returnType("void")
                                    .usingStrings("explore_viewer")));
                }
                if (methods.isEmpty()) {
                    ModuleLog.line("(IE|Story) ❌ Click handler not found");
                    return;
                }
                method = methods.get(0).getMethodInstance(classLoader);
                DexKitCache.saveMethod("StoryDownload_click_v2", method);
            } catch (Throwable t) {
                ModuleLog.line("(IE|Story) ❌ Click handler DexKit: " + t);
                return;
            }
        }

        try {
            XposedBridge.hookMethod(method, new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.enableStoryDownload) return;

                    CharSequence tapped = null;
                    for (Object arg : param.args) {
                        if (arg instanceof CharSequence cs) { tapped = cs; break; }
                    }
                    Context app = AndroidAppHelper.currentApplication();
                    String dlLabel = I18n.t(app, R.string.ig_dl_title);
                    if (tapped == null) return;
                    boolean ours = dlLabel.contentEquals(tapped)
                            || (injectedDownloadLabel != null && injectedDownloadLabel.contentEquals(tapped));
                    if (!ours) return;

                    param.setResult(null);

                    Object holder = findReelItemHolder(param);

                    Context ctx = findContext(holder != null ? holder : param.thisObject);
                    if (ctx == null) {
                        try { ctx = AndroidAppHelper.currentApplication(); } catch (Throwable ignored) {}
                    }
                    if (ctx == null) {
                        ModuleLog.line("(IE|Story) ❌ Context not found");
                        return;
                    }

                    Object effectiveHolder = holder != null ? holder : param.thisObject;
                    currentStoryUsername = extractUsernameFromReelItemHolder(effectiveHolder);

                    List<StoryDl> sequence = new ArrayList<>();
                    try {
                        sequence = collectStoryDownloads(ctx, effectiveHolder);
                    } catch (Throwable t) {
                        ModuleLog.line("(IE|Story) collect sequence: " + t);
                    }
                    if (sequence.size() >= 2 && FeatureFlags.enableBatchDownload) {
                        ModuleLog.line("(IE|Story) batch sequence=" + sequence.size()
                                + " user=" + currentStoryUsername);
                        startBatchDownload(ctx, sequence);
                        return;
                    }

                    String url = null;
                    if (sequence.size() == 1) {
                        url = sequence.get(0).url;
                        currentStoryMediaId = sequence.get(0).mediaId;
                    }
                    if (url == null || url.isEmpty()) {
                        url = extractStoryUrl(ctx, effectiveHolder);
                        currentStoryMediaId = extractMediaIdFromReelItemHolder(effectiveHolder);
                    }

                    if (url == null || url.isEmpty()) {
                        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_story_url_not_found), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    startDownload(ctx, url, FeedVideoDownloadHook.isVideoUrl(url));
                }
            });

            FeatureStatusTracker.setHooked("StoryDownload");
            ModuleLog.line("(IE|Story) ✅ click hook installed: "
                    + method.getDeclaringClass().getName() + "." + method.getName());

        } catch (Throwable t) {
            ModuleLog.line("(IE|Story) ❌ Click handler hook: " + t);
        }
    }

    private static Object findReelItemHolder(XC_MethodHook.MethodHookParam param) {
        if (hasReelItemField(param.thisObject)) return param.thisObject;
        for (Object arg : param.args) {
            if (arg != null && hasReelItemField(arg)) return arg;
        }
        return lastReelItem;
    }

    private static boolean classHasReelItemField(Class<?> cls) {
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                String tn = f.getType().getName();
                if (tn.equals("com.instagram.model.reels.ReelItem")
                        || tn.equals("com.instagram.feed.media.Media")) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean hasReelItemField(Object obj) {
        if (obj == null) return false;
        if (reelItemClass != null && reelItemClass.isInstance(obj)) return true;
        if (mediaClass != null && mediaClass.isInstance(obj)) return true;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                String tn = f.getType().getName();
                if (tn.equals("com.instagram.model.reels.ReelItem")
                        || tn.equals("com.instagram.feed.media.Media")) return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static String extractStoryUrl(Context ctx, Object holder) {
        if (holder == null) holder = lastReelItem;
        if (holder == null) return null;
        try {
            Object reelItem = resolveReelItem(holder);
            ModuleLog.line("(IE|Story) reelItem=" +
                    (reelItem != null ? reelItem.getClass().getName() : "null"));

            Object media = resolveMedia(reelItem != null ? reelItem : holder);
            if (media != null) {
                List<String> urls = FeedVideoDownloadHook.extractAllUrlsFromMedia(ctx, media);
                if (!urls.isEmpty()) {
                    String bestVid = null;
                    for (String u : urls) {
                        if (FeedVideoDownloadHook.isVideoUrl(u)) { bestVid = u; break; }
                    }
                    return bestVid != null ? bestVid : urls.get(0);
                }
            }

            Object target = reelItem != null ? reelItem : holder;

            if (videoVersionIntfClass != null && videoVersionGetUrl != null) {
                String videoUrl = findVideoUrl(target,
                        Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                if (videoUrl != null) return videoUrl;
            }

            List<CandidateInfo> candidates = new ArrayList<>();
            collectImageCandidates(target, candidates,
                    Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            ModuleLog.line("(IE|Story) imageCandidates=" + candidates.size());
            if (!candidates.isEmpty()) {
                candidates.sort((a, b) -> Integer.compare(b.area, a.area));
                return candidates.get(0).url;
            }

            List<String> cdnUrls = new ArrayList<>();
            scanCdnUrls(target, cdnUrls, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (!cdnUrls.isEmpty()) return pickBestUrl(cdnUrls);

        } catch (Throwable t) {
            ModuleLog.line("(IE|Story) extractStoryUrl error: " + t);
        }
        return null;
    }

    private static Object resolveReelItem(Object holder) {
        Object found = null;
        if (holder != null) {
            if (reelItemClass != null && reelItemClass.isInstance(holder)) found = holder;
            if (found == null) found = readFieldByTypeName(holder, "com.instagram.model.reels.ReelItem");
        }
        if (found == null) found = lastReelItem;
        if (found != null) lastReelItem = found;
        return found;
    }

    private static Object resolveMedia(Object host) {
        if (host == null) return null;
        if (mediaClass != null && mediaClass.isInstance(host)) return host;
        Object media = readFieldByTypeName(host, "com.instagram.feed.media.Media");
        if (media != null) return media;
        Class<?> cls = host.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (mediaClass != null && mediaClass.isAssignableFrom(m.getReturnType())) {
                    try {
                        m.setAccessible(true);
                        Object r = m.invoke(host);
                        if (r != null) return r;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return mediaClass != null ? FeedVideoDownloadHook.findFieldAssignableTo(host, mediaClass) : null;
    }

    private static Object readFieldByTypeName(Object obj, String typeName) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().getName().equals(typeName)) {
                    f.setAccessible(true);
                    try { return f.get(obj); } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static String findVideoUrl(Object obj, Set<Object> visited, int depth) {
        if (obj == null || depth > 5 || !visited.add(obj)) return null;
        if (videoVersionIntfClass == null || videoVersionGetUrl == null) return null;

        if (videoVersionIntfClass.isInstance(obj)) {
            try {
                String url = (String) videoVersionGetUrl.invoke(obj);
                if (url != null && isCdnUrl(url)) return url;
            } catch (Throwable ignored) {}
        }

        String cn = obj.getClass().getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook."))
            return null;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof List<?> list) {
                        for (Object elem : list) {
                            if (videoVersionIntfClass.isInstance(elem)) {
                                try {
                                    String url = (String) videoVersionGetUrl.invoke(elem);
                                    if (url != null && isCdnUrl(url)) return url;
                                } catch (Throwable ignored) {}
                            }
                        }
                    } else {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook.")) {
                            String found = findVideoUrl(val, visited, depth + 1);
                            if (found != null) return found;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void scanCdnUrls(Object obj, List<String> out, int depth, Set<Object> visited) {
        if (obj == null || depth > 5 || out.size() >= 20) return;
        if (!visited.add(obj)) return;
        String cn = obj.getClass().getName();
        if (cn.startsWith("android.") || cn.startsWith("java.lang.") || cn.startsWith("kotlin.")) return;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof String s) {
                        if (isCdnUrl(s) && !out.contains(s)) out.add(s);
                    } else if (val instanceof List<?> list) {
                        for (Object item : list) scanCdnUrls(item, out, depth + 1, visited);
                    } else {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook.")) {
                            scanCdnUrls(val, out, depth + 1, visited);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static final class CandidateInfo {
        final String url;
        final int    area;
        CandidateInfo(String url, int area) { this.url = url; this.area = area; }
    }

    private static void collectImageCandidates(Object obj, List<CandidateInfo> out,
                                               Set<Object> visited, int depth) {
        if (obj == null || depth > 7 || out.size() >= 40) return;
        if (!visited.add(obj)) return;

        String cn = obj.getClass().getName();
        if (cn.startsWith("android.") || cn.startsWith("java.lang.") || cn.startsWith("kotlin.")) return;

        String candidateUrl = null;
        List<Integer> dims = new ArrayList<>();

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    if (f.getType() == String.class) {
                        String v = (String) f.get(obj);
                        if (v != null && isCdnUrl(v) && !isVideoUrl(v)) candidateUrl = v;
                    } else if (f.getType() == int.class) {
                        int v = f.getInt(obj);
                        if (v >= 50 && v <= 20_000) dims.add(v);
                    } else if (f.getType() == long.class) {
                        long v = f.getLong(obj);
                        if (v >= 50 && v <= 20_000) dims.add((int) v);
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }

        if (cn.startsWith("X.") || cn.startsWith("com.instagram.") || cn.startsWith("com.facebook.")) {
            cls = obj.getClass();
            while (cls != null && cls != Object.class) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getParameterCount() != 0) continue;
                    try {
                        m.setAccessible(true);
                        Class<?> ret = m.getReturnType();
                        if (ret == String.class) {
                            Object r = m.invoke(obj);
                            if (r instanceof String s && isCdnUrl(s) && !isVideoUrl(s)
                                    && candidateUrl == null) candidateUrl = s;
                        } else if (ret == int.class) {
                            Object r = m.invoke(obj);
                            if (r instanceof Integer v && v >= 50 && v <= 20_000) dims.add(v);
                        } else if (ret == long.class) {
                            Object r = m.invoke(obj);
                            if (r instanceof Long v && v >= 50 && v <= 20_000) dims.add((int)(long) v);
                        }
                    } catch (Throwable ignored) {}
                }
                cls = cls.getSuperclass();
            }
        }

        if (candidateUrl != null && dims.size() >= 2) {
            dims.sort(Collections.reverseOrder());
            out.add(new CandidateInfo(candidateUrl, dims.get(0) * dims.get(1)));
            return;
        }

        cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof List<?> list) {
                        for (Object item : list)
                            collectImageCandidates(item, out, visited, depth + 1);
                    } else if (!(val instanceof String)) {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook.")) {
                            collectImageCandidates(val, out, visited, depth + 1);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static Context findContext(Object obj) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Context.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        if (v instanceof Context ctx) return ctx;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean isCdnUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false;
        if (!url.contains("cdninstagram.com") && !url.contains("fbcdn.net")) return false;
        if (url.contains("/t51.") && url.contains("-19/")) return false;
        return true;
    }

    private static boolean isVideoUrl(String url) {
        return url.contains("t50.") || url.contains("/o1/");
    }

    private static String pickBestUrl(List<String> urls) {
        for (String u : urls) if (isVideoUrl(u)) return u;
        String best = null;
        int bestArea = 0;
        for (String u : urls) {
            int area = parseUrlArea(u);
            if (area > bestArea) { bestArea = area; best = u; }
        }
        return best != null ? best : urls.get(0);
    }

    private static int parseUrlArea(String url) {
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

    private static String extractUsernameFromReelItemHolder(Object holder) {
        Object reelItem = resolveReelItem(holder);
        if (reelItem == null) {
            ModuleLog.line("(IE|Story|Username) ❌ ReelItem not found in holder");
            return null;
        }
        ModuleLog.line("(IE|Story|Username) searching in " + reelItem.getClass().getName());
        try {
            for (Method m : reelItem.getClass().getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> ret = m.getReturnType();
                if (ret.isPrimitive() || ret == String.class || ret == void.class) continue;
                try {
                    m.setAccessible(true);
                    Object candidate = m.invoke(reelItem);
                    if (candidate == null) continue;

                    String candidateClass = candidate.getClass().getName();

                    if (candidateClass.equals("com.instagram.user.model.User")) {
                        String username = UserUtils.callUsernameGetter(candidate);
                        if (username != null) {
                            ModuleLog.line("(IE|Story|Username) reelItem." + m.getName() + "() [User] → " + username);
                            return username;
                        }
                        continue;
                    }

                    if (candidateClass.equals("com.instagram.feed.media.Media")) {
                        String username = FeedVideoDownloadHook.extractUsernameFromMediaObject(candidate);
                        if (username != null) {
                            ModuleLog.line("(IE|Story|Username) reelItem." + m.getName()
                                    + "() [Media] → " + username);
                            return username;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            Object media = resolveMedia(reelItem);
            if (media != null) {
                String username = FeedVideoDownloadHook.extractUsernameFromMediaObject(media);
                if (username != null) return username;
            }

            ModuleLog.line("(IE|Story|Username) ❌ username not found on ReelItem methods");
        } catch (Throwable t) {
            ModuleLog.line("(IE|Story|Username) ❌ Exception: " + t);
        }
        return null;
    }

    private static boolean looksLikeUsername(String s) {
        return s != null && s.length() >= 2 && s.length() <= 30
                && s.matches("[a-zA-Z0-9._]+")
                && !s.matches("\\d+");
    }

    private static String extractMediaIdFromReelItemHolder(Object holder) {
        try {
            Object reelItem = resolveReelItem(holder);
            if (reelItem == null) return null;
            Object id = reelItem.getClass().getMethod("getId").invoke(reelItem);
            if (id instanceof String s && !s.isEmpty()) return s.split("_")[0];
            Object media = resolveMedia(reelItem);
            if (media != null) {
                Object mid = media.getClass().getMethod("getId").invoke(media);
                if (mid instanceof String s && !s.isEmpty()) return s.split("_")[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static final class StoryDl {
        final String url;
        final String mediaId;
        final boolean video;
        StoryDl(String url, String mediaId, boolean video) {
            this.url = url;
            this.mediaId = mediaId;
            this.video = video;
        }
    }

    private static void installReelItemsCacheHook() {
        if (reelClass == null) return;
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableStoryDownload) return;
                rememberReelItems(param.thisObject, param.getResult());
            }
        };
        int hooked = 0;
        Class<?> cls = reelClass;
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                Class<?> ret = m.getReturnType();
                if (!List.class.isAssignableFrom(ret)
                        && !Map.class.isAssignableFrom(ret)
                        && !Collection.class.isAssignableFrom(ret)) continue;
                try {
                    XposedBridge.hookMethod(m, hook);
                    hooked++;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        ModuleLog.line("(IE|Story) reel item cache hooks=" + hooked);
    }

    private static void rememberReelItems(Object reel, Object result) {
        if (reel == null || result == null) return;
        List<Object> items = asStoryHostList(result);
        if (items.size() < 1) return;
        List<Object> existing = reelItemsCache.get(reel);
        if (existing == null || items.size() >= existing.size()) {
            reelItemsCache.put(reel, items);
        }
        if (items.size() >= 2) lastStorySequence = items;
    }

    private static List<Object> asStoryHostList(Object result) {
        List<?> raw;
        if (result instanceof List<?> list) raw = list;
        else if (result instanceof Map<?, ?> map) raw = new ArrayList<>(map.values());
        else if (result instanceof Collection<?> col) raw = new ArrayList<>(col);
        else if (result instanceof Object[] arr) {
            List<Object> tmp = new ArrayList<>();
            Collections.addAll(tmp, arr);
            raw = tmp;
        } else return new ArrayList<>();
        if (!isMediaOrReelItemList(raw)) return new ArrayList<>();
        List<Object> out = new ArrayList<>();
        for (Object o : raw) if (o != null) out.add(o);
        return out;
    }

    private static List<StoryDl> collectStoryDownloads(Context ctx, Object seed) {
        List<StoryDl> out = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (Object host : collectStoryHosts(seed)) {
            String url = extractUrlFromHost(ctx, host);
            if (url == null || url.isEmpty() || !seenUrls.add(url)) continue;
            out.add(new StoryDl(url, mediaIdOf(host), FeedVideoDownloadHook.isVideoUrl(url)));
        }
        return out;
    }

    private static List<Object> collectStoryHosts(Object seed) {
        Object reelItem = resolveReelItem(seed);
        Object media = resolveMedia(reelItem != null ? reelItem : seed);
        Object current = reelItem != null ? reelItem : media;

        Object reel = findReel(seed);
        if (reel == null && reelItem != null) reel = findReel(reelItem);

        List<Object> items = new ArrayList<>();
        if (reel != null) items = itemsFromReel(reel, seed);
        if (items.size() < 2) {
            List<Object> cached = findCachedSequence(current, media);
            if (cached.size() > items.size()) items = cached;
        }
        if (items.size() < 2) {
            List<Object> last = lastStorySequence;
            if (last.size() >= 2 && containsHost(last, current, media)) {
                items = new ArrayList<>(last);
            }
        }
        if (items.size() < 2) {
            List<Object> walked = walkForStoryLists(seed, current, media);
            if (walked.size() > items.size()) items = walked;
        }
        if (items.size() < 2 && lastReelItem != null && lastReelItem != seed) {
            List<Object> walked = walkForStoryLists(lastReelItem, current, media);
            if (walked.size() > items.size()) items = walked;
            Object lastReel = findReel(lastReelItem);
            if (lastReel != null) {
                List<Object> fromLast = itemsFromReel(lastReel, lastReelItem);
                if (fromLast.size() > items.size()) items = fromLast;
            }
        }

        items = filterSameAuthor(items, current, media);
        items = dedupeByMediaId(items);

        if (items.isEmpty() && current != null) items.add(current);
        else if (current != null && !containsHost(items, current, media)) items.add(current);
        return items;
    }

    private static Object findReel(Object seed) {
        if (seed == null || reelClass == null) return null;
        if (reelClass.isInstance(seed)) return seed;
        Object direct = readFieldByTypeName(seed, "com.instagram.model.reels.Reel");
        if (direct != null) return direct;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findTypedDeep(seed, reelClass, visited, 0, 3);
    }

    private static Object findTypedDeep(Object obj, Class<?> type, Set<Object> visited, int depth, int maxDepth) {
        if (obj == null || type == null || depth > maxDepth || !visited.add(obj)) return null;
        if (type.isInstance(obj)) return obj;
        String cn = obj.getClass().getName();
        if (cn.startsWith("android.") || cn.startsWith("java.") || cn.startsWith("kotlin.")) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (type.isInstance(val)) return val;
                    String vcn = val.getClass().getName();
                    if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.") || vcn.startsWith("com.facebook.")) {
                        Object found = findTypedDeep(val, type, visited, depth + 1, maxDepth);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static List<Object> itemsFromReel(Object reel, Object sessionSeed) {
        if (reel == null) return new ArrayList<>();
        List<Object> cached = reelItemsCache.get(reel);
        if (cached != null && cached.size() >= 2) return new ArrayList<>(cached);

        List<Object> best = new ArrayList<>();
        if (cached != null) best = new ArrayList<>(cached);

        Class<?> cls = reel.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    List<Object> fromVal = asStoryHostList(f.get(reel));
                    if (fromVal.size() > best.size()) best = fromVal;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }

        if (best.size() >= 2) return best;

        Object session = findTypedDeep(sessionSeed, userSessionClass,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0, 3);
        cls = reel.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                try {
                    Class<?> ret = m.getReturnType();
                    if (!List.class.isAssignableFrom(ret)
                            && !Map.class.isAssignableFrom(ret)
                            && !Collection.class.isAssignableFrom(ret)) continue;
                    Object invoked = null;
                    if (m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        invoked = m.invoke(reel);
                    } else if (m.getParameterCount() == 1 && session != null
                            && userSessionClass != null
                            && userSessionClass.isAssignableFrom(m.getParameterTypes()[0])) {
                        m.setAccessible(true);
                        invoked = m.invoke(reel, session);
                    }
                    if (invoked == null) continue;
                    rememberReelItems(reel, invoked);
                    List<Object> fromM = asStoryHostList(invoked);
                    if (fromM.size() > best.size()) best = fromM;
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return best;
    }

    private static List<Object> findCachedSequence(Object current, Object media) {
        List<Object> best = new ArrayList<>();
        try {
            synchronized (reelItemsCache) {
                for (List<Object> items : reelItemsCache.values()) {
                    if (items == null || items.size() < 2) continue;
                    if (!containsHost(items, current, media)) continue;
                    if (items.size() > best.size()) best = new ArrayList<>(items);
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    private static List<Object> walkForStoryLists(Object seed, Object current, Object media) {
        List<Object> best = new ArrayList<>();
        if (seed == null) return best;
        walkStoryLists(seed, best, current, media,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        return best;
    }

    private static void walkStoryLists(Object obj, List<Object> best, Object current, Object media,
                                       Set<Object> visited, int depth) {
        if (obj == null || depth > 4 || !visited.add(obj)) return;
        String cn = obj.getClass().getName();
        if (cn.startsWith("android.") || cn.startsWith("java.") || cn.startsWith("kotlin.")) return;

        if (reelClass != null && reelClass.isInstance(obj)) {
            considerMediaList(itemsFromReel(obj, obj), best, current, media);
        }

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;
                    if (val instanceof List<?> list) {
                        considerAnyList(list, best, current, media);
                    } else if (val instanceof Map<?, ?> map) {
                        considerAnyList(new ArrayList<>(map.values()), best, current, media);
                    } else if (val instanceof Object[] arr) {
                        List<Object> asList = new ArrayList<>();
                        Collections.addAll(asList, arr);
                        considerAnyList(asList, best, current, media);
                    } else {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.")
                                || vcn.startsWith("com.facebook.")) {
                            walkStoryLists(val, best, current, media, visited, depth + 1);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void considerAnyList(List<?> list, List<Object> best, Object current, Object media) {
        if (list == null || list.isEmpty() || list.size() > 200) return;
        if (isReelList(list)) {
            for (Object r : list) {
                if (r == null) continue;
                considerMediaList(itemsFromReel(r, r), best, current, media);
            }
            return;
        }
        if (isMediaOrReelItemList(list)) considerMediaList(list, best, current, media);
    }

    private static void considerMediaList(List<?> list, List<Object> best, Object current, Object media) {
        if (list == null || list.size() < 2 || list.size() > 200) return;
        if (current != null && !containsHost(list, current, media)) return;
        List<Object> filtered = filterSameAuthor(list, current, media);
        if (filtered.size() < 2) return;
        if (filtered.size() > best.size()) {
            best.clear();
            best.addAll(filtered);
        }
    }

    private static boolean isMediaOrReelItemList(List<?> list) {
        if (list == null || list.isEmpty() || list.size() > 200) return false;
        for (Object item : list) {
            if (item == null) continue;
            if (reelItemClass != null && reelItemClass.isInstance(item)) return true;
            if (mediaClass != null && mediaClass.isInstance(item)) return true;
            return false;
        }
        return false;
    }

    private static boolean isReelList(List<?> list) {
        if (list == null || list.isEmpty() || reelClass == null) return false;
        for (Object item : list) {
            if (item == null) continue;
            return reelClass.isInstance(item);
        }
        return false;
    }

    private static boolean containsHost(List<?> list, Object current, Object media) {
        if (current == null && media == null) return true;
        String cid = mediaIdOf(current);
        String mid = mediaIdOf(media);
        for (Object o : list) {
            if (o == null) continue;
            if (o == current || o == media) return true;
            String id = mediaIdOf(o);
            if (id != null && (id.equals(cid) || id.equals(mid))) return true;
            Object om = resolveMedia(o);
            if (om != null && (om == media || om == current)) return true;
        }
        return false;
    }

    private static List<Object> filterSameAuthor(List<?> items, Object current, Object media) {
        String user = usernameOf(current);
        if (user == null) user = usernameOf(media);
        List<Object> out = new ArrayList<>();
        if (user == null) {
            for (Object o : items) if (o != null) out.add(o);
            return out;
        }
        for (Object o : items) {
            if (o == null) continue;
            String u = usernameOf(o);
            if (u == null || user.equalsIgnoreCase(u)) out.add(o);
        }
        return out;
    }

    private static List<Object> dedupeByMediaId(List<Object> items) {
        LinkedHashMap<String, Object> byId = new LinkedHashMap<>();
        int anon = 0;
        for (Object o : items) {
            if (o == null) continue;
            String id = mediaIdOf(o);
            if (id == null || id.isEmpty()) id = "anon_" + (anon++);
            byId.putIfAbsent(id, o);
        }
        return new ArrayList<>(byId.values());
    }

    private static String usernameOf(Object host) {
        if (host == null) return null;
        if (reelItemClass != null && reelItemClass.isInstance(host)) {
            return extractUsernameFromReelItemHolder(host);
        }
        if (mediaClass != null && mediaClass.isInstance(host)) {
            return FeedVideoDownloadHook.extractUsernameFromMediaObject(host);
        }
        Object media = resolveMedia(host);
        if (media != null) return FeedVideoDownloadHook.extractUsernameFromMediaObject(media);
        return null;
    }

    private static String mediaIdOf(Object host) {
        if (host == null) return null;
        try {
            Object id = host.getClass().getMethod("getId").invoke(host);
            if (id instanceof String s && !s.isEmpty()) return s.split("_")[0];
        } catch (Throwable ignored) {}
        Object media = resolveMedia(host);
        if (media != null && media != host) {
            try {
                Object id = media.getClass().getMethod("getId").invoke(media);
                if (id instanceof String s && !s.isEmpty()) return s.split("_")[0];
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String extractUrlFromHost(Context ctx, Object host) {
        if (host == null) return null;
        try {
            Object media = resolveMedia(host);
            Object target = media != null ? media : host;
            if (media != null) {
                String single = FeedVideoDownloadHook.extractSingleUrlFromMedia(ctx, media);
                if (single != null && !single.isEmpty()) return single;
            }
            if (videoVersionIntfClass != null && videoVersionGetUrl != null) {
                String videoUrl = findVideoUrl(target,
                        Collections.newSetFromMap(new IdentityHashMap<>()), 0);
                if (videoUrl != null) return videoUrl;
            }
            List<CandidateInfo> candidates = new ArrayList<>();
            collectImageCandidates(target, candidates,
                    Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            if (!candidates.isEmpty()) {
                candidates.sort((a, b) -> Integer.compare(b.area, a.area));
                return candidates.get(0).url;
            }
            List<String> cdnUrls = new ArrayList<>();
            scanCdnUrls(target, cdnUrls, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (!cdnUrls.isEmpty()) return pickBestUrl(cdnUrls);
        } catch (Throwable t) {
            ModuleLog.line("(IE|Story) extractUrlFromHost: " + t);
        }
        return null;
    }

    private void startBatchDownload(Context ctx, List<StoryDl> items) {
        int n = items.size();
        String username = currentStoryUsername;
        BulkDownloadProgressDialog progress = BulkDownloadProgressDialog.show(ctx, mainHandler, n);
        FeedVideoDownloadHook.executor.submit(() -> {
            int saved = 0;
            int failed = 0;
            for (StoryDl item : items) {
                if (progress.isCancelled()) break;
                String fn = FeedVideoDownloadHook.buildFilename(username, "story", item.mediaId, item.video);
                try {
                    FeedVideoDownloadHook.downloadAndSave(ctx, item.url, fn, item.video, username);
                    saved++;
                } catch (Throwable e) {
                    failed++;
                    ModuleLog.line("(IE|Story|DL) item failed: " + e);
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

    private void startDownload(Context ctx, String url, boolean isVideo) {
        String fn = FeedVideoDownloadHook.buildFilename(currentStoryUsername, "story", currentStoryMediaId, isVideo);
        ModuleLog.line("(IE|Story|DL) username=" + currentStoryUsername + " mediaId=" + currentStoryMediaId
                + " file=" + fn);
        if (FeedVideoDownloadHook.isDownloaded(ctx, fn, isVideo, currentStoryUsername)) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_already_downloaded), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(ctx, isVideo ? I18n.t(ctx, R.string.ig_toast_downloading_story_video) : I18n.t(ctx, R.string.ig_toast_downloading_story_photo), Toast.LENGTH_SHORT).show();
        mainHandler.post(() -> new Thread(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(ctx, url, fn, isVideo, currentStoryUsername);
                if (!delegated) {
                    mainHandler.post(() -> Toast.makeText(ctx,
                            I18n.t(ctx, R.string.ig_toast_story_saved), Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                mainHandler.post(() -> Toast.makeText(ctx,
                        I18n.t(ctx, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        }).start());
    }

}
