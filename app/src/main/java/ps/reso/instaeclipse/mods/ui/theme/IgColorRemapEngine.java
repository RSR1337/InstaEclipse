package ps.reso.instaeclipse.mods.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class IgColorRemapEngine {

    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Object FUZZY_LOCK = new Object();
    private static final int CACHE_MISS = Integer.MIN_VALUE;
    private static final int FUZZY_CACHE_LIMIT = 1024;
    private static final int MAX_NUDGES = 24;

    static final String[] COMPOSE_PALETTE_CLASSES = {
            "com.instagram.compose.core.theme.BaseColors",
            "com.instagram.compose.core.theme.BasePrismColors",
            "com.instagram.compose.core.theme.BasePrismColorsV2"
    };

    private static final int S_BG = 0, S_SURFACE = 1, S_TEXT = 2, S_SECONDARY = 3, S_ACCENT = 4, S_BUTTON = 5,
            S_DIVIDER = 8, S_BORDER = 9, S_LINK = 12, S_DESTRUCTIVE = 14;

    private static final int[] CANONICAL_SHARED = {
            0x0095F6, S_BUTTON, 0x1877F2, S_BUTTON, 0x4A5DF9, S_BUTTON, 0x0074CC, S_BUTTON,
            0xED4956, S_DESTRUCTIVE, 0xFF3040, S_DESTRUCTIVE, 0xFE1D37, S_DESTRUCTIVE,
            0xE4010D, S_DESTRUCTIVE, 0xDB2634, S_DESTRUCTIVE,
            0xFCAF17, S_ACCENT, 0x58C322, S_ACCENT, 0xFFDB70, S_ACCENT, 0x47AFFF, S_ACCENT,
            0x70BCFF, S_ACCENT, 0xB3DBFF, S_ACCENT, 0x1CD14F, S_ACCENT,
            0x54CEFF, S_LINK, 0x00376B, S_LINK, 0x0057A3, S_LINK
    };

    private static final int[] CANONICAL_DARK_SOURCE = {
            0x000000, S_BG, 0x0C1014, S_BG, 0x121212, S_BG, 0x141414, S_BG, 0xF3F5F7, S_BG,
            0x1A1A1A, S_SURFACE, 0x1E1E1E, S_SURFACE, 0x262626, S_SURFACE, 0x212328, S_SURFACE,
            0x32353E, S_SURFACE, 0x25292E, S_SURFACE, 0x1C1D22, S_SURFACE, 0x233038, S_SURFACE,
            0xEBF7FE, S_SURFACE, 0xE9EDF0, S_SURFACE, 0xE0F1FF, S_SURFACE,
            0x363636, S_DIVIDER,
            0x2C2C2C, S_BORDER, 0x424242, S_BORDER, 0x383838, S_BORDER,
            0xFFFFFF, S_TEXT, 0xF5F5F5, S_TEXT, 0xF8F9F9, S_TEXT, 0xFAFAFA, S_TEXT,
            0xEFEFEF, S_SECONDARY, 0xDBDBDB, S_SECONDARY, 0xC7C7C7, S_SECONDARY, 0xA8A8A8, S_SECONDARY,
            0x737373, S_SECONDARY, 0x555555, S_SECONDARY, 0xBCC0C4, S_SECONDARY, 0xA2AAB4, S_SECONDARY,
            0x838993, S_SECONDARY, 0x6F7680, S_SECONDARY, 0x5E646D, S_SECONDARY, 0xDBDFE4, S_SECONDARY,
            0xE0E0E0, S_SECONDARY, 0x757575, S_SECONDARY, 0x9E9E9E, S_SECONDARY, 0x8E8E8E, S_SECONDARY,
            0x999999, S_SECONDARY
    };

    private static final int[] CANONICAL_LIGHT_SOURCE = {
            0xFFFFFF, S_BG,
            0xFAFAFA, S_SURFACE, 0xF8F9F9, S_SURFACE, 0xF5F5F5, S_SURFACE, 0xEFEFEF, S_SURFACE,
            0xF3F5F7, S_SURFACE, 0xEBF7FE, S_SURFACE, 0xE9EDF0, S_SURFACE, 0xE0F1FF, S_SURFACE,
            0xDBDBDB, S_DIVIDER, 0xDBDFE4, S_DIVIDER, 0xE0E0E0, S_DIVIDER,
            0xC7C7C7, S_BORDER, 0xBCC0C4, S_BORDER,
            0xA8A8A8, S_SECONDARY, 0x8E8E8E, S_SECONDARY, 0x999999, S_SECONDARY, 0x9E9E9E, S_SECONDARY,
            0x737373, S_SECONDARY, 0x757575, S_SECONDARY, 0xA2AAB4, S_SECONDARY, 0x838993, S_SECONDARY,
            0x6F7680, S_SECONDARY, 0x5E646D, S_SECONDARY, 0x555555, S_SECONDARY,
            0x000000, S_TEXT, 0x0C1014, S_TEXT, 0x121212, S_TEXT, 0x141414, S_TEXT, 0x1A1A1A, S_TEXT,
            0x1E1E1E, S_TEXT, 0x262626, S_TEXT, 0x212328, S_TEXT, 0x1C1D22, S_TEXT, 0x233038, S_TEXT,
            0x25292E, S_TEXT, 0x2C2C2C, S_TEXT, 0x32353E, S_TEXT, 0x363636, S_TEXT, 0x383838, S_TEXT,
            0x424242, S_TEXT
    };

    private static volatile boolean built;
    private static volatile boolean builtSourceDark;
    private static volatile int generation;
    private static volatile SparseIntArray exactTable;
    private static volatile SparseIntArray rgbTable;
    private static volatile SparseIntArray fuzzyCache;
    private static volatile SparseIntArray moduleProtectedRgb;
    private static final AtomicInteger moduleUiOpen = new AtomicInteger();
    private static final AtomicInteger liveModuleRoots = new AtomicInteger();
    private static final Map<Field, Long> composeFieldOriginals = new LinkedHashMap<>();
    private static volatile boolean composeFieldsApplied;

    private static volatile int fuzzyBg, fuzzySurface, fuzzyPrimary, fuzzySecondary, fuzzyButton, fuzzyLink, fuzzyDestructive;
    private static volatile int scrimDark, scrimLight;
    private static volatile boolean sourceDark = true;

    private static final View.OnAttachStateChangeListener MODULE_ROOT_LISTENER = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(View v) {
            liveModuleRoots.incrementAndGet();
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            if (liveModuleRoots.decrementAndGet() < 0) liveModuleRoots.set(0);
        }
    };

    private IgColorRemapEngine() {}

    public static void registerModuleUiColors(int... colors) {
        if (colors == null || colors.length == 0) return;
        SparseIntArray map = new SparseIntArray(colors.length + 8);
        for (int color : colors) {
            map.put(color & 0x00FFFFFF, 1);
        }
        moduleProtectedRgb = map;
    }

    private static boolean isModuleUiColor(int color) {
        SparseIntArray map = moduleProtectedRgb;
        return map != null && map.get(color & 0x00FFFFFF, 0) == 1;
    }

    public static boolean isProtectedModuleColor(int color) {
        return moduleUiOpen.get() > 0 && isModuleUiColor(color);
    }

    public static boolean isBypassing() {
        return BYPASS_DEPTH.get() > 0;
    }

    public static boolean shouldSkipRemap(Object hookTarget) {
        return isBypassing() || isModuleUiTarget(hookTarget);
    }

    public static boolean shouldSkipDrawable(Object drawable, int color) {
        if (isBypassing()) return true;
        Object callback = drawable instanceof Drawable ? ((Drawable) drawable).getCallback() : null;
        for (int i = 0; i < 8 && callback instanceof Drawable; i++) {
            callback = ((Drawable) callback).getCallback();
        }
        if (callback instanceof View) return isModuleUiView((View) callback);
        return isProtectedModuleColor(color);
    }

    public static boolean isModuleUiTarget(Object target) {
        return target instanceof View && isModuleUiView((View) target);
    }

    public static boolean isModuleUiView(View view) {
        if (liveModuleRoots.get() <= 0) return false;
        for (View current = view; current != null; current = parentView(current)) {
            if (current.getTag(R.id.tag_module_dialog_root) != null) return true;
        }
        return false;
    }

    public static void markModuleDialogView(View view) {
        if (view == null) return;
        view.setTag(R.id.tag_module_dialog_root, Boolean.TRUE);
        trackModuleRoot(view);
    }

    public static void markModuleTree(View root) {
        if (root == null) return;
        markModuleDialogView(root);
        markChildren(root);
    }

    private static void markChildren(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setTag(R.id.tag_module_dialog_root, Boolean.TRUE);
            markChildren(child);
        }
    }

    private static void trackModuleRoot(View root) {
        if (root.getTag(R.id.tag_module_root_tracked) != null) return;
        root.setTag(R.id.tag_module_root_tracked, Boolean.TRUE);
        root.addOnAttachStateChangeListener(MODULE_ROOT_LISTENER);
        if (root.isAttachedToWindow()) liveModuleRoots.incrementAndGet();
    }

    private static View parentView(View view) {
        Object parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    public static void enterModuleUi() {
        moduleUiOpen.incrementAndGet();
    }

    public static void leaveModuleUi() {
        if (moduleUiOpen.decrementAndGet() < 0) moduleUiOpen.set(0);
    }

    public static void withBypass(Runnable action) {
        int depth = BYPASS_DEPTH.get() + 1;
        BYPASS_DEPTH.set(depth);
        try {
            action.run();
        } finally {
            BYPASS_DEPTH.set(depth - 1);
        }
    }

    private static void enterBypass() {
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
    }

    private static void exitBypass() {
        BYPASS_DEPTH.set(BYPASS_DEPTH.get() - 1);
    }

    public static int sampleColor(Resources res, int resId) {
        if (res == null || resId == 0) return 0;
        enterBypass();
        try {
            return res.getColor(resId, null);
        } catch (Throwable th) {
            return 0;
        } finally {
            exitBypass();
        }
    }

    public static void invalidate() {
        synchronized (IgColorRemapEngine.class) {
            restoreComposePaletteFields();
            built = false;
            exactTable = null;
            rgbTable = null;
            fuzzyCache = null;
        }
    }

    public static boolean isReady() {
        return built && rgbTable != null;
    }

    public static int generation() {
        return generation;
    }

    public static void ensureBuilt(Context context) {
        if (built || context == null || !IgThemeEngine.isActive()) return;
        synchronized (IgColorRemapEngine.class) {
            if (built) return;
            restoreComposePaletteFields();
            buildTable(context);
            applyComposePaletteFields(context.getClassLoader());
            built = true;
            generation++;
            int size = (rgbTable != null ? rgbTable.size() : 0) + (exactTable != null ? exactTable.size() : 0);
            ModuleLog.line("(InstaEclipse | Theme): color remap table size=" + size
                    + " sourceDark=" + builtSourceDark
                    + " composeFields=" + composeFieldOriginals.size());
        }
    }

    public static void checkSourceMode(Context context) {
        if (!built || context == null || !IgThemeEngine.isActive()) return;
        if (detectSourceDark(context) == builtSourceDark) return;
        ModuleLog.line("(InstaEclipse | Theme): Instagram light/dark mode changed; rebuilding");
        IgThemeEngine.invalidate();
        IgThemeEngine.ensureInitialized(context);
        ensureBuilt(context);
    }

    public static int remap(int color) {
        if (!IgThemeEngine.isActive() || color == 0) return color;
        if (isPaletteColor(color)) return color;
        SparseIntArray exact = exactTable;
        SparseIntArray rgb = rgbTable;
        if (rgb == null) return color;
        int result = exact != null ? exact.get(color, CACHE_MISS) : CACHE_MISS;
        if (result == CACHE_MISS) result = remapScrim(color);
        if (result == CACHE_MISS) {
            int mappedRgb = rgb.get(color & 0x00FFFFFF, CACHE_MISS);
            if (mappedRgb != CACHE_MISS) {
                result = (0xFF000000 & color) | (0x00FFFFFF & mappedRgb);
            }
        }
        if (result == CACHE_MISS) {
            SparseIntArray fuzzy = fuzzyCache;
            if (fuzzy != null) {
                int cached;
                synchronized (FUZZY_LOCK) {
                    cached = fuzzy.get(color, CACHE_MISS);
                }
                if (cached != CACHE_MISS) result = cached;
            }
            if (result == CACHE_MISS) {
                result = remapFuzzy(color);
                if (fuzzy != null) {
                    synchronized (FUZZY_LOCK) {
                        if (fuzzy.size() < FUZZY_CACHE_LIMIT) fuzzy.put(color, result);
                    }
                }
            }
        }
        return result;
    }

    public static int remapExact(int color) {
        if (!IgThemeEngine.isActive() || color == 0) return color;
        if (isPaletteColor(color)) return color;
        SparseIntArray exact = exactTable;
        SparseIntArray rgb = rgbTable;
        if (rgb == null) return color;
        int result = exact != null ? exact.get(color, CACHE_MISS) : CACHE_MISS;
        if (result == CACHE_MISS) result = remapScrim(color);
        if (result == CACHE_MISS) {
            int mappedRgb = rgb.get(color & 0x00FFFFFF, CACHE_MISS);
            if (mappedRgb != CACHE_MISS) {
                result = (0xFF000000 & color) | (0x00FFFFFF & mappedRgb);
            }
        }
        return result == CACHE_MISS ? color : result;
    }

    private static int remapScrim(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0xFF || alpha == 0) return CACHE_MISS;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min > 12) return CACHE_MISS;
        if (max <= 24) return (alpha << 24) | (0x00FFFFFF & scrimDark);
        if (min >= 236) return (alpha << 24) | (0x00FFFFFF & scrimLight);
        return CACHE_MISS;
    }

    public static long remapComposePacked(long packed) {
        if (!IgThemeEngine.isActive() || packed == 0L) return packed;
        if ((packed & 63L) != 0L) return packed;
        int argb = (int) (packed >>> 32);
        if (argb == 0) return packed;
        int remapped = remap(argb);
        if (remapped == argb) return packed;
        return (((long) remapped) << 32) | (packed & 0xFFFFFFFFL);
    }

    public static ColorStateList remapColorStateList(ColorStateList original) {
        if (original == null || !IgThemeEngine.isActive()) return original;
        try {
            int def = original.getDefaultColor();
            int remappedDef = remap(def);
            if (!original.isStateful()) {
                if (remappedDef == def) return original;
                return valueOfBypassed(remappedDef, original);
            }
            Field colorsField = colorStateListColorsField();
            Field statesField = colorStateListStatesField();
            if (colorsField == null) {
                if (remappedDef == def) return original;
                return valueOfBypassed(remappedDef, original);
            }
            int[] colors = (int[]) colorsField.get(original);
            if (colors == null || colors.length == 0) return original;
            int[] remapped = remapIntArray(colors);
            if (remapped == colors) return original;
            int[][] states = statesField != null ? (int[][]) statesField.get(original) : null;
            if (states != null && states.length == remapped.length) {
                final int[][] st = cloneStates(states);
                final ColorStateList[] holder = new ColorStateList[1];
                withBypass(() -> holder[0] = new ColorStateList(st, remapped));
                return holder[0] != null ? holder[0] : original;
            }
            return valueOfBypassed(remappedDef, original);
        } catch (Throwable ignored) {
            return original;
        }
    }

    private static ColorStateList valueOfBypassed(int color, ColorStateList fallback) {
        final ColorStateList[] holder = new ColorStateList[1];
        withBypass(() -> holder[0] = ColorStateList.valueOf(color));
        return holder[0] != null ? holder[0] : fallback;
    }

    private static int[][] cloneStates(int[][] states) {
        int[][] copy = new int[states.length][];
        for (int i = 0; i < states.length; i++) {
            copy[i] = states[i] != null ? states[i].clone() : null;
        }
        return copy;
    }

    public static int[] remapIntArray(int[] colors) {
        if (colors == null || !IgThemeEngine.isActive()) return colors;
        int[] out = null;
        for (int i = 0; i < colors.length; i++) {
            int remapped = remap(colors[i]);
            if (remapped != colors[i]) {
                if (out == null) {
                    out = colors.clone();
                }
                out[i] = remapped;
            }
        }
        return out != null ? out : colors;
    }

    public static Map<?, ?> remapNativeColorMap(Map<?, ?> original) {
        if (original == null || original.isEmpty() || !IgThemeEngine.isActive()) return original;
        Map<Object, Object> out = new HashMap<>();
        boolean changed = false;
        for (Map.Entry<?, ?> entry : original.entrySet()) {
            Object value = entry.getValue();
            Object remapped = remapNativeColorValue(value);
            out.put(entry.getKey(), remapped);
            if (remapped != value && (remapped == null || !remapped.equals(value))) changed = true;
        }
        return changed ? out : original;
    }

    private static Object remapNativeColorValue(Object value) {
        if (value instanceof Integer) {
            int original = (Integer) value;
            if (((original >>> 24) & 0xFF) != 0xFF) return value;
            int remapped = remap(original);
            return remapped == original ? value : remapped;
        }
        if (value instanceof String) {
            String hex = (String) value;
            if (hex.length() >= 7 && hex.charAt(0) == '#') {
                try {
                    int original = android.graphics.Color.parseColor(hex);
                    int remapped = remap(original);
                    if (remapped != original) {
                        return String.format("#%08X", remapped);
                    }
                } catch (Throwable ignored) {}
            }
        }
        return value;
    }

    private static volatile Field colorStateListColors;
    private static volatile Field colorStateListStates;

    private static Field colorStateListColorsField() {
        Field field = colorStateListColors;
        if (field != null) return field;
        field = findColorStateListField("mColors", "mDefaultColors");
        colorStateListColors = field;
        return field;
    }

    private static Field colorStateListStatesField() {
        Field field = colorStateListStates;
        if (field != null) return field;
        field = findColorStateListField("mStateSpecs", "mStates");
        colorStateListStates = field;
        return field;
    }

    private static Field findColorStateListField(String... names) {
        for (String name : names) {
            try {
                Field field = ColorStateList.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static void applyRemapArg(Object[] args, int index) {
        if (!IgThemeEngine.isActive() || args == null || index < 0 || index >= args.length) return;
        if (!(args[index] instanceof Integer)) return;
        int original = (Integer) args[index];
        int remapped = remap(original);
        if (remapped != original) args[index] = remapped;
    }

    private static int remapFuzzy(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return color;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (looksLikePhotographic(r, g, b)) return color;
        double lum = ((r * 0.299d) + (g * 0.587d) + (b * 0.114d)) / 255.0d;
        int target;
        if (isAccentBlue(r, g, b)) target = fuzzyButton;
        else if (isDestructiveRed(r, g, b)) target = fuzzyDestructive;
        else if (isLinkBlue(r, g, b)) target = fuzzyLink;
        else if (sourceDark) {
            if (lum < 0.08d) target = fuzzyBg;
            else if (lum < 0.22d) target = fuzzySurface;
            else if (lum > 0.65d) target = fuzzyPrimary;
            else target = fuzzySecondary;
        } else if (lum > 0.92d) target = fuzzyBg;
        else if (lum > 0.75d) target = fuzzySurface;
        else if (lum < 0.25d) target = fuzzyPrimary;
        else target = fuzzySecondary;
        return (alpha << 24) | (0x00FFFFFF & target);
    }

    public static boolean isPaletteColor(int color) {
        int[] palette = IgThemeEngine.getActivePaletteRgb();
        if (palette == null) return false;
        int rgb = color & 0x00FFFFFF;
        for (int value : palette) {
            if (value == rgb) return true;
        }
        return false;
    }

    private static boolean looksLikePhotographic(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min < 28) return false;
        if (isAccentBlue(r, g, b) || isLinkBlue(r, g, b) || isDestructiveRed(r, g, b)) return false;
        float lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f;
        return lum > 0.06f && lum < 0.94f;
    }

    private static void cacheFuzzyPalette(IgThemePalette palette, boolean dark) {
        fuzzyBg = palette.background;
        fuzzySurface = palette.surface;
        fuzzyPrimary = palette.primaryText;
        fuzzySecondary = palette.secondaryText;
        fuzzyButton = palette.button;
        fuzzyLink = palette.link;
        fuzzyDestructive = palette.destructive;
        boolean bgDarker = luminance(palette.background) <= luminance(palette.primaryText);
        scrimDark = bgDarker ? palette.background : palette.primaryText;
        scrimLight = bgDarker ? palette.primaryText : palette.background;
        sourceDark = dark;
    }

    private static double luminance(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return ((r * 0.299d) + (g * 0.587d) + (b * 0.114d)) / 255.0d;
    }

    private static boolean isAccentBlue(int r, int g, int b) {
        return b > 160 && b > r + 30 && b > g + 10;
    }

    private static boolean isLinkBlue(int r, int g, int b) {
        return b > 120 && b >= r && g < b;
    }

    private static boolean isDestructiveRed(int r, int g, int b) {
        return r > 180 && r > g + 60 && r > b + 60;
    }

    static boolean detectSourceDark(Context context) {
        Resources res = context.getResources();
        try {
            int id = res.getIdentifier("igds_primary_background", "color", CommonUtils.IG_PACKAGE_NAME);
            if (id != 0) {
                int color = sampleColor(res, id);
                if (color != 0) return luminance(color) < 0.5d;
            }
        } catch (Throwable ignored) {}
        return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void buildTable(Context context) {
        IgThemePalette palette = IgThemeEngine.getActivePalette();
        Resources res = context.getResources();
        boolean dark = detectSourceDark(context);
        builtSourceDark = dark;
        ClassLoader cl = context.getClassLoader();
        for (int pass = 0; pass < 2; pass++) {
            SparseIntArray exact = new SparseIntArray(32);
            SparseIntArray rgb = new SparseIntArray(512);
            cacheFuzzyPalette(palette, dark);
            mapCanonical(exact, rgb, palette, dark);
            mapResourceNames(rgb, res, palette);
            mapFromSlots(rgb, res, palette);
            mapComposePalettes(exact, rgb, cl, palette);
            if (pass == 0 && separatePaletteFromSources(rgb, palette)) {
                IgThemeEngine.onPaletteMutated();
                continue;
            }
            exactTable = exact;
            rgbTable = rgb;
            break;
        }
        fuzzyCache = new SparseIntArray(256);
    }

    private static boolean separatePaletteFromSources(SparseIntArray rgb, IgThemePalette palette) {
        boolean changed = false;
        for (String key : IgThemePalette.SLOT_KEYS) {
            int color = palette.get(key);
            if (!collides(rgb, color)) continue;
            int nudged = color;
            for (int i = 0; i < MAX_NUDGES && collides(rgb, nudged); i++) {
                nudged = nudge(nudged);
            }
            palette.set(key, nudged);
            changed = true;
        }
        return changed;
    }

    private static boolean collides(SparseIntArray rgb, int color) {
        int key = color & 0x00FFFFFF;
        int target = rgb.get(key, CACHE_MISS);
        return target != CACHE_MISS && (target & 0x00FFFFFF) != key;
    }

    private static int nudge(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += r >= 128 ? -1 : 1;
        g += g >= 128 ? -1 : 1;
        b += b >= 128 ? -1 : 1;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void mapComposePalettes(SparseIntArray exact, SparseIntArray rgb, ClassLoader cl, IgThemePalette palette) {
        if (cl == null) return;
        for (String className : COMPOSE_PALETTE_CLASSES) {
            try {
                Class<?> cls = cl.loadClass(className);
                for (Field field : cls.getDeclaredFields()) {
                    if (field.getType() != long.class || (field.getModifiers() & Modifier.STATIC) == 0) continue;
                    try {
                        field.setAccessible(true);
                        long packed = readComposeField(field);
                        int original = (int) (packed >>> 32);
                        if (original == 0) continue;
                        int slot = IgThemeEngine.slotForColorName(field.getName());
                        if (slot < 0) continue;
                        put(exact, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private static long readComposeField(Field field) throws IllegalAccessException {
        Long saved = composeFieldOriginals.get(field);
        if (saved != null) return saved;
        return field.getLong(null);
    }

    private static void applyComposePaletteFields(ClassLoader cl) {
        if (cl == null || composeFieldsApplied) return;
        int applied = 0;
        for (String className : COMPOSE_PALETTE_CLASSES) {
            try {
                Class<?> cls = cl.loadClass(className);
                for (Field field : cls.getDeclaredFields()) {
                    if (field.getType() != long.class || (field.getModifiers() & Modifier.STATIC) == 0) continue;
                    try {
                        field.setAccessible(true);
                        clearFinal(field);
                        long original = field.getLong(null);
                        if (!composeFieldOriginals.containsKey(field)) {
                            composeFieldOriginals.put(field, original);
                        } else {
                            original = composeFieldOriginals.get(field);
                        }
                        long remapped = remapComposePackedForField(original);
                        if (remapped != original) {
                            field.setLong(null, remapped);
                            applied++;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        composeFieldsApplied = true;
        if (applied > 0) {
            ModuleLog.line("(InstaEclipse | Theme): Compose palette fields rewritten=" + applied);
        }
    }

    private static long remapComposePackedForField(long packed) {
        if (packed == 0L) return packed;
        if ((packed & 63L) != 0L) return packed;
        int argb = (int) (packed >>> 32);
        if (argb == 0) return packed;
        int remapped = remapExact(argb);
        if (remapped == argb && ((argb >>> 24) & 0xFF) == 0xFF) {
            remapped = remapFuzzy(argb);
        }
        if (remapped == argb) return packed;
        return (((long) remapped) << 32) | (packed & 0xFFFFFFFFL);
    }

    private static void restoreComposePaletteFields() {
        if (composeFieldOriginals.isEmpty()) {
            composeFieldsApplied = false;
            return;
        }
        int restored = 0;
        for (Map.Entry<Field, Long> entry : composeFieldOriginals.entrySet()) {
            try {
                Field field = entry.getKey();
                field.setAccessible(true);
                clearFinal(field);
                field.setLong(null, entry.getValue());
                restored++;
            } catch (Throwable ignored) {}
        }
        composeFieldsApplied = false;
        if (restored > 0) {
            ModuleLog.line("(InstaEclipse | Theme): Compose palette fields restored=" + restored);
        }
    }

    private static void clearFinal(Field field) {
        try {
            Field accessFlags = Field.class.getDeclaredField("accessFlags");
            accessFlags.setAccessible(true);
            accessFlags.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Throwable ignored) {
            try {
                Field modifiers = Field.class.getDeclaredField("modifiers");
                modifiers.setAccessible(true);
                modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            } catch (Throwable ignored2) {}
        }
    }

    private static void mapCanonical(SparseIntArray exact, SparseIntArray rgb, IgThemePalette palette, boolean dark) {
        putPairs(rgb, palette, CANONICAL_SHARED);
        putPairs(rgb, palette, dark ? CANONICAL_DARK_SOURCE : CANONICAL_LIGHT_SOURCE);
        exact.put(0x80FFFFFF, IgThemePalette.withAlpha(scrimLight, 0.5f));
        exact.put(0x80F5F5F5, IgThemePalette.withAlpha(scrimLight, 0.5f));
        exact.put(0x33FFFFFF, IgThemePalette.withAlpha(scrimLight, 0.2f));
        exact.put(0x80000000, IgThemePalette.withAlpha(scrimDark, 0.5f));
        exact.put(0xCC000000, IgThemePalette.withAlpha(scrimDark, 0.8f));
        exact.put(0x33000000, IgThemePalette.withAlpha(scrimDark, 0.2f));
    }

    private static void putPairs(SparseIntArray rgb, IgThemePalette palette, int[] pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            rgb.put(pairs[i], 0x00FFFFFF & palette.get(IgThemePalette.SLOT_KEYS[pairs[i + 1]]));
        }
    }

    private static void mapResourceNames(SparseIntArray rgb, Resources res, IgThemePalette palette) {
        for (String name : IgThemeEngine.CORE_COLOR_NAMES) {
            int id = res.getIdentifier(name, "color", CommonUtils.IG_PACKAGE_NAME);
            if (id == 0) continue;
            int slot = IgThemeEngine.slotForColorName(name);
            if (slot < 0) continue;
            int original = sampleColor(res, id);
            if (original != 0) put(null, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
        }
    }

    private static void mapFromSlots(SparseIntArray rgb, Resources res, IgThemePalette palette) {
        SparseIntArray colorResToSlot = IgThemeEngine.getColorResToSlot();
        if (colorResToSlot == null) return;
        for (int i = 0; i < colorResToSlot.size(); i++) {
            int resId = colorResToSlot.keyAt(i);
            int slot = colorResToSlot.valueAt(i);
            int original = sampleColor(res, resId);
            if (original != 0) put(null, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
        }
    }

    private static void put(SparseIntArray exact, SparseIntArray rgb, int from, int to) {
        if (from == 0 || to == 0) return;
        if (((from >>> 24) & 0xFF) != 0xFF) {
            if (exact != null) exact.put(from, (from & 0xFF000000) | (to & 0x00FFFFFF));
            return;
        }
        rgb.put(from & 0x00FFFFFF, 0x00FFFFFF & to);
    }
}
