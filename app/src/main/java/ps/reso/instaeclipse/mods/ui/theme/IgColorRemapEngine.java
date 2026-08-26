package ps.reso.instaeclipse.mods.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.util.SparseIntArray;
import android.view.View;

import androidx.core.view.ViewCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Two-tier color substitution: an exact/RGB lookup table built from every color the palette
 * actually touches, plus a luminance/hue "fuzzy" classifier for any color that slips through
 * (so arbitrary colors baked into Instagram's own drawables/bitmaps still land on a roughly
 * plausible slot instead of staying unthemed).
 */
public final class IgColorRemapEngine {

    private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Object FUZZY_LOCK = new Object();
    private static final int CACHE_MISS = Integer.MIN_VALUE;
    private static final int FUZZY_CACHE_LIMIT = 1024;

    private static volatile boolean built;
    private static volatile SparseIntArray exactTable;
    private static volatile SparseIntArray rgbTable;
    private static volatile SparseIntArray fuzzyCache;
    private static volatile int moduleUiDepth;

    private static volatile int fuzzyBg, fuzzySurface, fuzzyPrimary, fuzzySecondary, fuzzyButton, fuzzyLink, fuzzyDestructive;
    private static volatile boolean fuzzyDarkTheme;

    private IgColorRemapEngine() {}

    public static boolean isBypassing() {
        return BYPASS_DEPTH.get() > 0 || moduleUiDepth > 0;
    }

    public static int getModuleUiDepth() {
        return moduleUiDepth;
    }

    public static boolean shouldSkipRemap(Object hookTarget) {
        return isBypassing() || isModuleUiTarget(hookTarget);
    }

    public static boolean isModuleUiTarget(Object target) {
        return target instanceof View && isModuleUiView((View) target);
    }

    public static boolean isModuleUiView(View view) {
        for (View current = view; current != null; current = parentView(current)) {
            if (Boolean.TRUE.equals(current.getTag(R.id.tag_module_dialog_root))) return true;
        }
        return false;
    }

    public static void markModuleDialogView(View view) {
        if (view != null) view.setTag(R.id.tag_module_dialog_root, Boolean.TRUE);
    }

    private static View parentView(View view) {
        Object parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    public static void enterModuleUi() {
        moduleUiDepth++;
    }

    public static void leaveModuleUi() {
        if (moduleUiDepth > 0) moduleUiDepth--;
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
        built = false;
        exactTable = null;
        rgbTable = null;
        fuzzyCache = null;
    }

    public static boolean isReady() {
        return built && rgbTable != null;
    }

    public static void ensureBuilt(Context context) {
        if (built || context == null || !IgThemeEngine.isActive()) return;
        synchronized (IgColorRemapEngine.class) {
            if (built) return;
            buildTable(context);
            built = true;
            int size = (rgbTable != null ? rgbTable.size() : 0) + (exactTable != null ? exactTable.size() : 0);
            ModuleLog.line("(InstaEclipse | Theme): color remap table size=" + size);
        }
    }

    public static int remap(int color) {
        if (!IgThemeEngine.isActive() || isBypassing() || color == 0) return color;
        SparseIntArray exact = exactTable;
        SparseIntArray rgb = rgbTable;
        if (rgb == null) return color;
        if (exact != null) {
            int exactHit = exact.get(color, CACHE_MISS);
            if (exactHit != CACHE_MISS) return exactHit;
        }
        int mappedRgb = rgb.get(color & 0x00FFFFFF, CACHE_MISS);
        if (mappedRgb != CACHE_MISS) {
            return (0xFF000000 & color) | (0x00FFFFFF & mappedRgb);
        }
        SparseIntArray fuzzy = fuzzyCache;
        if (fuzzy != null) {
            int cached;
            synchronized (FUZZY_LOCK) {
                cached = fuzzy.get(color, CACHE_MISS);
            }
            if (cached != CACHE_MISS) return cached;
        }
        int result = remapFuzzy(color);
        if (fuzzy != null) {
            synchronized (FUZZY_LOCK) {
                if (fuzzy.size() < FUZZY_CACHE_LIMIT) fuzzy.put(color, result);
            }
        }
        return result;
    }

    public static int remapIfChanged(int color) {
        int remapped = remap(color);
        return remapped == color ? color : remapped;
    }

    public static int remapExact(int color) {
        if (!IgThemeEngine.isActive() || isBypassing() || color == 0) return color;
        SparseIntArray exact = exactTable;
        SparseIntArray rgb = rgbTable;
        if (rgb == null) return color;
        if (exact != null) {
            int exactHit = exact.get(color, CACHE_MISS);
            if (exactHit != CACHE_MISS) return exactHit;
        }
        int mappedRgb = rgb.get(color & 0x00FFFFFF, CACHE_MISS);
        if (mappedRgb != CACHE_MISS) {
            return (0xFF000000 & color) | (0x00FFFFFF & mappedRgb);
        }
        return color;
    }

    public static long remapComposePacked(long packed) {
        if (!IgThemeEngine.isActive() || isBypassing() || packed == 0L) return packed;
        if ((packed & 63L) != 0L) return packed;
        int argb = (int) (packed >>> 32);
        if (argb == 0) return packed;
        int remapped = remap(argb);
        if (remapped == argb) return packed;
        return (((long) remapped) << 32) | (packed & 0xFFFFFFFFL);
    }

    public static ColorStateList remapColorStateList(ColorStateList original) {
        if (original == null || !IgThemeEngine.isActive() || isBypassing()) return original;
        try {
            int def = original.getDefaultColor();
            int remappedDef = remap(def);
            if (!original.isStateful()) {
                if (remappedDef == def) return original;
                final ColorStateList[] holder = new ColorStateList[1];
                withBypass(() -> holder[0] = ColorStateList.valueOf(remappedDef));
                return holder[0] != null ? holder[0] : original;
            }
            Field colorsField = colorStateListColorsField();
            Field statesField = colorStateListStatesField();
            if (colorsField == null) {
                if (remappedDef == def) return original;
                final ColorStateList[] holder = new ColorStateList[1];
                withBypass(() -> holder[0] = ColorStateList.valueOf(remappedDef));
                return holder[0] != null ? holder[0] : original;
            }
            int[] colors = (int[]) colorsField.get(original);
            if (colors == null || colors.length == 0) return original;
            int[] remapped = remapIntArray(colors);
            if (remapped == colors) return original;
            int[][] states = statesField != null ? (int[][]) statesField.get(original) : null;
            if (states != null && states.length == remapped.length) {
                final int[][] st = cloneStates(states);
                final int[] cols = remapped;
                final ColorStateList[] holder = new ColorStateList[1];
                withBypass(() -> holder[0] = new ColorStateList(st, cols));
                return holder[0] != null ? holder[0] : original;
            }
            final ColorStateList[] holder = new ColorStateList[1];
            withBypass(() -> holder[0] = ColorStateList.valueOf(remappedDef));
            return holder[0] != null ? holder[0] : original;
        } catch (Throwable ignored) {
            return original;
        }
    }

    private static int[][] cloneStates(int[][] states) {
        int[][] copy = new int[states.length][];
        for (int i = 0; i < states.length; i++) {
            copy[i] = states[i] != null ? states[i].clone() : null;
        }
        return copy;
    }

    public static int[] remapIntArray(int[] colors) {
        if (colors == null || !IgThemeEngine.isActive() || isBypassing()) return colors;
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
        if (original == null || original.isEmpty() || !IgThemeEngine.isActive() || isBypassing()) return original;
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
        if (!IgThemeEngine.isActive() || isBypassing() || args == null || index < 0 || index >= args.length) return;
        if (!(args[index] instanceof Integer)) return;
        int original = (Integer) args[index];
        int remapped = remap(original);
        if (remapped != original) args[index] = remapped;
    }

    public static int remapResourceColor(Resources res, int resId, int resolved) {
        if (!IgThemeEngine.isActive() || isBypassing()) return resolved;
        Integer override = IgThemeEngine.colorForResource(resId);
        return override != null ? override : remap(resolved);
    }

    private static int remapFuzzy(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return color;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double lum = ((r * 0.299d) + (g * 0.587d) + (b * 0.114d)) / 255.0d;
        int target;
        if (isAccentBlue(r, g, b)) target = fuzzyButton;
        else if (isDestructiveRed(r, g, b)) target = fuzzyDestructive;
        else if (isLinkBlue(r, g, b)) target = fuzzyLink;
        else if (fuzzyDarkTheme) {
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

    private static void cacheFuzzyPalette(IgThemePalette palette) {
        fuzzyBg = palette.background;
        fuzzySurface = palette.surface;
        fuzzyPrimary = palette.primaryText;
        fuzzySecondary = palette.secondaryText;
        fuzzyButton = palette.button;
        fuzzyLink = palette.link;
        fuzzyDestructive = palette.destructive;
        int bg = palette.background;
        int r = (bg >> 16) & 0xFF, g = (bg >> 8) & 0xFF, b = bg & 0xFF;
        fuzzyDarkTheme = ((r * 0.299d) + (g * 0.587d) + (b * 0.114d)) / 255.0d < 0.2d;
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

    private static void buildTable(Context context) {
        SparseIntArray exact = new SparseIntArray(128);
        SparseIntArray rgb = new SparseIntArray(512);
        IgThemePalette palette = IgThemeEngine.getActivePalette();
        cacheFuzzyPalette(palette);
        Resources res = context.getResources();
        String pkg = res.getResourcePackageName(android.R.color.black);
        mapCanonical(exact, rgb, palette);
        mapResourceNames(exact, rgb, res, pkg, palette);
        mapFromSlots(exact, rgb, res, pkg, palette);
        ClassLoader cl = context.getClassLoader();
        mapAllResourceColors(exact, rgb, res, cl, palette);
        mapComposePalettes(exact, rgb, cl, palette);
        exactTable = exact;
        rgbTable = rgb;
        fuzzyCache = new SparseIntArray(256);
    }

    private static void mapAllResourceColors(SparseIntArray exact, SparseIntArray rgb, Resources res, ClassLoader cl, IgThemePalette palette) {
        if (cl == null) return;
        try {
            Class<?> cls = cl.loadClass("com.instagram.android.R$color");
            scanColorClass(exact, rgb, res, palette, cls);
        } catch (Throwable ignored) {}
        try {
            Class<?> cls = cl.loadClass("com.instagram.barcelona.R$color");
            scanColorClass(exact, rgb, res, palette, cls);
        } catch (Throwable ignored) {}
    }

    private static void scanColorClass(SparseIntArray exact, SparseIntArray rgb, Resources res, IgThemePalette palette, Class<?> cls) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.getType() != int.class || (field.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) continue;
            int slot = IgThemeEngine.slotForColorName(field.getName());
            if (slot < 0) continue;
            try {
                int resId = field.getInt(null);
                int original = sampleColor(res, resId);
                if (original != 0) put(exact, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
            } catch (Throwable ignored) {}
        }
    }

    private static void mapComposePalettes(SparseIntArray exact, SparseIntArray rgb, ClassLoader cl, IgThemePalette palette) {
        if (cl == null) return;
        String[] classes = {
                "com.instagram.compose.core.theme.BaseColors",
                "com.instagram.compose.core.theme.BasePrismColors",
                "com.instagram.compose.core.theme.BasePrismColorsV2"
        };
        for (String className : classes) {
            try {
                Class<?> cls = cl.loadClass(className);
                for (Field field : cls.getDeclaredFields()) {
                    if (field.getType() != long.class || (field.getModifiers() & Modifier.STATIC) == 0) continue;
                    try {
                        long packed = field.getLong(null);
                        int original = (int) (packed >>> 32);
                        if (original == 0) continue;
                        int slot = IgThemeEngine.slotForColorName(field.getName());
                        int target = slot >= 0 ? palette.get(IgThemePalette.SLOT_KEYS[slot]) : remapFuzzy(original);
                        put(exact, rgb, original, target);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void mapCanonical(SparseIntArray exact, SparseIntArray rgb, IgThemePalette palette) {
        put(exact, rgb, ViewCompat.MEASURED_STATE_MASK, palette.background);
        put(exact, rgb, -15986668, palette.background);
        put(exact, rgb, -15066598, palette.surface);
        put(exact, rgb, -14803426, palette.surface);
        put(exact, rgb, -14277082, palette.surface);
        put(exact, rgb, -13224394, palette.divider);
        put(exact, rgb, -15592942, palette.background);
        put(exact, rgb, -15461356, palette.surface);
        put(exact, rgb, -14605528, palette.surface);
        put(exact, rgb, -13486786, palette.surface);
        put(exact, rgb, -657931, palette.primaryText);
        put(exact, rgb, -1052689, palette.secondaryText);
        put(exact, rgb, -2368549, palette.secondaryText);
        put(exact, rgb, -3684409, palette.secondaryText);
        put(exact, rgb, -5723992, palette.secondaryText);
        put(exact, rgb, -9211021, palette.secondaryText);
        put(exact, rgb, -11184811, palette.secondaryText);
        put(exact, rgb, -4407100, palette.secondaryText);
        put(exact, rgb, -1, palette.primaryText);
        put(exact, rgb, -16738826, palette.button);
        put(exact, rgb, -15173646, palette.button);
        put(exact, rgb, -11903495, palette.button);
        put(exact, rgb, -1226410, palette.destructive);
        put(exact, rgb, -53184, palette.destructive);
        put(exact, rgb, -217321, palette.accent);
        put(exact, rgb, -14934750, palette.surface);
        put(exact, rgb, -14471112, palette.surface);
        put(exact, rgb, -1312770, palette.surface);
        put(exact, rgb, -789001, palette.background);
        put(exact, rgb, -328966, palette.primaryText);
        put(exact, rgb, -2039584, palette.secondaryText);
        put(exact, rgb, -9079435, palette.secondaryText);
        put(exact, rgb, -6381922, palette.secondaryText);
        put(exact, rgb, -12434878, palette.border);
        put(exact, rgb, -13882324, palette.border);
        put(exact, rgb, -13092808, palette.border);
        put(exact, rgb, -11219201, palette.link);
        put(exact, rgb, -16763029, palette.link);
        put(exact, rgb, -16754781, palette.link);
        put(exact, rgb, -10960094, palette.accent);
        put(exact, rgb, -9360, palette.accent);
        put(exact, rgb, -12079105, palette.accent);
        put(exact, rgb, -9388801, palette.accent);
        put(exact, rgb, -4989953, palette.accent);
        put(exact, rgb, -2035201, palette.surface);
        put(exact, rgb, -16747316, palette.button);
        put(exact, rgb, -2130706433, IgThemePalette.withAlpha(palette.primaryText, 0.5f));
        put(exact, rgb, -2131364363, IgThemePalette.withAlpha(palette.primaryText, 0.5f));
        put(exact, rgb, Integer.MIN_VALUE, IgThemePalette.withAlpha(palette.background, 0.5f));
        put(exact, rgb, -872415232, IgThemePalette.withAlpha(palette.background, 0.8f));
        put(exact, rgb, 855638016, IgThemePalette.withAlpha(palette.background, 0.2f));
        put(exact, rgb, 872415231, IgThemePalette.withAlpha(palette.primaryText, 0.2f));
        put(exact, rgb, -7434610, palette.secondaryText);
        put(exact, rgb, -6710887, palette.secondaryText);
        put(exact, rgb, -13882324, palette.secondaryText);
        put(exact, rgb, -2415052, palette.destructive);
        put(exact, rgb, -14888625, palette.accent);
    }

    private static void mapResourceNames(SparseIntArray exact, SparseIntArray rgb, Resources res, String pkg, IgThemePalette palette) {
        String[] colorNames = {"bds_black", "igds_prism_black", "bds_white", "igds_prism_gray_00", "bds_grey_0", "bds_grey_1",
                "bds_grey_2", "bds_grey_3", "bds_grey_4", "bds_grey_6", "bds_grey_7", "bds_grey_8", "bds_grey_9", "bds_grey_10",
                "bds_grey_11", "bds_grey_12", "bds_grey_16", "bds_grey_18", "bds_grey_21", "bds_grey_22", "bds_grey_24",
                "igds_prism_gray_08", "igds_prism_gray_10", "igds_prism_gray_0000", "igds_prism_gray_0100",
                "igds_prism_gray_0500", "igds_prism_gray_0800", "igds_prism_gray_1000", "igds_prism_gray_1300",
                "igds_prism_gray_1400", "igds_prism_gray_1500", "igds_prism_gray_1600",
                "emphasized_action_color", "badge_color", "igds_prism_indigo_1000",
                "bds_blue_1", "bds_blue_2", "bds_red_5", "bds_red_6", "igds_primary_background", "bottom_sheet_undo_redo_color",
                // Instagram 444+ semantic colors (resources renamed igds_color_* → igds_*)
                "igds_primary_text", "igds_primary_text_disabled", "igds_secondary_text", "igds_secondary_background",
                "igds_primary_button", "igds_primary_icon", "igds_secondary_icon", "igds_separator", "igds_stroke",
                "igds_link", "igds_error_or_destructive", "igds_elevated_background", "igds_elevated_separator",
                "igds_elevated_highlight_background", "igds_photo_border", "igds_photo_placeholder", "igds_selected_text_background",
                "igds_tag_or_toast_background", "igds_context_menu_background_color", "igds_context_menu_item_background_color",
                "igds_creation_menu_background", "igds_creation_button_destructive", "igds_icon_on_color", "igds_link_on_color",
                "igds_pill_active_text", "igds_success", "igds_secondary_button_on_media",
                "igds_secondary_button_elevated_pressed_panavision", "igds_secondary_media_button_onblack_panavision_updated"};
        for (String name : colorNames) {
            int id = res.getIdentifier(name, "color", pkg);
            if (id == 0) continue;
            int slot = IgThemeEngine.slotForColorName(name);
            if (slot < 0) continue;
            int original = sampleColor(res, id);
            if (original != 0) put(exact, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
        }
    }

    private static void mapFromSlots(SparseIntArray exact, SparseIntArray rgb, Resources res, String pkg, IgThemePalette palette) {
        SparseIntArray colorResToSlot = IgThemeEngine.getColorResToSlot();
        if (colorResToSlot == null) return;
        for (int i = 0; i < colorResToSlot.size(); i++) {
            int resId = colorResToSlot.keyAt(i);
            int slot = colorResToSlot.valueAt(i);
            int original = sampleColor(res, resId);
            if (original != 0) put(exact, rgb, original, palette.get(IgThemePalette.SLOT_KEYS[slot]));
        }
    }

    private static void put(SparseIntArray exact, SparseIntArray rgb, int from, int to) {
        if (from == 0 || to == 0) return;
        int fromAlpha = (from >>> 24) & 0xFF;
        int toAlpha = (to >>> 24) & 0xFF;
        if (fromAlpha != 255 || toAlpha != 255) exact.put(from, to);
        rgb.put(from & 0x00FFFFFF, 0x00FFFFFF & to);
    }
}
