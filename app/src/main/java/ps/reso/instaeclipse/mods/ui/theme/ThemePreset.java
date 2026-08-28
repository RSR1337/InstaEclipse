package ps.reso.instaeclipse.mods.ui.theme;

public class ThemePreset {
    public final int id;
    public final ThemePresetCategory category;
    public final String name;
    public final IgThemePalette palette;

    public ThemePreset(int id, ThemePresetCategory category, String name, IgThemePalette palette) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.palette = palette;
    }
}
