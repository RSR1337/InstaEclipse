package ps.reso.instaeclipse.mods.ui.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A full custom color palette for Instagram's UI: 15 semantic "slots" (background, surface,
 * text, accent, etc.) that {@link IgColorRemapEngine} and {@link IgThemeEngine} substitute in
 * place of Instagram's own resolved colors.
 */
public class IgThemePalette {

    public static final String SLOT_BACKGROUND = "background";
    public static final String SLOT_SURFACE = "surface";
    public static final String SLOT_PRIMARY_TEXT = "primaryText";
    public static final String SLOT_SECONDARY_TEXT = "secondaryText";
    public static final String SLOT_ACCENT = "accent";
    public static final String SLOT_BUTTON = "button";
    public static final String SLOT_ICON = "icon";
    public static final String SLOT_GLYPH = "glyph";
    public static final String SLOT_DIVIDER = "divider";
    public static final String SLOT_BORDER = "border";
    public static final String SLOT_STATUS_BAR = "statusBar";
    public static final String SLOT_NAVIGATION = "navigation";
    public static final String SLOT_LINK = "link";
    public static final String SLOT_ERROR = "error";
    public static final String SLOT_DESTRUCTIVE = "destructive";

    public static final String[] SLOT_KEYS = {
            SLOT_BACKGROUND, SLOT_SURFACE, SLOT_PRIMARY_TEXT, SLOT_SECONDARY_TEXT, SLOT_ACCENT,
            SLOT_BUTTON, SLOT_ICON, SLOT_GLYPH, SLOT_DIVIDER, SLOT_BORDER, SLOT_STATUS_BAR,
            SLOT_NAVIGATION, SLOT_LINK, SLOT_ERROR, SLOT_DESTRUCTIVE
    };

    public int background;
    public int surface;
    public int primaryText;
    public int secondaryText;
    public int accent;
    public int button;
    public int icon;
    public int glyph;
    public int divider;
    public int border;
    public int statusBar;
    public int navigation;
    public int link;
    public int error;
    public int destructive;

    public IgThemePalette(int background, int surface, int primaryText, int secondaryText, int accent,
                           int button, int icon, int glyph, int divider, int border, int statusBar,
                           int navigation, int link, int error, int destructive) {
        this.background = background;
        this.surface = surface;
        this.primaryText = primaryText;
        this.secondaryText = secondaryText;
        this.accent = accent;
        this.button = button;
        this.icon = icon;
        this.glyph = glyph;
        this.divider = divider;
        this.border = border;
        this.statusBar = statusBar;
        this.navigation = navigation;
        this.link = link;
        this.error = error;
        this.destructive = destructive;
    }

    public IgThemePalette copy() {
        return new IgThemePalette(background, surface, primaryText, secondaryText, accent, button,
                icon, glyph, divider, border, statusBar, navigation, link, error, destructive);
    }

    public int get(String key) {
        switch (key) {
            case SLOT_BACKGROUND: return background;
            case SLOT_SURFACE: return surface;
            case SLOT_PRIMARY_TEXT: return primaryText;
            case SLOT_SECONDARY_TEXT: return secondaryText;
            case SLOT_ACCENT: return accent;
            case SLOT_BUTTON: return button;
            case SLOT_ICON: return icon;
            case SLOT_GLYPH: return glyph;
            case SLOT_DIVIDER: return divider;
            case SLOT_BORDER: return border;
            case SLOT_STATUS_BAR: return statusBar;
            case SLOT_NAVIGATION: return navigation;
            case SLOT_LINK: return link;
            case SLOT_ERROR: return error;
            case SLOT_DESTRUCTIVE: return destructive;
            default: return 0;
        }
    }

    public void set(String key, int color) {
        switch (key) {
            case SLOT_BACKGROUND: background = color; break;
            case SLOT_SURFACE: surface = color; break;
            case SLOT_PRIMARY_TEXT: primaryText = color; break;
            case SLOT_SECONDARY_TEXT: secondaryText = color; break;
            case SLOT_ACCENT: accent = color; break;
            case SLOT_BUTTON: button = color; break;
            case SLOT_ICON: icon = color; break;
            case SLOT_GLYPH: glyph = color; break;
            case SLOT_DIVIDER: divider = color; break;
            case SLOT_BORDER: border = color; break;
            case SLOT_STATUS_BAR: statusBar = color; break;
            case SLOT_NAVIGATION: navigation = color; break;
            case SLOT_LINK: link = color; break;
            case SLOT_ERROR: error = color; break;
            case SLOT_DESTRUCTIVE: destructive = color; break;
            default: break;
        }
    }

    public static void bindCardPreview(Context context, ViewGroup container, IgThemePalette palette) {
        container.removeAllViews();
        if (palette == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        int screenCorner = Math.round(18.0f * density);
        int cardCorner = Math.round(10.0f * density);
        int stroke = Math.round(1.0f * density);
        int pad = Math.round(8.0f * density);
        int bar = Math.round(8.0f * density);
        int avatar = Math.round(18.0f * density);
        int lineH = Math.round(5.0f * density);

        GradientDrawable screen = new GradientDrawable();
        screen.setColor(palette.background);
        screen.setCornerRadius(screenCorner);
        screen.setStroke(stroke, palette.border);
        container.setBackground(screen);
        container.setPadding(0, 0, 0, 0);

        View status = new View(context);
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setColor(palette.statusBar);
        statusBg.setCornerRadii(new float[]{screenCorner, screenCorner, screenCorner, screenCorner, 0, 0, 0, 0});
        status.setBackground(statusBg);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bar);
        container.addView(status, statusLp);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams bodyLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        bodyLp.topMargin = bar;
        bodyLp.bottomMargin = Math.round(14.0f * density);
        bodyLp.setMarginStart(pad);
        bodyLp.setMarginEnd(pad);
        body.setLayoutParams(bodyLp);

        LinearLayout post = new LinearLayout(context);
        post.setOrientation(LinearLayout.HORIZONTAL);
        post.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable postBg = new GradientDrawable();
        postBg.setColor(palette.surface);
        postBg.setCornerRadius(cardCorner);
        post.setBackground(postBg);
        post.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams postLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        postLp.topMargin = pad;
        post.setLayoutParams(postLp);

        View avatarView = new View(context);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(palette.accent);
        avatarView.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(avatar, avatar);
        avatarLp.setMarginEnd(pad);
        post.addView(avatarView, avatarLp);

        LinearLayout lines = new LinearLayout(context);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        View line1 = colorBar(context, palette.primaryText, lineH, cardCorner);
        View line2 = colorBar(context, palette.secondaryText, lineH, cardCorner);
        LinearLayout.LayoutParams line2Lp = (LinearLayout.LayoutParams) line2.getLayoutParams();
        line2Lp.topMargin = Math.round(4.0f * density);
        line2Lp.width = Math.round(48.0f * density);
        lines.addView(line1);
        lines.addView(line2);
        post.addView(lines);
        body.addView(post);

        View accentChip = new View(context);
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(Math.round(8.0f * density));
        chip.setColor(palette.button);
        accentChip.setBackground(chip);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(Math.round(52.0f * density), Math.round(12.0f * density));
        chipLp.topMargin = pad;
        body.addView(accentChip, chipLp);
        container.addView(body);

        View nav = new View(context);
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(palette.navigation);
        navBg.setCornerRadii(new float[]{0, 0, 0, 0, screenCorner, screenCorner, screenCorner, screenCorner});
        nav.setBackground(navBg);
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(14.0f * density));
        navLp.gravity = Gravity.BOTTOM;
        container.addView(nav, navLp);

        View navAccent = new View(context);
        GradientDrawable navDot = new GradientDrawable();
        navDot.setShape(GradientDrawable.OVAL);
        navDot.setColor(palette.accent);
        navAccent.setBackground(navDot);
        int dot = Math.round(6.0f * density);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dot, dot);
        dotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dotLp.bottomMargin = Math.round(4.0f * density);
        container.addView(navAccent, dotLp);
    }

    private static View colorBar(Context context, int color, int height, int corner) {
        View view = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(corner);
        bg.setColor(color);
        view.setBackground(bg);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return view;
    }

    public String toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put(SLOT_BACKGROUND, background);
            o.put(SLOT_SURFACE, surface);
            o.put(SLOT_PRIMARY_TEXT, primaryText);
            o.put(SLOT_SECONDARY_TEXT, secondaryText);
            o.put(SLOT_ACCENT, accent);
            o.put(SLOT_BUTTON, button);
            o.put(SLOT_ICON, icon);
            o.put(SLOT_GLYPH, glyph);
            o.put(SLOT_DIVIDER, divider);
            o.put(SLOT_BORDER, border);
            o.put(SLOT_STATUS_BAR, statusBar);
            o.put(SLOT_NAVIGATION, navigation);
            o.put(SLOT_LINK, link);
            o.put(SLOT_ERROR, error);
            o.put(SLOT_DESTRUCTIVE, destructive);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static IgThemePalette fromJson(String json) {
        IgThemePalette palette = ThemePresets.getById(1).palette.copy();
        if (json == null || json.isEmpty()) return palette;
        try {
            JSONObject o = new JSONObject(json);
            if (o.has(SLOT_BACKGROUND)) palette.background = o.getInt(SLOT_BACKGROUND);
            if (o.has(SLOT_SURFACE)) palette.surface = o.getInt(SLOT_SURFACE);
            if (o.has(SLOT_PRIMARY_TEXT)) palette.primaryText = o.getInt(SLOT_PRIMARY_TEXT);
            if (o.has(SLOT_SECONDARY_TEXT)) palette.secondaryText = o.getInt(SLOT_SECONDARY_TEXT);
            if (o.has(SLOT_ACCENT)) palette.accent = o.getInt(SLOT_ACCENT);
            if (o.has(SLOT_BUTTON)) palette.button = o.getInt(SLOT_BUTTON);
            else if (o.has(SLOT_ACCENT)) palette.button = o.getInt(SLOT_ACCENT);
            if (o.has(SLOT_ICON)) palette.icon = o.getInt(SLOT_ICON);
            if (o.has(SLOT_GLYPH)) palette.glyph = o.getInt(SLOT_GLYPH);
            else if (o.has(SLOT_ICON)) palette.glyph = o.getInt(SLOT_ICON);
            if (o.has(SLOT_DIVIDER)) palette.divider = o.getInt(SLOT_DIVIDER);
            if (o.has(SLOT_BORDER)) palette.border = o.getInt(SLOT_BORDER);
            else if (o.has(SLOT_DIVIDER)) palette.border = o.getInt(SLOT_DIVIDER);
            if (o.has(SLOT_STATUS_BAR)) palette.statusBar = o.getInt(SLOT_STATUS_BAR);
            else if (o.has(SLOT_NAVIGATION)) palette.statusBar = o.getInt(SLOT_NAVIGATION);
            if (o.has(SLOT_STATUS_BAR) && o.has(SLOT_NAVIGATION)) palette.navigation = o.getInt(SLOT_NAVIGATION);
            else if (o.has(SLOT_BACKGROUND)) palette.navigation = o.getInt(SLOT_BACKGROUND);
            else if (o.has(SLOT_NAVIGATION)) palette.navigation = o.getInt(SLOT_NAVIGATION);
            if (o.has(SLOT_LINK)) palette.link = o.getInt(SLOT_LINK);
            if (o.has(SLOT_ERROR)) palette.error = o.getInt(SLOT_ERROR);
            else if (o.has(SLOT_DESTRUCTIVE)) palette.error = o.getInt(SLOT_DESTRUCTIVE);
            if (o.has(SLOT_DESTRUCTIVE)) palette.destructive = o.getInt(SLOT_DESTRUCTIVE);
        } catch (JSONException ignored) {}
        return palette;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(255.0f * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
