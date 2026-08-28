package ps.reso.instaeclipse.mods.ui.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ThemePresets {

    private static final List<ThemePreset> PRESETS;

    static {
        List<ThemePreset> list = new ArrayList<>();
        list.add(p(1, ThemePresetCategory.DARK, "Midnight Eclipse", 0xFF0A1628, 0xFF122036, 0xFFF2F4F7, 0xFF8BA0B8, 0xFF4DA3FF, 0xFFF2F4F7, 0xFF1C3048, 0xFF0A1628, 0xFF5BB0FF, 0xFFFF5252));
        list.add(p(2, ThemePresetCategory.DARK, "Obsidian", 0xFF161616, 0xFF222222, 0xFFF5F5F5, 0xFF9E9E9E, 0xFF7C5CFF, 0xFFF5F5F5, 0xFF2E2E2E, 0xFF161616, 0xFF8B74FF, 0xFFFF5252));
        list.add(p(3, ThemePresetCategory.DARK, "Ocean Night", 0xFF071824, 0xFF0E2A3A, 0xFFE8F4FA, 0xFF7AA3B5, 0xFF2EC4E6, 0xFFE8F4FA, 0xFF163848, 0xFF071824, 0xFF4DD4F0, 0xFFFF5252));
        list.add(p(4, ThemePresetCategory.DARK, "Forest Night", 0xFF0C1610, 0xFF16241A, 0xFFEAF3EA, 0xFF86A890, 0xFF5BD68A, 0xFFEAF3EA, 0xFF1E3224, 0xFF0C1610, 0xFF6EE09A, 0xFFE53935));
        list.add(p(5, ThemePresetCategory.DARK, "Ember", 0xFF1A1210, 0xFF2A1C18, 0xFFFFF1E6, 0xFFC4A090, 0xFFFF7A45, 0xFFFFF1E6, 0xFF3A2820, 0xFF1A1210, 0xFFFF8B5C, 0xFFFF5722));
        list.add(p(6, ThemePresetCategory.DARK, "Slate", 0xFF1B1E24, 0xFF2A2E38, 0xFFF0F2F5, 0xFF9AA3B0, 0xFF6B8CAF, 0xFFF0F2F5, 0xFF3A404C, 0xFF1B1E24, 0xFF8BA8C4, 0xFFFF5252));
        list.add(p(7, ThemePresetCategory.DARK, "Velvet", 0xFF1C1018, 0xFF2C1824, 0xFFFFF0F5, 0xFFC090A8, 0xFFE85A9A, 0xFFFFF0F5, 0xFF3C2430, 0xFF1C1018, 0xFFFF7AB0, 0xFFFF5252));
        list.add(p(8, ThemePresetCategory.DARK, "Royal", 0xFF100E24, 0xFF1C1838, 0xFFEEE8FF, 0xFF9A90C0, 0xFF7B61FF, 0xFFEEE8FF, 0xFF2C2850, 0xFF100E24, 0xFF9B87FF, 0xFFFF5252));
        list.add(p(9, ThemePresetCategory.DARK, "Copper", 0xFF1A120C, 0xFF2C1E14, 0xFFF8EBD8, 0xFFB09070, 0xFFE09050, 0xFFF8EBD8, 0xFF3C2C1C, 0xFF1A120C, 0xFFF0A868, 0xFFE53935));
        list.add(p(10, ThemePresetCategory.DARK, "Frost", 0xFF0C141C, 0xFF162028, 0xFFE8F4FC, 0xFF88A8C0, 0xFFA0D0E8, 0xFFE8F4FC, 0xFF243038, 0xFF0C141C, 0xFFB8E0F0, 0xFFFF5252));
        list.add(p(11, ThemePresetCategory.DARK, "Graphite", 0xFF1C1C1E, 0xFF2C2C2E, 0xFFF5F5F7, 0xFFA0A0A5, 0xFFFFD60A, 0xFFF5F5F7, 0xFF3C3C3E, 0xFF1C1C1E, 0xFFFFE14A, 0xFFFF453A));
        list.add(p(12, ThemePresetCategory.DARK, "Twilight", 0xFF16121E, 0xFF241E30, 0xFFF4EEF8, 0xFFA090B0, 0xFFFF8A80, 0xFFF4EEF8, 0xFF342C40, 0xFF16121E, 0xFFFFA8A0, 0xFFFF5252));
        list.add(p(13, ThemePresetCategory.DARK, "Mocha", 0xFF16120E, 0xFF241E18, 0xFFF5EDE4, 0xFFA89888, 0xFFC4A882, 0xFFF5EDE4, 0xFF342C24, 0xFF16120E, 0xFFD4C09A, 0xFFE53935));
        list.add(p(14, ThemePresetCategory.DARK, "Cyber", 0xFF080C1C, 0xFF101830, 0xFFE0F0FF, 0xFF7090B8, 0xFF00F0FF, 0xFFE0F0FF, 0xFF1C2848, 0xFF080C1C, 0xFF40F8FF, 0xFFFF1744));
        list.add(p(15, ThemePresetCategory.DARK, "Crimson", 0xFF1A0C10, 0xFF2A141C, 0xFFFFE8EC, 0xFFC08090, 0xFFFF3355, 0xFFFFE8EC, 0xFF3C1C28, 0xFF1A0C10, 0xFFFF5C78, 0xFFFF1744));
        list.add(p(16, ThemePresetCategory.DARK, "Sage", 0xFF141814, 0xFF202820, 0xFFE8F0E8, 0xFF8A9A8A, 0xFFA8C4A0, 0xFFE8F0E8, 0xFF303830, 0xFF141814, 0xFFBCD4B4, 0xFFE53935));
        list.add(p(17, ThemePresetCategory.DARK, "Indigo", 0xFF0C1020, 0xFF181E34, 0xFFE8ECFF, 0xFF8890B8, 0xFF536DFE, 0xFFE8ECFF, 0xFF282E48, 0xFF0C1020, 0xFF7388FF, 0xFFFF5252));
        list.add(p(18, ThemePresetCategory.DARK, "Amber", 0xFF1A1608, 0xFF2A2410, 0xFFFFF8E1, 0xFFC0A860, 0xFFFFC107, 0xFFFFF8E1, 0xFF3C3418, 0xFF1A1608, 0xFFFFD54F, 0xFFE53935));
        list.add(p(19, ThemePresetCategory.DARK, "Steel", 0xFF14181C, 0xFF222830, 0xFFECEFF1, 0xFF90A4AE, 0xFF7EB8D4, 0xFFECEFF1, 0xFF323840, 0xFF14181C, 0xFF9CCCE0, 0xFFFF5252));
        list.add(p(20, ThemePresetCategory.DARK, "Orchid", 0xFF180E1C, 0xFF281A2C, 0xFFFCE4FF, 0xFFB080C0, 0xFFE040FB, 0xFFFCE4FF, 0xFF38283C, 0xFF180E1C, 0xFFEA70FF, 0xFFFF5252));
        list.add(p(21, ThemePresetCategory.DARK, "Abyss", 0xFF050E14, 0xFF0C1C24, 0xFFD8F0F8, 0xFF608898, 0xFF1DE9B6, 0xFFD8F0F8, 0xFF142830, 0xFF050E14, 0xFF4EF0C8, 0xFFFF5252));
        list.add(p(22, ThemePresetCategory.DARK, "Sandstone", 0xFF1C1814, 0xFF2C261E, 0xFFFFF6E8, 0xFFC0A888, 0xFFE8B86D, 0xFFFFF6E8, 0xFF3C3428, 0xFF1C1814, 0xFFF0C888, 0xFFE53935));
        list.add(p(23, ThemePresetCategory.DARK, "Plum", 0xFF160E14, 0xFF241824, 0xFFF8E8F0, 0xFFB090A8, 0xFFCE93D8, 0xFFF8E8F0, 0xFF342834, 0xFF160E14, 0xFFD8A8E0, 0xFFFF5252));
        list.add(p(24, ThemePresetCategory.DARK, "Glacier", 0xFF10181A, 0xFF1C282C, 0xFFE8F4F4, 0xFF80A0A8, 0xFF80CBC4, 0xFFE8F4F4, 0xFF2C383C, 0xFF10181A, 0xFFA0DCD6, 0xFFFF5252));
        list.add(p(25, ThemePresetCategory.DARK, "Blood Moon", 0xFF180E10, 0xFF28181C, 0xFFFFE8E8, 0xFFC09090, 0xFFFF6B6B, 0xFFFFE8E8, 0xFF382428, 0xFF180E10, 0xFFFF8888, 0xFFFF1744));

        list.add(p(31, ThemePresetCategory.AMOLED, "Pure OLED", 0xFF000000, 0xFF0A0A0A, 0xFFF5F5F5, 0xFF8E8E93, 0xFF0095F6, 0xFFF5F5F5, 0xFF1A1A1A, 0xFF000000, 0xFF0095F6, 0xFFFF453A));
        list.add(p(32, ThemePresetCategory.AMOLED, "Carbon", 0xFF000000, 0xFF121212, 0xFFEDEDED, 0xFF9A9A9A, 0xFF64D2FF, 0xFFEDEDED, 0xFF1C1C1C, 0xFF000000, 0xFF64D2FF, 0xFFFF453A));
        list.add(p(33, ThemePresetCategory.AMOLED, "Matrix", 0xFF000000, 0xFF001400, 0xFFB8FFB8, 0xFF4A9A4A, 0xFF00FF66, 0xFFB8FFB8, 0xFF002000, 0xFF000000, 0xFF00FF66, 0xFFFF1744));
        list.add(p(34, ThemePresetCategory.AMOLED, "Amethyst OLED", 0xFF000000, 0xFF0E0814, 0xFFF0E6FF, 0xFF9A88B8, 0xFFC77DFF, 0xFFF0E6FF, 0xFF1A1024, 0xFF000000, 0xFFC77DFF, 0xFFFF5252));
        list.add(p(35, ThemePresetCategory.AMOLED, "Aurora", 0xFF000000, 0xFF001018, 0xFFE6FFF9, 0xFF6AA898, 0xFF5EF0C8, 0xFFE6FFF9, 0xFF001C24, 0xFF000000, 0xFF5EF0C8, 0xFFFF5252));
        list.add(p(36, ThemePresetCategory.AMOLED, "Neon Rose", 0xFF000000, 0xFF140810, 0xFFFFF0F5, 0xFFB07090, 0xFFFF2D95, 0xFFFFF0F5, 0xFF241018, 0xFF000000, 0xFFFF5CB0, 0xFFFF453A));
        list.add(p(37, ThemePresetCategory.AMOLED, "Solar", 0xFF000000, 0xFF141000, 0xFFFFF8E1, 0xFFB09040, 0xFFFFB300, 0xFFFFF8E1, 0xFF241C00, 0xFF000000, 0xFFFFC933, 0xFFFF453A));
        list.add(p(38, ThemePresetCategory.AMOLED, "Ice OLED", 0xFF000000, 0xFF000814, 0xFFE3F2FD, 0xFF6A90B8, 0xFFB3E5FC, 0xFFE3F2FD, 0xFF001428, 0xFF000000, 0xFFC8EEFF, 0xFFFF453A));
        list.add(p(39, ThemePresetCategory.AMOLED, "Ruby", 0xFF000000, 0xFF140004, 0xFFFFEBEE, 0xFFB06070, 0xFFFF1744, 0xFFFFEBEE, 0xFF240008, 0xFF000000, 0xFFFF5252, 0xFFFF1744));
        list.add(p(40, ThemePresetCategory.AMOLED, "Lime", 0xFF000000, 0xFF081400, 0xFFF0FFE0, 0xFF709040, 0xFFC6FF00, 0xFFF0FFE0, 0xFF102000, 0xFF000000, 0xFFD4FF40, 0xFFFF1744));
        list.add(p(41, ThemePresetCategory.AMOLED, "Cobalt", 0xFF000000, 0xFF000818, 0xFFE8EEFF, 0xFF6070B0, 0xFF2962FF, 0xFFE8EEFF, 0xFF001030, 0xFF000000, 0xFF448AFF, 0xFFFF453A));
        list.add(p(42, ThemePresetCategory.AMOLED, "Magenta OLED", 0xFF000000, 0xFF100010, 0xFFFFE0FF, 0xFFB060B0, 0xFFFF00E5, 0xFFFFE0FF, 0xFF200020, 0xFF000000, 0xFFFF40F0, 0xFFFF453A));
        list.add(p(43, ThemePresetCategory.AMOLED, "Sunset", 0xFF000000, 0xFF140800, 0xFFFFF3E0, 0xFFB07840, 0xFFFF6D00, 0xFFFFF3E0, 0xFF241000, 0xFF000000, 0xFFFF8A33, 0xFFFF453A));
        list.add(p(44, ThemePresetCategory.AMOLED, "Periwinkle", 0xFF000000, 0xFF080814, 0xFFEEF0FF, 0xFF8088B8, 0xFF9FA8DA, 0xFFEEF0FF, 0xFF141428, 0xFF000000, 0xFFB3BAE8, 0xFFFF5252));
        list.add(p(45, ThemePresetCategory.AMOLED, "Champagne", 0xFF000000, 0xFF12100A, 0xFFFFF8E7, 0xFFB0A070, 0xFFFFE082, 0xFFFFF8E7, 0xFF221C12, 0xFF000000, 0xFFFFEAA0, 0xFFFF453A));
        list.add(p(46, ThemePresetCategory.AMOLED, "Tangerine", 0xFF000000, 0xFF140C00, 0xFFFFF0E0, 0xFFB08850, 0xFFFFAB40, 0xFFFFF0E0, 0xFF241800, 0xFF000000, 0xFFFFC066, 0xFFFF453A));
        list.add(p(47, ThemePresetCategory.AMOLED, "Blush", 0xFF000000, 0xFF14080C, 0xFFFFF0F3, 0xFFB08090, 0xFFFF80AB, 0xFFFFF0F3, 0xFF241018, 0xFF000000, 0xFFFFA0C0, 0xFFFF453A));
        list.add(p(48, ThemePresetCategory.AMOLED, "Violet OLED", 0xFF000000, 0xFF0A0018, 0xFFF0E8FF, 0xFF8870C0, 0xFF7C4DFF, 0xFFF0E8FF, 0xFF140028, 0xFF000000, 0xFF9575FF, 0xFFFF5252));
        list.add(p(49, ThemePresetCategory.AMOLED, "Ember OLED", 0xFF000000, 0xFF140A00, 0xFFFFF0E8, 0xFFB07040, 0xFFFF5722, 0xFFFFF0E8, 0xFF241400, 0xFF000000, 0xFFFF7043, 0xFFFF453A));
        list.add(p(50, ThemePresetCategory.AMOLED, "Wave", 0xFF000000, 0xFF000C14, 0xFFE0F7FA, 0xFF508090, 0xFF00B8D4, 0xFFE0F7FA, 0xFF001824, 0xFF000000, 0xFF26C6DA, 0xFFFF453A));
        list.add(p(51, ThemePresetCategory.AMOLED, "Ivory", 0xFF000000, 0xFF0C0C0C, 0xFFFAFAF5, 0xFF9A9A90, 0xFFF5F0E6, 0xFFFAFAF5, 0xFF1C1C1C, 0xFF000000, 0xFFFFF8E8, 0xFFFF453A));
        list.add(p(52, ThemePresetCategory.AMOLED, "Coral", 0xFF000000, 0xFF140808, 0xFFFFF0EE, 0xFFB07870, 0xFFFF6F61, 0xFFFFF0EE, 0xFF241010, 0xFF000000, 0xFFFF8A80, 0xFFFF453A));
        list.add(p(53, ThemePresetCategory.AMOLED, "Toxic", 0xFF000000, 0xFF101400, 0xFFF5FFE0, 0xFF889040, 0xFFEEFF41, 0xFFF5FFE0, 0xFF1C2400, 0xFF000000, 0xFFF4FF70, 0xFFFF1744));
        list.add(p(54, ThemePresetCategory.AMOLED, "Wine", 0xFF000000, 0xFF100008, 0xFFFFE8F0, 0xFFA06078, 0xFFE04080, 0xFFFFE8F0, 0xFF200010, 0xFF000000, 0xFFEC5C98, 0xFFFF453A));
        list.add(p(55, ThemePresetCategory.AMOLED, "Plasma", 0xFF000000, 0xFF080014, 0xFFFCE4FF, 0xFFA070B8, 0xFFEA80FC, 0xFFFCE4FF, 0xFF140028, 0xFF000000, 0xFFF0A0FF, 0xFFFF5252));
        PRESETS = Collections.unmodifiableList(list);
    }

    private static ThemePreset p(int id, ThemePresetCategory category, String name,
                                 int bg, int surface, int primary, int secondary, int accent,
                                 int icon, int divider, int nav, int link, int destructive) {
        return new ThemePreset(id, category, name,
                new IgThemePalette(bg, surface, primary, secondary, accent, accent, icon, icon, divider, divider, nav, bg, link, destructive, destructive));
    }

    public static List<ThemePreset> byCategory(ThemePresetCategory category) {
        List<ThemePreset> list = new ArrayList<>();
        for (ThemePreset preset : PRESETS) {
            if (preset.category == category) list.add(preset);
        }
        return list;
    }

    public static ThemePreset getById(int id) {
        for (ThemePreset preset : PRESETS) {
            if (preset.id == id) return preset;
        }
        return PRESETS.get(0);
    }

    public static ThemePresetCategory categoryForId(int id) {
        if (id >= CustomThemeStore.MIN_ID) return ThemePresetCategory.MY_PRESETS;
        for (ThemePreset preset : PRESETS) {
            if (preset.id == id) return preset.category;
        }
        return ThemePresetCategory.DARK;
    }

    private ThemePresets() {}
}
