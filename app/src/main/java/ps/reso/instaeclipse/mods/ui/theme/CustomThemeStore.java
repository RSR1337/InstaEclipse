package ps.reso.instaeclipse.mods.ui.theme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class CustomThemeStore {

    public static final int MIN_ID = 1000;

    private CustomThemeStore() {}

    public static List<ThemePreset> parse(String json) {
        List<ThemePreset> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                int id = o.optInt("id", 0);
                String name = o.optString("name", "");
                String paletteJson = o.optString("palette", "");
                if (id < MIN_ID || name.isEmpty() || paletteJson.isEmpty()) continue;
                list.add(new ThemePreset(id, ThemePresetCategory.MY_PRESETS, name, IgThemePalette.fromJson(paletteJson)));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public static ThemePreset find(String json, int id) {
        for (ThemePreset preset : parse(json)) {
            if (preset.id == id) return preset;
        }
        return null;
    }

    public static String toJson(List<ThemePreset> presets) {
        JSONArray array = new JSONArray();
        if (presets == null) return array.toString();
        for (ThemePreset preset : presets) {
            if (preset == null || preset.category != ThemePresetCategory.MY_PRESETS) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", preset.id);
                o.put("name", preset.name);
                o.put("palette", preset.palette.toJson());
                array.put(o);
            } catch (Throwable ignored) {}
        }
        return array.toString();
    }

    public static int nextId(List<ThemePreset> presets) {
        int max = MIN_ID - 1;
        if (presets != null) {
            for (ThemePreset preset : presets) {
                if (preset.id > max) max = preset.id;
            }
        }
        return max + 1;
    }
}
