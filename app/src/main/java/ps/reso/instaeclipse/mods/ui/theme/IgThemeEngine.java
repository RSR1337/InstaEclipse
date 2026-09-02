package ps.reso.instaeclipse.mods.ui.theme;

import android.content.Context;
import android.content.res.Resources;
import android.util.SparseIntArray;
import android.util.TypedValue;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;


public final class IgThemeEngine {

    private static volatile IgThemePalette activePalette;
    private static volatile SparseIntArray attrToSlot;
    private static volatile SparseIntArray colorResToSlot;
    private static volatile ClassLoader hostClassLoader;
    private static volatile boolean initialized;

    private static final Pattern PRISM_TONE = Pattern.compile("gr[ae]y_(\\d{4})");

    static final String[] CORE_COLOR_NAMES = {
            "badge_color", "emphasized_action_color", "bottom_sheet_undo_redo_color",
            "bds_black", "bds_white",
            "bds_blue_0", "bds_blue_1", "bds_blue_2", "bds_blue_3", "bds_blue_4", "bds_blue_6", "bds_blue_7", "bds_blue_8",
            "bds_green_5", "bds_green_6", "bds_green_7",
            "bds_grey_0", "bds_grey_1", "bds_grey_2", "bds_grey_3", "bds_grey_4", "bds_grey_6", "bds_grey_7",
            "bds_grey_8", "bds_grey_9", "bds_grey_10", "bds_grey_11", "bds_grey_12", "bds_grey_13",
            "bds_grey_16", "bds_grey_18", "bds_grey_21", "bds_grey_22", "bds_grey_24",
            "bds_red_5", "bds_red_6", "bds_red_10", "bds_red_11",
            "igds_primary_background", "igds_secondary_background", "igds_elevated_background",
            "igds_elevated_highlight_background", "igds_elevated_separator",
            "igds_primary_text", "igds_primary_text_disabled", "igds_secondary_text",
            "igds_primary_button", "igds_primary_icon", "igds_secondary_icon",
            "igds_separator", "igds_stroke", "igds_link", "igds_error_or_destructive", "igds_success",
            "igds_photo_border", "igds_photo_placeholder", "igds_selected_text_background",
            "igds_tag_or_toast_background", "igds_context_menu_background_color",
            "igds_context_menu_item_background_color", "igds_creation_menu_background",
            "igds_creation_button_destructive", "igds_pill_active_text",
            "igds_secondary_button_elevated_pressed_panavision",
            "igds_secondary_media_button_onblack_panavision_updated",
            "igds_prism_black",
            "igds_prism_gray_00", "igds_prism_gray_01", "igds_prism_gray_02", "igds_prism_gray_03",
            "igds_prism_gray_04", "igds_prism_gray_0400", "igds_prism_gray_05", "igds_prism_gray_0500",
            "igds_prism_gray_06", "igds_prism_gray_06_ax", "igds_prism_gray_07", "igds_prism_gray_0700",
            "igds_prism_gray_08", "igds_prism_gray_0800", "igds_prism_gray_09", "igds_prism_gray_0900",
            "igds_prism_gray_10", "igds_prism_gray_1000", "igds_prism_gray_11", "igds_prism_gray_1100",
            "igds_prism_gray_13", "igds_prism_gray_14", "igds_prism_gray_1500", "igds_prism_gray_1550",
            "igds_prism_blue_00", "igds_prism_blue_0000", "igds_prism_blue_01", "igds_prism_blue_0100",
            "igds_prism_blue_02", "igds_prism_blue_03", "igds_prism_blue_04", "igds_prism_blue_0400",
            "igds_prism_blue_05", "igds_prism_blue_06", "igds_prism_blue_07", "igds_prism_blue_0700",
            "igds_prism_blue_08", "igds_prism_blue_0800", "igds_prism_blue_1100", "igds_prism_blue_1200",
            "igds_prism_blue_1300",
            "igds_prism_indigo_000", "igds_prism_indigo_01", "igds_prism_indigo_0100", "igds_prism_indigo_02",
            "igds_prism_indigo_0300", "igds_prism_indigo_050", "igds_prism_indigo_06", "igds_prism_indigo_0600",
            "igds_prism_indigo_07", "igds_prism_indigo_0700", "igds_prism_indigo_08", "igds_prism_indigo_0800",
            "igds_prism_indigo_0900", "igds_prism_indigo_1000", "igds_prism_indigo_1100", "igds_prism_indigo_1200",
            "igds_prism_indigo_400", "igds_prism_indigo_borderless_button_link",
            "igds_prism_primary_borderless_button_indigo", "igds_prism_primary_button_background_indigo",
            "igds_prism_primary_button_label_indigo", "igds_prism_v2_indigo_borderless_button_link",
            "igds_prism_v2_primary_borderless_button_indigo",
            "igds_prism_green_05", "igds_prism_green_06", "igds_prism_green_07",
            "igds_prism_red_05", "igds_prism_red_06", "igds_prism_red_1000",
            "igds_prism_tooltip_dark_bg",
            "igds_prism_secondary_button_background_filled", "igds_prism_secondary_button_label_A",
            "igds_prism_secondary_borderless_button_label_A"
    };

    private IgThemeEngine() {}

    public static boolean isActive() {
        return FeatureFlags.customThemeEnabled && !IgColorRemapEngine.isBypassing();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static IgThemePalette getActivePalette() {
        if (activePalette == null) {
            synchronized (IgThemeEngine.class) {
                if (activePalette == null) activePalette = resolvePalette();
            }
        }
        return activePalette;
    }

    public static void invalidate() {
        activePalette = null;
        initialized = false;
        attrToSlot = null;
        colorResToSlot = null;
        IgColorRemapEngine.invalidate();
    }

    public static SparseIntArray getColorResToSlot() {
        return colorResToSlot;
    }

    public static IgThemePalette resolvePalette() {
        return ThemeSettingsHelper.resolveEffectivePalette();
    }

    public static void ensureInitialized(Context context) {
        if (context == null) return;
        ensureInitialized(context.getResources(), context.getClassLoader());
    }

    public static void ensureInitialized(Resources res) {
        if (res == null) return;
        ensureInitialized(res, hostClassLoader);
    }

    public static void ensureInitialized(Resources res, ClassLoader cl) {
        if (initialized) return;
        synchronized (IgThemeEngine.class) {
            if (initialized) return;
            if (cl != null) hostClassLoader = cl;
            String pkg = res.getResourcePackageName(android.R.color.black);
            initMappings(res, pkg, cl);
            initialized = true;
            ModuleLog.line("(InstaEclipse | Theme): mapped " + attrToSlot.size() + " attrs, " + colorResToSlot.size() + " colors");
        }
    }

    public static Integer colorForAttr(int attrId) {
        if (!FeatureFlags.customThemeEnabled || attrId == 0) return null;
        SparseIntArray map = attrToSlot;
        if (map == null) return null;
        int slotIndex = map.get(attrId, -1);
        if (slotIndex < 0) return null;
        return getActivePalette().get(IgThemePalette.SLOT_KEYS[slotIndex]);
    }

    public static Integer colorForResource(int resId) {
        if (!FeatureFlags.customThemeEnabled || resId == 0) return null;
        SparseIntArray map = colorResToSlot;
        if (map == null) return null;
        int slotIndex = map.get(resId, -1);
        if (slotIndex < 0) return null;
        return getActivePalette().get(IgThemePalette.SLOT_KEYS[slotIndex]);
    }

    public static boolean looksLikeResourceId(int value) {
        if (value == 0) return false;
        int pkg = value >>> 24;
        return pkg == 127 || pkg == 1;
    }

    public static boolean looksLikeDirectColor(int value) {
        if (value == 0 || looksLikeResourceId(value)) return false;
        int alpha = value >>> 24;
        return alpha == 255 || alpha == 0 || alpha < 127;
    }

    public static void applyAttrOverride(int attrId, TypedValue out) {
        Integer override = colorForAttr(attrId);
        if (override == null || out == null) return;
        out.type = TypedValue.TYPE_INT_COLOR_ARGB8;
        out.data = override;
        out.resourceId = 0;
        out.assetCookie = 0;
        out.string = null;
    }

    private static void initMappings(Resources res, String pkg, ClassLoader cl) {
        SparseIntArray attrs = new SparseIntArray();
        SparseIntArray colors = new SparseIntArray();
        mapCoreAttrs(attrs, res, pkg);
        scanAttrClasses(attrs, cl);
        scanColorClasses(colors, cl);
        mapCoreColors(colors, res, pkg);
        attrToSlot = attrs;
        colorResToSlot = colors;
    }

    private static void mapCoreAttrs(SparseIntArray map, Resources res, String pkg) {
        String[] names = {
                "igds_color_primary_background", "igds_color_media_background", "igds_color_clips_tab_bar_background",
                "igds_color_elevated_background", "igds_color_elevated_background_dark", "igds_color_elevated_background_intent_card",
                "igds_color_elevated_background_prompt_suggestion", "igds_color_highlight_background", "igds_color_highlight_media_background",
                "igds_color_elevated_highlight_background", "igds_color_elevated_highlight_background_night", "igds_color_secondary_background",
                "igds_color_secondary_background_on_media", "igds_color_secondary_background_strong", "igds_color_tertiary_background",
                "igds_color_banner_background", "igds_color_banner_stroke_background", "igds_color_cta_banner_background",
                "igds_color_notification_background", "igds_color_toast_background", "igds_color_toast_95_alpha_background",
                "igds_color_tag_or_toast_background", "igds_color_error_background", "igds_color_media_thumbnail_tray_background",
                "igds_color_stories_loading_background", "igds_color_reels_end_scene_background", "igds_color_prism_card_background",
                "igds_color_meta_ai_card_background", "igds_color_sticker_background", "igds_color_sticker_subtle_background",
                "igds_color_stamp_background", "igds_color_reels_afi_button_dark_background", "actionBarBackgroundColor",
                "tabBarBackgroundColor", "modalActionBarBackground", "directThreadActionBarBackgroundColor",
                "statusBarBackgroundColor", "sc_card_background_flat", "fbpay_background_color",
                "permissionBannerBackground", "igdsPrimaryBackground", "status_bar_background",
                "android:colorBackground", "android:windowBackground", "android:navigationBarColor",
                "callout_background", "creationTertiaryBackground", "igds_color_form_field_background_default_color",
                "igds_color_form_field_background_disabled_color", "igds_color_form_field_background_focussed_color", "igds_color_generic_xma_background_color",
                "igds_composer_background", "igds_search_bar_background", "igds_nav3_background",
                "igds_bottom_sheet_background", "igds_modal_background", "igds_input_background",
                "igds_comment_composer_background", "igds_direct_inbox_background", "igds_profile_background",
                "colorPrimary", "colorPrimaryDark", "colorSurface",
                "android:colorPrimary", "android:colorPrimaryDark", "android:colorAccent",
                "android:statusBarColor", "android:colorControlActivated", "android:colorControlNormal",
                "android:textColorLink", "igds_notes_background", "igds_color_bottom_sheet_background",
                "igds_color_modal_background", "igds_color_search_background", "igds_color_composer_background",
                "igds_color_input_background", "igds_color_direct_background", "igds_color_comment_composer_background",
                "igds_color_notes_background", "igds_color_nav3_background", "igds_color_pill_background",
                "igds_color_pill_background_pressed", "igds_color_pill_active_background", "igds_color_pill_active_background_pressed",
                "igds_color_pill_active_text", "igds_color_pill_active_text_pressed", "igds_color_selected_pill_text",
                "igds_color_prism_pill_active_background", "igds_color_prism_pill_active_text", "igds_color_prism_chip_background",
                "igds_color_prism_chip_background_pressed", "igds_color_prism_chip_background_selected", "igds_color_prism_chip_background_stroke",
                "igds_color_prism_chip_label_disabled", "igds_color_bio_pill_active_background", "igds_color_bio_pill_active_text",
                "igds_color_bio_pill_text", "igds_color_instream_pill_background", "igds_color_carrera_selected_pill_bg",
                "igds_color_carrera_selected_pill_bg_pressed", "igds_color_carrera_selected_pill_text", "igds_color_status_pill",
                "igds_color_status_pill_ripple", "igds_color_clips_reply_bar_pill", "igds_color_attribution_pill_background_fill",
                "igds_color_attribution_pill_background_stroke", "igds_color_icon_badge", "igds_color_new_badge",
                "igds_color_list_badge", "igds_color_thumbnail_badge_background", "igds_color_active_badge",
                "igds_color_active_badge_step_1", "igds_color_active_badge_step_2", "igds_color_active_badge_step_3",
                "igds_color_active_badge_step_4", "igds_color_active_badge_step_5", "igds_color_active_badge_step_6",
                "igds_color_reaction_background", "igds_color_reaction_selected_background", "igds_color_close_friends",
                "igds_color_primary_text", "igds_color_primary_text_on_media", "igds_color_primary_text_pressed",
                "igds_color_primary_text_disabled", "igds_color_primary_text_pill_redesign", "igds_color_primary_text_story_pill",
                "igds_color_primary_text_story_pill_redesign", "igds_color_secondary_text", "igds_color_secondary_text_on_media",
                "igds_color_secondary_selectable_text", "igds_color_text_on_color", "igds_color_text_on_white",
                "igds_color_selected_text_background", "igds_color_temporary_highlight", "igds_color_reply_bar_hint_text",
                "igdsPrimaryText", "glyphColorPrimary", "glyphColorSecondaryActive",
                "fbpay_primary_text_color", "tabSelectedTextColor", "android:textColorPrimary",
                "snackbar_text_color", "android:textColorSecondary", "reportSubtitleTextColor",
                "igds_color_floating_cta_text", "igds_color_clips_up_next_banner_text", "igds_color_primary_button",
                "igds_color_primary_button_on_media", "igds_color_primary_button_pressed", "igds_color_primary_button_icon",
                "igds_color_primary_button_indigo", "igds_color_secondary_button_on_media", "igds_color_secondary_button_background_strong",
                "igds_color_secondary_button_elevated_panavision", "igds_color_secondary_button_elevated_pressed_panavision", "igds_color_secondary_button_selected_panavision",
                "igds_color_data_visualization_primary", "igds_color_data_visualization_secondary", "igds_color_gradient_blue",
                "colorControlActivated", "igds_color_creation_tools_blue", "fbpay_focus_color",
                "igds_color_success", "igds_color_stories_progress_bar", "igds_color_feed_seekbar_knob_inner_circle",
                "igds_color_feed_seekbar_knob_outer_circle_active", "igds_color_primary_icon", "igds_color_secondary_icon",
                "igds_color_primary_icon_pill_redesign", "igds_color_primary_icon_story_pill", "igds_color_primary_icon_story_pill_redesign",
                "igds_color_icon_on_color", "igds_color_icon_on_media", "igds_color_icon_on_white",
                "igds_color_actionbar_drawable_primary", "igds_color_actionbar_drawable_secondary", "igds_color_clips_tab_bar_icon",
                "colorControlNormal", "igds_color_form_field_list_icon_color", "igds_color_divider",
                "igds_color_elevated_separator", "igds_color_separator", "igds_color_separator_or_stroke_on_media",
                "igds_color_search_typeahead_separator", "igds_color_reels_tab_bar_separator", "igds_color_clips_cta_separator",
                "igds_color_quick_send_divider_background", "igds_color_border_secondary", "igds_color_border_secondary_background",
                "igds_color_border_tertiary", "igds_color_stroke", "igds_color_photo_border",
                "igds_color_inbox_filter_chip_outline", "igds_color_drawer_status_bar_background", "igds_color_transparent_navigation_bar",
                "igds_color_text_link", "igds_color_link", "igds_color_link_on_color",
                "igds_color_link_on_media", "igds_color_link_on_white", "igds_color_action_cell_emphasized_text",
                "fbpay_link_text_color", "igds_color_error_or_destructive", "nav3_dark_active_tab_bar_icon",
                "nav3_inactive_tab_bar_icon", "igds_color_prism_indigo_accent"
        };
        for (String name : names) mapAttrByName(map, res, pkg, name);
    }

    private static void mapCoreColors(SparseIntArray map, Resources res, String pkg) {
        String[] packages = {pkg, CommonUtils.IG_PACKAGE_NAME};
        for (String p : packages) {
            if (p == null || p.isEmpty()) continue;
            for (String name : CORE_COLOR_NAMES) {
                mapColorByName(map, res, p, name);
            }
        }
    }

    private static void scanAttrClasses(SparseIntArray map, ClassLoader cl) {
        if (cl == null) return;
        String[] candidates = {"com.instagram.android.R$attr", "com.instagram.barcelona.R$attr"};
        for (String className : candidates) scanFields(map, cl, className, true);
    }

    private static void scanColorClasses(SparseIntArray map, ClassLoader cl) {
        if (cl == null) return;
        String[] candidates = {"com.instagram.android.R$color", "com.instagram.barcelona.R$color"};
        for (String className : candidates) scanFields(map, cl, className, false);
    }

    private static void scanFields(SparseIntArray map, ClassLoader cl, String className, boolean attrs) {
        try {
            Class<?> cls = cl.loadClass(className);
            for (Field field : cls.getDeclaredFields()) {
                if ((field.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0 && field.getType() == int.class) {
                    String name = field.getName();
                    int slot = attrs ? slotForAttrName(name) : slotForColorName(name);
                    if (slot >= 0) {
                        try { map.put(field.getInt(null), slot); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    static int slotForAttrName(String name) {
        if (name == null) return -1;
        name = name.toLowerCase(Locale.US);
        if (name.contains("dimmer") || name.contains("overlay") || name.contains("shadow") || name.contains("shimmer")
                || name.contains("legibility") || name.contains("sticker_text_vibrant")
                || name.contains("internal_") || name.contains("whatsapp") || name.contains("messenger_")
                || name.contains("facebook_blue") || name.contains("discord_blurple") || name.contains("line_green")
                || name.contains("kakaotalk") || name.contains("snapchat") || name.contains("sms_blue")
                || name.contains("live_external_link") || name.contains("band_green")
                || name.contains("on_media") || name.contains("on_color") || name.contains("story_ring")
                || name.contains("gradient_red") || name.contains("gradient_orange") || name.contains("gradient_pink")
                || name.contains("gradient_purple") || name.contains("gradient_yellow")) return -1;
        if (name.contains("primary_background") || name.contains("media_background") || name.contains("tab_bar_background")) return 0;
        if ((name.contains("clips_tab") && name.contains(IgThemePalette.SLOT_BACKGROUND)) || name.equals("status_bar_background")
                || name.contains("cta_banner") || name.equals("igdsprimarybackground") || name.contains("actionbarbackground")
                || name.contains("tabbarbackground") || name.contains("colorbackground") || name.contains("windowbackground")
                || name.contains("nav3_background") || name.equals("colorprimarydark")) return 0;
        if (name.contains("composer") && name.contains(IgThemePalette.SLOT_BACKGROUND)) return 1;
        if (name.contains("search") && name.contains(IgThemePalette.SLOT_BACKGROUND)) return 1;
        if (name.contains("bottom_sheet") || name.contains("bottomsheet") || name.contains("modal_background")
                || name.contains("input_background") || name.contains("notes_background")
                || name.contains("inbox_background") || name.contains("direct_background")) return 1;
        if (name.contains("elevated") || name.contains("highlight_background") || name.contains("secondary_background")
                || name.contains("callout_background") || name.contains("form_field_background") || name.contains("banner_background")
                || name.contains("pill_background") || name.contains("chip_background") || name.contains("toast")
                || name.contains("stamp_background") || name.contains("sticker_background") || name.contains("card_background")
                || name.contains("notification_background") || name.contains("status_pill") || name.contains("reaction_background")
                || name.contains("prism_card") || name.contains("colorsurface")
                || (name.contains("creation") && name.contains(IgThemePalette.SLOT_BACKGROUND))) return 1;
        if (name.contains("disabled") && (name.contains("text") || name.contains("label"))) return 3;
        if (name.contains("secondary_text") || name.contains("textcolorsecondary") || name.contains("selectable_text")
                || name.contains("hint_text")) return 3;
        if (name.contains("text_on_white")) return 2;
        if (name.contains("primary_text") || name.equals("igdsprimarytext") || name.contains("textcolorprimary")
                || name.contains("tabselectedtext") || name.contains("snackbar_text")
                || name.contains("pill_active_text") || name.contains("selected_pill_text")) return 2;
        if (name.endsWith("_text") && !name.contains("action_cell")) return 2;
        if (name.contains("glyphcolor")) return 7;
        if (name.contains("primary_button_icon")) return 6;
        if (name.contains("primary_button") || name.contains("gradient_blue") || name.contains("colorcontrolactivated")
                || name.contains("data_visualization_primary") || name.contains("fbpay_focus") || name.contains("creation_tools_blue")
                || name.contains("prism_indigo") || name.equals("coloraccent") || name.equals("colorprimary")) return 5;
        if (name.contains(IgThemePalette.SLOT_ACCENT) || name.contains("cta")
                || name.contains("selected_text") || name.contains("active_badge") || name.contains("success")
                || name.contains("close_friends") || name.contains("progress_bar") || name.contains("seekbar")) return 4;
        if (name.contains("primary_icon") || name.contains("secondary_icon") || name.contains("actionbar_drawable")
                || name.contains("clips_tab_bar_icon") || name.contains("colorcontrolnormal") || name.contains("tab_bar_icon")
                || name.contains("icon_on") || name.contains("list_icon")) return 6;
        if (name.contains("nav3_") && name.contains(IgThemePalette.SLOT_ICON)) return 6;
        if (name.contains(IgThemePalette.SLOT_DIVIDER) || name.contains("separator")) return 8;
        if (name.contains(IgThemePalette.SLOT_BORDER) || name.contains("outline")) return 9;
        if (name.contains("stroke") && !name.contains(IgThemePalette.SLOT_DESTRUCTIVE)) return 9;
        if (name.contains("statusbarcolor") || name.contains("status_bar")) return 10;
        if (name.contains("navigationbar") || name.contains("nav3_")) return 11;
        if (name.contains("text_link") || name.contains("link_text") || name.contains("link_on") || name.equals("igds_color_link")
                || name.contains("action_cell_emphasized") || name.contains("textcolorlink")) return 12;
        if (name.contains(IgThemePalette.SLOT_DESTRUCTIVE)) return 14;
        if (name.contains("badge") && name.contains(IgThemePalette.SLOT_ICON)) return 14;
        if (name.equals("igds_color_new_badge") || name.contains("list_badge") || name.contains("thumbnail_badge")) return 14;
        if (name.contains("error") || name.contains("icon_badge")) return 13;
        return name.startsWith("igds_color_") || name.startsWith("igds_") ? 1 : -1;
    }

    static int slotForColorName(String name) {
        if (name == null) return -1;
        name = name.toLowerCase(Locale.US);
        if (name.contains("dimmer") || name.contains("overlay") || name.contains("shadow") || name.contains("shimmer")
                || name.contains("legibility") || name.contains("sticker_text_vibrant")
                || name.contains("_transparent") || name.contains("_alpha_")
                || name.contains("internal_") || name.contains("whatsapp") || name.contains("messenger_")
                || name.contains("facebook_blue") || name.contains("discord_blurple") || name.contains("line_green")
                || name.contains("kakaotalk") || name.contains("snapchat") || name.equals("igds_sms_blue")
                || name.contains("live_external_link") || name.contains("band_green")
                || name.contains("on_media") || name.contains("on_color") || name.contains("story_ring")
                || name.contains("gradient_red") || name.contains("gradient_orange") || name.contains("gradient_pink")
                || name.contains("gradient_purple") || name.contains("gradient_yellow")) return -1;
        int prismTone = prismGrayTone(name);
        if (prismTone >= 0) {
            if (prismTone <= 400) return 2;
            if (prismTone <= 900) return 3;
            if (prismTone <= 1300) return 1;
            return 0;
        }
        if (name.contains("primary_background") || name.equals("igds_primary_background")
                || name.contains("nav3_background")) return 0;
        if (name.contains("disabled")) return 3;
        if (name.contains("primary_text") || name.contains("text_on_white")
                || name.contains("pill_active_text")) return 2;
        if (name.contains("secondary_text") || name.contains("text_subtitle") || name.contains("selectable_text")) return 3;
        if (name.contains(IgThemePalette.SLOT_GLYPH)) return 7;
        if (name.contains("primary_icon") || name.contains("secondary_icon") || name.contains("icon_on")) return 6;
        if (name.contains(IgThemePalette.SLOT_DESTRUCTIVE) || (name.contains("badge") && name.contains(IgThemePalette.SLOT_ICON))) return 14;
        if (name.contains("error")) return 13;
        if (name.contains(IgThemePalette.SLOT_LINK) || name.contains("link_on") || name.contains("textcolorlink")) return 12;
        if (name.contains("primary_button") || name.contains("bds_blue") || name.equals("emphasized_action_color")
                || name.equals("badge_color") || name.equals("coloraccent") || name.equals("colorprimary")) return 5;
        if (name.contains("indigo") || name.contains("blue") || name.contains("emphasized") || name.contains("gradient")
                || name.contains(IgThemePalette.SLOT_ACCENT) || name.contains("cta") || name.contains("selected_text")
                || name.contains("success") || name.contains("close_friends") || name.contains("undo_redo")) return 4;
        if (name.contains(IgThemePalette.SLOT_DIVIDER) || name.contains("separator")) return 8;
        if (name.contains(IgThemePalette.SLOT_BORDER) || name.contains("stroke")) return 9;
        if (name.contains("status_bar")) return 10;
        if (name.contains("navigation") || name.contains("nav3_")) return 11;
        if (name.contains("black") || name.equals("igds_prism_black") || name.contains("grey_9") || name.contains("gray_10")
                || name.contains("grey_10") || name.contains("gray_9") || name.contains("media_background")
                || name.contains("true_black")) return 0;
        if (name.contains("composer") || name.contains("bottom_sheet") || name.contains("bottomsheet")
                || name.contains("modal_background") || name.contains("input_background")
                || name.contains("search_bar") || name.contains("notes_background")
                || name.contains("inbox_background")) return 1;
        if (name.contains("grey_8") || name.contains("gray_8") || name.contains("gray_08") || name.contains("grey_7") || name.contains("elevated")
                || name.contains("highlight") || name.contains(IgThemePalette.SLOT_SURFACE)) return 1;
        if (name.contains("grey_0") || name.contains("gray_0") || name.contains("gray_00") || name.contains("white")) return 2;
        if (name.contains("grey_1") || name.contains("secondary") || name.contains("grey_2") || name.contains("grey_3")
                || name.contains("grey_4") || name.contains("grey_6") || name.contains("gray_1") || name.contains("gray_2")
                || name.contains("gray_3") || name.contains("gray_4") || name.contains("gray_5") || name.contains("gray_6")
                || name.contains("gray_7")) return 3;
        if (name.contains(IgThemePalette.SLOT_ICON)) return 6;
        if (name.contains("red") && (name.contains("5") || name.contains("6") || name.contains(IgThemePalette.SLOT_DESTRUCTIVE))) return 14;
        return (name.startsWith("bds_") || name.startsWith("igds_prism_") || name.startsWith("igds_")) ? 1 : -1;
    }

    private static int prismGrayTone(String name) {
        Matcher four = PRISM_TONE.matcher(name);
        if (!four.find()) return -1;
        try {
            return Integer.parseInt(four.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void mapAttrByName(SparseIntArray map, Resources res, String pkg, String name) {
        String attrName = name.startsWith("android:") ? name.substring(8) : name;
        String attrPkg = name.startsWith("android:") ? "android" : pkg;
        int id = res.getIdentifier(attrName, "attr", attrPkg);
        if (id != 0) {
            int slot = slotForAttrName(attrName);
            if (slot >= 0) map.put(id, slot);
        }
    }

    private static void mapColorByName(SparseIntArray map, Resources res, String pkg, String name) {
        int id = res.getIdentifier(name, "color", pkg);
        if (id != 0) {
            int slot = slotForColorName(name);
            if (slot >= 0) map.put(id, slot);
        }
    }
}
