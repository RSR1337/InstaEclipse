package ps.reso.instaeclipse.mods.media;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class ReelDownloadHook {

    private static Class<?> mediaClass;
    private static Class<?> liveTreeMediaDictClass;
    private static Object   downloadOptionValue;

    private static Method buttonAdderMethod;
    private static Field  activityField;
    private static boolean loggedOptionsBuiltFailure;

    // Cached field path to the carousel position holder on the controller.
    // The position holder is identified structurally: a non-framework object field
    // whose class has exactly ONE int field (survives obfuscation renames).
    private static Field cachedOuterField = null;
    private static Field cachedInnerField = null;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            mediaClass = classLoader.loadClass("com.instagram.feed.media.Media");
        } catch (Throwable ignored) {}
        try {
            liveTreeMediaDictClass = classLoader.loadClass("com.instagram.feed.media.LiveTreeMediaDict");
        } catch (Throwable ignored) {}

        installNativeDownloadGateUnlock(bridge, classLoader);
        installStringDownloadGates(bridge, classLoader);
        installReduceOptionsListPatch(bridge, classLoader);
        installControllerHook(bridge, classLoader);
    }

    private static XC_MethodHook controllerHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableReelDownload) return;
                Object result = param.getResult();
                if (result instanceof List<?> list && downloadOptionValue != null) {
                    try {
                        if (!list.contains(downloadOptionValue)) {
                            @SuppressWarnings("unchecked")
                            List<Object> mutable = (List<Object>) list;
                            mutable.add(downloadOptionValue);
                        }
                    } catch (Throwable t) {
                        ModuleLog.line("(IE|Reel) ❌ controller list patch: " + t);
                    }
                    return;
                }
                onOptionsBuilt(param);
            }
        };
    }

    private static void installControllerHook(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = controllerHook();

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("ReelDownload_v3", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) {
                    XposedBridge.hookMethod(m, hook);
                }
                FeatureStatusTracker.setHooked("ReelDownload");
                ModuleLog.line("(IE|Reel) ✅ hooked (cached): " + describeMethods(cached));
                return;
            }
        }

        try {
            List<Method> targets = discoverControllerMethods(bridge, classLoader);
            if (targets.isEmpty()) {
                ModuleLog.line("(IE|Reel) ❌ reel options builder not found");
                return;
            }

            for (Method m : targets) {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, hook);
            }
            DexKitCache.saveMethods("ReelDownload_v3", targets);
            FeatureStatusTracker.setHooked("ReelDownload");
            ModuleLog.line("(IE|Reel) ✅ hooked: " + describeMethods(targets));
        } catch (Throwable t) {
            ModuleLog.line("(IE|Reel) ❌ install: " + t);
        }
    }

    private static List<Method> discoverControllerMethods(DexKitBridge bridge, ClassLoader classLoader) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        String[] markers = {
                "ClipsOrganicMediaItemViewMoreOptionsController",
                "ClipsOrganicMoreOptionsActionUtil"
        };

        for (String marker : markers) {
            try {
                List<ClassData> classHits = bridge.findClass(FindClass.create()
                        .matcher(ClassMatcher.create().usingStrings(marker)));
                for (ClassData cd : classHits) {
                    Class<?> cls = loadNonFrameworkClass(classLoader, cd.getName());
                    if (cls != null) classes.add(cls);
                }
            } catch (Throwable ignored) {}

            try {
                List<MethodData> methodHits = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create().usingStrings(marker)));
                for (MethodData md : methodHits) {
                    Class<?> cls = loadNonFrameworkClass(classLoader, md.getClassName());
                    if (cls != null) classes.add(cls);
                }
            } catch (Throwable ignored) {}
        }

        List<Method> targets = new ArrayList<>();
        Class<?> bestClass = null;
        for (Class<?> cls : classes) {
            List<Method> found = findOptionsBuilderMethods(cls);
            if (found.isEmpty() || found.size() > 8) continue;
            if (bestClass == null || scoreControllerClass(cls) > scoreControllerClass(bestClass)) {
                bestClass = cls;
                targets = found;
            }
        }

        if (targets.isEmpty() && !classes.isEmpty()) {
            StringBuilder dump = new StringBuilder("(IE|Reel) no builder in ");
            boolean first = true;
            for (Class<?> cls : classes) {
                if (!first) dump.append(", ");
                first = false;
                dump.append(cls.getName());
            }
            ModuleLog.line(dump.toString());
        }

        if (targets.isEmpty()) {
            try {
                String optionDesc = "Lcom/instagram/feed/media/mediaoption/MediaOption$Option;";
                List<MethodData> downloadRefs = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .addUsingField(optionDesc + "->DOWNLOAD:" + optionDesc)));
                for (MethodData md : downloadRefs) {
                    Class<?> cls = loadNonFrameworkClass(classLoader, md.getClassName());
                    if (cls == null) continue;
                    List<Method> found = findOptionsBuilderMethods(cls);
                    if (found.isEmpty() || found.size() > 8) continue;
                    if (bestClass == null || scoreControllerClass(cls) > scoreControllerClass(bestClass)) {
                        bestClass = cls;
                        targets = found;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return targets;
    }

    private static int scoreControllerClass(Class<?> cls) {
        String n = cls.getName();
        int score = 0;
        if (n.contains("MoreOptions") || n.contains("Controller")) score += 4;
        if (n.contains("Clips") || n.contains("clips")) score += 3;
        if (n.startsWith("X.") || n.startsWith("p000X.")) score += 1;
        return score;
    }

    private static Class<?> loadNonFrameworkClass(ClassLoader classLoader, String name) {
        if (name == null) return null;
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("androidx.")
                || name.startsWith("kotlin.") || name.startsWith("dalvik.")) return null;
        try {
            return classLoader.loadClass(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Method> findOptionsBuilderMethods(Class<?> cls) {
        List<Method> out = new ArrayList<>();
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (isOptionsBuilderCandidate(m)) out.add(m);
            }
            c = c.getSuperclass();
        }
        return out;
    }

    private static boolean isOptionsBuilderCandidate(Method m) {
        if (!List.class.isAssignableFrom(m.getReturnType())) return false;
        for (Class<?> p : m.getParameterTypes()) {
            if (isMediaLike(p)) return true;
        }
        return false;
    }

    static boolean isMediaLike(Class<?> type) {
        if (type == null) return false;
        if (mediaClass != null && (type == mediaClass || mediaClass.isAssignableFrom(type))) return true;
        if (liveTreeMediaDictClass != null && liveTreeMediaDictClass.isAssignableFrom(type)) return true;
        String n = type.getName();
        return n.equals("com.instagram.feed.media.Media")
                || n.contains("LiveTreeMediaDict")
                || n.contains("MutableMediaDict");
    }

    static boolean isMediaInstance(Object obj) {
        if (obj == null) return false;
        if (mediaClass != null && mediaClass.isInstance(obj)) return true;
        if (liveTreeMediaDictClass != null && liveTreeMediaDictClass.isInstance(obj)) return true;
        String n = obj.getClass().getName();
        return n.equals("com.instagram.feed.media.Media") || n.contains("LiveTreeMediaDict");
    }

    private static String describeMethods(List<Method> methods) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < methods.size(); i++) {
            if (i > 0) sb.append(", ");
            Method m = methods.get(i);
            sb.append(m.getDeclaringClass().getName()).append('.').append(m.getName());
        }
        return sb.toString();
    }

    // ── Reduced options-list patch ──────────────────────────────────────────────
    //
    // IG's newer, simplified reel overflow menu builds its option list via one
    // method that returns a plain ArrayList<MediaOption$Option> (SAVE/UNSAVE,
    // PLAYBACK_CONTROLS, WHY_AM_I_SEEING_THIS, INTERESTED, NOT_INTERESTED,
    // TAG_OPTIONS, REPORT, REQUEST_COMMUNITY_NOTE, DEBUG_STICKER_TRANSLATION) —
    // DOWNLOAD was dropped entirely from this list, unlike the older/fuller
    // overflow-menu code path. Found via field-usage matching on two of its
    // distinctive enum references. Appending DOWNLOAD to the returned (mutable)
    // ArrayList lets it flow through the same generic per-option row builder
    // (LX/5RY;->A0Q -> LX/QIy;->A04) used for every other option here — same
    // shared row primitive the post menu uses, so PostDownloadContextMenuHook's
    // app-wide click-handler hook already covers whatever dispatches its click.
    private static void installReduceOptionsListPatch(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            Class<?> optionClass = classLoader.loadClass("com.instagram.feed.media.mediaoption.MediaOption$Option");
            for (Object v : (Object[]) optionClass.getMethod("values").invoke(null)) {
                if (v.toString().equals("DOWNLOAD")) {
                    downloadOptionValue = v;
                    break;
                }
            }
            if (downloadOptionValue == null) {
                ModuleLog.line("(IE|Reel) ❌ DOWNLOAD enum value not found");
                return;
            }
            final Object download = downloadOptionValue;

            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.enableReelDownload) return;
                    try {
                        Object result = param.getResult();
                        if (result instanceof List<?> list && !list.contains(download)) {
                            @SuppressWarnings("unchecked")
                            List<Object> mutable = (List<Object>) list;
                            mutable.add(download);
                        }
                    } catch (Throwable t) {
                        ModuleLog.line("(IE|Reel) ❌ options-list patch failed: " + t);
                    }
                }
            };

            if (DexKitCache.isCacheValid()) {
                List<Method> cached = DexKitCache.loadMethods("ReelOptionsListBuilder_v2", classLoader);
                if (cached != null && !cached.isEmpty()) {
                    for (Method m : cached) XposedBridge.hookMethod(m, hook);
                    FeatureStatusTracker.setHooked("ReelDownload");
                    ModuleLog.line("(IE|Reel) ✅ Options-list patch hooked (cached): " + describeMethods(cached));
                    return;
                }
            }

            List<Method> targets = findReelOptionsListBuilders(bridge, classLoader);
            if (targets.isEmpty()) {
                ModuleLog.line("(IE|Reel) ⚠️ Reduced options-list builder not found");
                return;
            }

            for (Method target : targets) {
                target.setAccessible(true);
                XposedBridge.hookMethod(target, hook);
            }
            DexKitCache.saveMethods("ReelOptionsListBuilder_v2", targets);
            FeatureStatusTracker.setHooked("ReelDownload");
            ModuleLog.line("(IE|Reel) ✅ Options-list patch hooked: " + describeMethods(targets));

        } catch (Throwable t) {
            ModuleLog.line("(IE|Reel) ❌ installReduceOptionsListPatch: " + t);
        }
    }

    public static List<Method> findReelOptionsListBuilders(DexKitBridge bridge, ClassLoader classLoader) {
        String optionDesc = "Lcom/instagram/feed/media/mediaoption/MediaOption$Option;";
        String[][] combos = {
                {"PLAYBACK_CONTROLS", "UNSAVE"},
                {"PLAYBACK_CONTROLS", "SAVE"},
                {"PLAYBACK_CONTROLS", "WHY_AM_I_SEEING_THIS"},
                {"PLAYBACK_CONTROLS", "NOT_INTERESTED"},
                {"PLAYBACK_CONTROLS", "INTERESTED"},
                {"PLAYBACK_CONTROLS", "REPORT"},
                {"PLAYBACK_CONTROLS", "PICTURE_IN_PICTURE"},
                {"PLAYBACK_CONTROLS", "VIDEO_CAPTIONS"},
                {"PLAYBACK_SPEED", "UNSAVE"},
        };
        String[] returnTypes = {"java.util.ArrayList", "java.util.List"};
        LinkedHashSet<Method> found = new LinkedHashSet<>();

        for (String[] combo : combos) {
            for (String rt : returnTypes) {
                try {
                    MethodMatcher matcher = MethodMatcher.create().returnType(rt);
                    for (String field : combo) {
                        matcher.addUsingField(optionDesc + "->" + field + ":" + optionDesc);
                    }
                    List<MethodData> methods = bridge.findMethod(FindMethod.create().matcher(matcher));
                    for (MethodData md : methods) {
                        try {
                            Method m = md.getMethodInstance(classLoader);
                            if (List.class.isAssignableFrom(m.getReturnType())) found.add(m);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
            if (!found.isEmpty()) return new ArrayList<>(found);
        }

        for (String rt : returnTypes) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .returnType(rt)
                                .addUsingField(optionDesc + "->PLAYBACK_CONTROLS:" + optionDesc)));
                for (MethodData md : methods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        if (List.class.isAssignableFrom(m.getReturnType())) found.add(m);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return new ArrayList<>(found);
    }

    // ── Native download-row unlock ──────────────────────────────────────────────
    //
    // IG 437+ moved the reel overflow menu to the same shared row-builder (QIy) used
    // by the post menu, and it already has a fully-working, native DOWNLOAD row —
    // gated behind two eligibility checks (a "can this media be downloaded" gate and
    // a "is the viewer restricted" gate). When both pass, native code adds the row
    // via the same QIy.A04 primitive posts use, with a working click handler already
    // wired to Instagram's own save-to-camera-roll flow. Bypassing the two gates is
    // far simpler and more robust than reconstructing that row/click machinery
    // ourselves. Found via each gate's distinct hardcoded MobileConfig param ID.
    private static void installNativeDownloadGateUnlock(DexKitBridge bridge, ClassLoader classLoader) {
        installGateHook(bridge, classLoader, "ReelDownloadGate_eligible",
                36313978552585585L,
                "com.instagram.common.session.UserSession", "com.instagram.feed.media.Media",
                true);

        installGateHook(bridge, classLoader, "ReelDownloadGate_restricted",
                36313978552847731L,
                "com.instagram.common.session.UserSession", "boolean",
                false);
    }

    private static void installStringDownloadGates(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook forceTrue = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.enableReelDownload) param.setResult(true);
            }
        };

        if (DexKitCache.isCacheValid()) {
            List<Method> cached = DexKitCache.loadMethods("ReelDownloadGate_strings", classLoader);
            if (cached != null && !cached.isEmpty()) {
                for (Method m : cached) XposedBridge.hookMethod(m, forceTrue);
                ModuleLog.line("(IE|Reel) ✅ string gates hooked (cached): " + describeMethods(cached));
                return;
            }
        }

        String[] markers = {
                "third_party_downloads_enabled",
                "ClipsDownloadUtil",
                "android_purge_26_q3_ClipsDownloadUtil_shouldShowProducerDownloadControls"
        };
        LinkedHashSet<Method> hooked = new LinkedHashSet<>();
        for (String marker : markers) {
            try {
                List<MethodData> methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .usingStrings(marker)
                                .returnType("boolean")));
                for (MethodData md : methods) {
                    try {
                        Method m = md.getMethodInstance(classLoader);
                        if (!isLikelyDownloadGate(m)) continue;
                        m.setAccessible(true);
                        XposedBridge.hookMethod(m, forceTrue);
                        hooked.add(m);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        if (hooked.isEmpty()) {
            ModuleLog.line("(IE|Reel) ⚠️ string download gates not found");
            return;
        }
        List<Method> list = new ArrayList<>(hooked);
        DexKitCache.saveMethods("ReelDownloadGate_strings", list);
        FeatureStatusTracker.setHooked("ReelDownload");
        ModuleLog.line("(IE|Reel) ✅ string gates hooked: " + describeMethods(list));
    }

    private static boolean isLikelyDownloadGate(Method m) {
        boolean hasSession = false;
        boolean hasMedia = false;
        for (Class<?> p : m.getParameterTypes()) {
            if (p.getName().equals("com.instagram.common.session.UserSession")) hasSession = true;
            if (isMediaLike(p)) hasMedia = true;
        }
        return hasSession || hasMedia;
    }

    private static void installGateHook(DexKitBridge bridge, ClassLoader classLoader,
                                         String cacheKey, long configId,
                                         String param1Type, String param2Type,
                                         boolean forcedResult) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.enableReelDownload) param.setResult(forcedResult);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod(cacheKey, classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                ModuleLog.line("(IE|Reel) ✅ Gate unlocked (cached): " +
                        cached.getDeclaringClass().getName() + "." + cached.getName() + " -> " + forcedResult);
                return;
            }
        }

        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .paramTypes(param1Type, param2Type)
                            .returnType("boolean")
                            .usingNumbers(configId)));

            if (methods.isEmpty()) {
                methods = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .paramTypes(param1Type, param2Type)
                                .returnType("boolean")
                                .usingNumbers(List.of(configId))));
            }

            if (methods.isEmpty()) {
                return;
            }

            Method target = methods.get(0).getMethodInstance(classLoader);
            target.setAccessible(true);
            XposedBridge.hookMethod(target, hook);
            DexKitCache.saveMethod(cacheKey, target);
            FeatureStatusTracker.setHooked("ReelDownload");
            ModuleLog.line("(IE|Reel) ✅ Gate unlocked: " +
                    target.getDeclaringClass().getName() + "." + target.getName() + " -> " + forcedResult);

        } catch (Throwable t) {
            ModuleLog.line("(IE|Reel) ❌ installGateHook(" + cacheKey + "): " + t);
        }
    }

    /**
     * Fallback index resolver: structurally locates the carousel position holder on the
     * controller. The holder is the unique non-framework field whose class has exactly
     * ONE int field — this property survives obfuscation renames across IG versions.
     * Values outside [0, 200) are excluded to filter out config constants.
     * Result is cached after first resolution.
     */
    private static int findReelCarouselIndex(Object controller) {
        if (controller == null) return 0;

        if (cachedOuterField != null && cachedInnerField != null) {
            try {
                Object holder = cachedOuterField.get(controller);
                if (holder != null) return cachedInnerField.getInt(holder);
            } catch (Throwable ignored) {}
            cachedOuterField = null;
            cachedInnerField = null;
        }

        int bestIdx = Integer.MAX_VALUE;
        Field bestOuter = null;
        Field bestInner = null;

        Class<?> c = controller.getClass();
        while (c != null && c != Object.class) {
            for (Field outerF : c.getDeclaredFields()) {
                if (outerF.getType().isPrimitive()) continue;
                String pkg = outerF.getType().getName();
                if (pkg.startsWith("android.") || pkg.startsWith("java.")
                        || pkg.startsWith("androidx.") || pkg.startsWith("kotlin.")) continue;
                outerF.setAccessible(true);
                Object nested;
                try { nested = outerF.get(controller); } catch (Throwable ignored) { continue; }
                if (nested == null) continue;

                Field singleIntField = null;
                int intCount = 0;
                Class<?> nc = nested.getClass();
                while (nc != null && nc != Object.class) {
                    String npkg = nc.getName();
                    if (npkg.startsWith("android.") || npkg.startsWith("java.")
                            || npkg.startsWith("androidx.") || npkg.startsWith("kotlin.")) break;
                    for (Field nf : nc.getDeclaredFields()) {
                        if (nf.getType() != int.class) continue;
                        intCount++;
                        singleIntField = nf;
                        if (intCount > 1) break;
                    }
                    if (intCount > 1) break;
                    nc = nc.getSuperclass();
                }

                if (intCount == 1 && singleIntField != null) {
                    singleIntField.setAccessible(true);
                    try {
                        int idx = singleIntField.getInt(nested);
                        if (idx >= 0 && idx < 200 && idx < bestIdx) {
                            bestIdx   = idx;
                            bestOuter = outerF;
                            bestInner = singleIntField;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            c = c.getSuperclass();
        }

        if (bestOuter != null) {
            cachedOuterField = bestOuter;
            cachedInnerField = bestInner;
            return bestIdx;
        }
        return 0;
    }

    /**
     * Primary index resolver: walks the activity's live view hierarchy for a
     * ViewPager / ViewPager2 / ReboundViewPager / horizontal RecyclerView whose adapter
     * item count equals {@code carouselSize} and returns its current data index.
     * Multiple unrelated carousels can coincidentally share the same item count (e.g.
     * two feed posts both showing 4 photos) — trusting the first DFS hit in that case
     * previously misattributed the index to the wrong post. So every match is collected
     * and the result is only trusted when exactly one candidate matches; otherwise the
     * caller falls back to the data-layer field.
     *
     * @return current position [0, carouselSize), or -1 if not found / ambiguous
     */
    static int findCarouselIndexFromView(Context ctx, int carouselSize) {
        if (!(ctx instanceof Activity)) return -1;
        try {
            View root = ((Activity) ctx).getWindow().getDecorView();
            List<Integer> matches = new java.util.ArrayList<>();
            collectCarouselMatches(root, carouselSize, matches);
            return matches.size() == 1 ? matches.get(0) : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** Returns adapter item count, trying RecyclerView-style then PagerAdapter-style. */
    private static int adapterCount(Object adapter) {
        try { return (int) adapter.getClass().getMethod("getItemCount").invoke(adapter); } catch (Throwable ignored) {}
        try { return (int) adapter.getClass().getMethod("getCount").invoke(adapter); } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Recursive DFS over the view tree, collecting the resolved index of every carousel
     * whose adapter size matches — does not stop at the first hit. ViewPager / ViewPager2 /
     * ReboundViewPager are AndroidX / Instagram common-UI classes — stable names, no obfuscation.
     */
    private static void collectCarouselMatches(View view, int carouselSize, List<Integer> out) {
        String cn = view.getClass().getName();

        // ViewPager / ViewPager2 / ReboundViewPager and any subclass
        if (cn.contains("ViewPager")) {
            try {
                Object adapter = view.getClass().getMethod("getAdapter").invoke(view);
                if (adapter != null && adapterCount(adapter) == carouselSize) {
                    // Standard pagers: getCurrentItem()
                    // ReboundViewPager (Instagram looping carousel): getCurrentDataIndex()
                    for (String getter : new String[]{
                            "getCurrentItem", "getCurrentDataIndex",
                            "getCurrentWrappedDataIndex", "getCurrentRawDataIndex"}) {
                        try {
                            int cur = (int) view.getClass().getMethod(getter).invoke(view);
                            if (cur >= 0) { out.add(cur); break; }
                        } catch (NoSuchMethodException ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Horizontal RecyclerView (carousel, not the vertical feed list)
        if (cn.contains("RecyclerView")) {
            try {
                Object adapter = view.getClass().getMethod("getAdapter").invoke(view);
                if (adapter != null && adapterCount(adapter) == carouselSize) {
                    Object lm = view.getClass().getMethod("getLayoutManager").invoke(view);
                    if (lm != null) {
                        try {
                            int orientation = (int) lm.getClass().getMethod("getOrientation").invoke(lm);
                            if (orientation != 0 /* HORIZONTAL */) lm = null;
                        } catch (Throwable ignored) {}
                        if (lm != null) {
                            Integer pos = null;
                            try {
                                int p = (int) lm.getClass()
                                        .getMethod("findFirstCompletelyVisibleItemPosition").invoke(lm);
                                if (p >= 0) pos = p;
                            } catch (Throwable ignored) {}
                            if (pos == null) {
                                try {
                                    int p = (int) lm.getClass()
                                            .getMethod("findFirstVisibleItemPosition").invoke(lm);
                                    if (p >= 0) pos = p;
                                } catch (Throwable ignored) {}
                            }
                            if (pos != null) out.add(pos);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectCarouselMatches(vg.getChildAt(i), carouselSize, out);
            }
        }
    }

    private static void onOptionsBuilt(XC_MethodHook.MethodHookParam param) {
        try {
            Object controller = param.thisObject;
            Object media = null;
            Object buttonAdder = null;
            if (param.args != null) {
                for (Object arg : param.args) {
                    if (arg == null) continue;
                    if (media == null && isMediaInstance(arg)) media = arg;
                    else if (buttonAdder == null && looksLikeButtonAdder(arg)) buttonAdder = arg;
                }
            }
            if (media == null) media = findMediaField(controller);
            if (buttonAdder == null) buttonAdder = findButtonAdderField(controller);

            if (media == null || buttonAdder == null) {
                if (!loggedOptionsBuiltFailure) {
                    loggedOptionsBuiltFailure = true;
                    ModuleLog.line("(IE|Reel) ⚠️ options-built skipped media=" +
                            (media != null) + " adder=" + (buttonAdder != null)
                            + (controller != null ? " cls=" + controller.getClass().getName() : ""));
                }
                return;
            }

            if (activityField == null && controller != null) {
                Class<?> c = controller.getClass();
                while (c != null && c != Object.class && activityField == null) {
                    for (Field f : c.getDeclaredFields()) {
                        if (Activity.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            activityField = f;
                            break;
                        }
                    }
                    c = c.getSuperclass();
                }
            }
            if (activityField == null) {
                if (!loggedOptionsBuiltFailure) {
                    loggedOptionsBuiltFailure = true;
                    ModuleLog.line("(IE|Reel) ❌ no Activity field on controller");
                }
                return;
            }

            Activity activity = (Activity) activityField.get(controller);
            if (activity == null) return;

            if (buttonAdderMethod == null) {
                buttonAdderMethod = findButtonAdderMethod(buttonAdder.getClass());
            }
            if (buttonAdderMethod == null) {
                if (!loggedOptionsBuiltFailure) {
                    loggedOptionsBuiltFailure = true;
                    ModuleLog.line("(IE|Reel) ❌ buttonAdderMethod not found");
                }
                return;
            }

            int icon = resolveDownloadIcon(activity);
            final Activity actCopy = activity;
            final Object mediaCopy = media;
            final Object controllerCopy = controller;

            buttonAdderMethod.invoke(buttonAdder, activity,
                    (View.OnClickListener) v -> startReelDownload(actCopy, mediaCopy, controllerCopy),
                    I18n.t(activity, R.string.ig_dl_title), icon);

        } catch (Throwable t) {
            ModuleLog.line("(IE|Reel) ❌ onOptionsBuilt: " + t);
        }
    }

    private static boolean looksLikeButtonAdder(Object obj) {
        return findButtonAdderMethod(obj.getClass()) != null;
    }

    private static Method findButtonAdderMethod(Class<?> cls) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 4) continue;
                if (!Context.class.isAssignableFrom(p[0])) continue;
                if (!View.OnClickListener.class.isAssignableFrom(p[1])) continue;
                if (p[2] != String.class) continue;
                if (p[3] != int.class) continue;
                m.setAccessible(true);
                return m;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object findMediaField(Object host) {
        if (host == null) return null;
        Class<?> c = host.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!isMediaLike(f.getType())) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(host);
                    if (isMediaInstance(v)) return v;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object findButtonAdderField(Object host) {
        if (host == null) return null;
        Class<?> c = host.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                String pkg = f.getType().getName();
                if (pkg.startsWith("android.") || pkg.startsWith("java.")
                        || pkg.startsWith("androidx.") || pkg.startsWith("kotlin.")) continue;
                f.setAccessible(true);
                try {
                    Object v = f.get(host);
                    if (v != null && looksLikeButtonAdder(v)) return v;
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static void startReelDownload(Context ctx, Object media, Object controller) {
        String username = FeedVideoDownloadHook.extractUsernameFromMediaObject(media);
        if (username == null) username = "reel";

        String mediaId = "0";
        try {
            Object id = media.getClass().getMethod("getId").invoke(media);
            if (id instanceof String s && !s.isEmpty()) mediaId = s;
        } catch (Throwable ignored) {}

        List<String> allUrls = FeedVideoDownloadHook.extractAllUrlsFromMedia(ctx, media);

        if (allUrls.size() > 1) {
            int viewIndex    = findCarouselIndexFromView(ctx, allUrls.size());
            int currentIndex = viewIndex >= 0 ? viewIndex : findReelCarouselIndex(controller);
            final String finalUsername = username;
            final String finalMediaId  = mediaId;
            final int    finalIndex    = currentIndex;
            FeedVideoDownloadHook.mainHandler.post(() ->
                    FeedVideoDownloadHook.showPostDownloadDialog(ctx, allUrls, finalUsername, finalMediaId, finalIndex));
            return;
        }

        if (allUrls.isEmpty()) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_reel_url_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        String url = allUrls.get(0);
        boolean isVid = FeedVideoDownloadHook.isVideoUrl(url);
        final String fn        = FeedVideoDownloadHook.buildFilename(username, "reel", mediaId, isVid);
        final String finalUrl  = url;
        final String finalUser = username;
        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading_reel), Toast.LENGTH_SHORT).show();
        FeedVideoDownloadHook.executor.submit(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(ctx, finalUrl, fn, isVid, finalUser);
                if (!delegated) {
                    FeedVideoDownloadHook.mainHandler.post(() ->
                            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_reel_saved), Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable e) {
                FeedVideoDownloadHook.mainHandler.post(() ->
                        Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_reel_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** Reads the icon drawable ID from MediaOption$Option.DOWNLOAD enum value. */
    private static int resolveDownloadIcon(Context ctx) {
        try {
            Class<?> optionClass = ctx.getClassLoader()
                    .loadClass("com.instagram.feed.media.mediaoption.MediaOption$Option");
            for (Object val : (Object[]) optionClass.getMethod("values").invoke(null)) {
                if (val.toString().contains("DOWNLOAD")) {
                    Field f = val.getClass().getField("iconDrawable");
                    return (int) f.get(val);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
