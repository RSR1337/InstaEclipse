package ps.reso.instaeclipse.ui.theme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.theme.CustomThemeStore;
import ps.reso.instaeclipse.mods.ui.theme.IgThemePalette;
import ps.reso.instaeclipse.mods.ui.theme.ThemePreset;
import ps.reso.instaeclipse.mods.ui.theme.ThemePresetCategory;
import ps.reso.instaeclipse.mods.ui.theme.ThemePresets;
import ps.reso.instaeclipse.mods.ui.theme.ThemeSettingsHelper;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.providers.SettingsProvider;

public class ThemeCustomizerActivity extends AppCompatActivity implements ColorPickerBottomSheet.Listener {

    private static final String CACHE_NAME = "instaeclipse_cache";
    private static final String KEY_ENABLED = "customThemeEnabled";
    private static final String KEY_PRESET_ID = "themePresetId";
    private static final String KEY_PALETTE_JSON = "themePaletteJson";
    private static final String KEY_CUSTOM_PRESETS = "themeCustomPresetsJson";

    private static final int[] SLOT_LABELS = {
            R.string.theme_slot_background, R.string.theme_slot_surface, R.string.theme_slot_primary_text,
            R.string.theme_slot_secondary_text, R.string.theme_slot_accent, R.string.theme_slot_button,
            R.string.theme_slot_icon, R.string.theme_slot_glyph, R.string.theme_slot_divider,
            R.string.theme_slot_border, R.string.theme_slot_status_bar, R.string.theme_slot_navigation,
            R.string.theme_slot_link, R.string.theme_slot_error, R.string.theme_slot_destructive
    };
    private static final String STATE_PRESETS_EXPANDED = "presets_expanded";
    private static final String STATE_CUSTOM_EXPANDED = "custom_expanded";
    private static final String STATE_CATEGORY = "preset_category";

    private MaterialSwitch enableSwitch;
    private View presetsContent;
    private View customContent;
    private View myPresetsEmpty;
    private ImageView presetsExpandIcon;
    private ImageView customExpandIcon;
    private ChipGroup categoryGroup;
    private PresetAdapter presetAdapter;
    private ColorSlotAdapter slotAdapter;
    private IgThemePalette workingPalette;
    private String pendingSlotKey;
    private boolean customMode;
    private boolean presetsExpanded = true;
    private boolean customExpanded = true;
    private int selectedPresetId = 1;
    private ThemePresetCategory selectedCategory = ThemePresetCategory.DARK;
    private List<ThemePreset> myPresets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_customizer);

        MaterialToolbar toolbar = findViewById(R.id.theme_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        enableSwitch = findViewById(R.id.theme_enable_switch);
        presetsContent = findViewById(R.id.theme_presets_content);
        customContent = findViewById(R.id.theme_custom_content);
        myPresetsEmpty = findViewById(R.id.theme_my_presets_empty);
        presetsExpandIcon = findViewById(R.id.theme_presets_expand_icon);
        customExpandIcon = findViewById(R.id.theme_custom_expand_icon);
        categoryGroup = findViewById(R.id.theme_preset_categories);
        RecyclerView presetList = findViewById(R.id.theme_preset_list);
        RecyclerView colorSlots = findViewById(R.id.theme_color_slots);
        MaterialButton resetButton = findViewById(R.id.theme_reset_custom);
        MaterialButton saveButton = findViewById(R.id.theme_save_preset);

        if (savedInstanceState != null) {
            presetsExpanded = savedInstanceState.getBoolean(STATE_PRESETS_EXPANDED, true);
            customExpanded = savedInstanceState.getBoolean(STATE_CUSTOM_EXPANDED, true);
            String cat = savedInstanceState.getString(STATE_CATEGORY);
            if (cat != null) {
                try {
                    selectedCategory = ThemePresetCategory.valueOf(cat);
                } catch (Throwable ignored) {}
            }
        }
        setupCollapsibleSection(findViewById(R.id.theme_presets_header), presetsContent, presetsExpandIcon, presetsExpanded);
        setupCollapsibleSection(findViewById(R.id.theme_custom_header), customContent, customExpandIcon, customExpanded);

        reloadPaletteState();
        boolean enabled = cache().getBoolean(KEY_ENABLED, false);
        enableSwitch.setChecked(enabled);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> persist(true));

        presetAdapter = new PresetAdapter();
        presetList.setLayoutManager(new GridLayoutManager(this, 2));
        presetList.setAdapter(presetAdapter);

        slotAdapter = new ColorSlotAdapter();
        colorSlots.setLayoutManager(new LinearLayoutManager(this));
        colorSlots.setAdapter(slotAdapter);

        categoryGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) return;
            if (checkedId == R.id.theme_cat_amoled) selectedCategory = ThemePresetCategory.AMOLED;
            else if (checkedId == R.id.theme_cat_mine) selectedCategory = ThemePresetCategory.MY_PRESETS;
            else selectedCategory = ThemePresetCategory.DARK;
            refreshPresetList();
        });
        if (savedInstanceState == null) {
            selectedCategory = ThemePresets.categoryForId(selectedPresetId);
        }
        checkCategoryChip();
        refreshPresetList();

        resetButton.setOnClickListener(v -> {
            workingPalette = ThemePresets.getById(1).palette.copy();
            customMode = true;
            selectedPresetId = 0;
            refreshPresetList();
            slotAdapter.notifyDataSetChanged();
            persist(true);
        });
        saveButton.setOnClickListener(v -> promptSaveMyPreset());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadPaletteState();
        refreshPresetList();
        if (slotAdapter != null) slotAdapter.notifyDataSetChanged();
    }

    private SharedPreferences cache() {
        return getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
    }

    private String customPresetsJson() {
        return cache().getString(KEY_CUSTOM_PRESETS, "");
    }

    private void reloadPaletteState() {
        selectedPresetId = cache().getInt(KEY_PRESET_ID, 1);
        String paletteJson = cache().getString(KEY_PALETTE_JSON, "");
        myPresets = CustomThemeStore.parse(customPresetsJson());
        if (selectedPresetId > 0 && selectedPresetId < CustomThemeStore.MIN_ID) {
            ThemePreset resolved = ThemePresets.getById(selectedPresetId);
            if (resolved.id != selectedPresetId) {
                selectedPresetId = resolved.id;
            }
        }
        customMode = ThemeSettingsHelper.isCustomMode(selectedPresetId);
        workingPalette = ThemeSettingsHelper.resolveRawPalette(selectedPresetId, paletteJson, customPresetsJson());
    }

    private IgThemePalette activePalette() {
        if (!customMode && selectedPresetId >= CustomThemeStore.MIN_ID) {
            ThemePreset saved = CustomThemeStore.find(customPresetsJson(), selectedPresetId);
            if (saved != null) return saved.palette;
        }
        if (!customMode && selectedPresetId > 0) return ThemePresets.getById(selectedPresetId).palette;
        return workingPalette;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_PRESETS_EXPANDED, presetsExpanded);
        outState.putBoolean(STATE_CUSTOM_EXPANDED, customExpanded);
        outState.putString(STATE_CATEGORY, selectedCategory.name());
    }

    private void setupCollapsibleSection(View header, View content, ImageView icon, boolean expanded) {
        setSectionExpanded(content, icon, expanded);
        header.setOnClickListener(v -> {
            if (content == presetsContent) {
                presetsExpanded = !presetsExpanded;
                setSectionExpanded(content, icon, presetsExpanded);
            } else {
                customExpanded = !customExpanded;
                setSectionExpanded(content, icon, customExpanded);
            }
        });
    }

    private void setSectionExpanded(View content, ImageView icon, boolean expanded) {
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        icon.setContentDescription(getString(expanded ? R.string.theme_collapse_section : R.string.theme_expand_section));
    }

    private void checkCategoryChip() {
        int chipId = R.id.theme_cat_dark;
        if (selectedCategory == ThemePresetCategory.AMOLED) chipId = R.id.theme_cat_amoled;
        else if (selectedCategory == ThemePresetCategory.MY_PRESETS) chipId = R.id.theme_cat_mine;
        categoryGroup.check(chipId);
    }

    private void refreshPresetList() {
        List<ThemePreset> items;
        if (selectedCategory == ThemePresetCategory.MY_PRESETS) {
            items = new ArrayList<>(myPresets);
            myPresetsEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            items = ThemePresets.byCategory(selectedCategory);
            myPresetsEmpty.setVisibility(View.GONE);
        }
        presetAdapter.setItems(items);
    }

    private static String formatColorHex(int color) {
        if (Color.alpha(color) == 255) return String.format("#%06X", 0xFFFFFF & color);
        return String.format("#%08X", color);
    }

    private void persist(boolean toast) {
        boolean enabled = enableSwitch.isChecked();
        int presetId = customMode ? 0 : selectedPresetId;
        String paletteJson = workingPalette.copy().toJson();
        String customJson = CustomThemeStore.toJson(myPresets);

        SharedPreferences.Editor editor = cache().edit();
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.putInt(KEY_PRESET_ID, presetId);
        editor.putString(KEY_PALETTE_JSON, paletteJson);
        editor.putString(KEY_CUSTOM_PRESETS, customJson);
        long updatedAt = SettingsManager.stampUpdatedAt(editor);
        editor.commit();
        LsPatchCompanionBridge.makeWorldReadable(this, CACHE_NAME);
        LsPatchCompanionBridge.syncFrom(cache());

        broadcastBool(KEY_ENABLED, enabled, updatedAt);

        Intent presetIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        presetIntent.putExtra("key", KEY_PRESET_ID);
        presetIntent.putExtra("value", presetId);
        presetIntent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, presetIntent);

        Intent paletteIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        paletteIntent.putExtra("key", KEY_PALETTE_JSON);
        paletteIntent.putExtra("value", paletteJson);
        paletteIntent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, paletteIntent);

        Intent customIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        customIntent.putExtra("key", KEY_CUSTOM_PRESETS);
        customIntent.putExtra("value", customJson);
        customIntent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, customIntent);

        if (toast) Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show();
    }

    private void broadcastBool(String key, boolean value, long updatedAt) {
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        intent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        CommonUtils.broadcastToInstagram(this, intent);
    }

    private void promptSaveMyPreset() {
        EditText input = new EditText(this);
        input.setHint(R.string.theme_preset_name_hint);
        int pad = dp(20);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_save_preset)
                .setView(input)
                .setPositiveButton(R.string.theme_apply_color, (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) name = getString(R.string.theme_cat_my_presets) + " " + (myPresets.size() + 1);
                    int id = CustomThemeStore.nextId(myPresets);
                    myPresets.add(new ThemePreset(id, ThemePresetCategory.MY_PRESETS, name, workingPalette.copy()));
                    selectedPresetId = id;
                    customMode = false;
                    selectedCategory = ThemePresetCategory.MY_PRESETS;
                    enableThemeQuietly();
                    checkCategoryChip();
                    refreshPresetList();
                    slotAdapter.notifyDataSetChanged();
                    persist(false);
                    Toast.makeText(this, R.string.theme_preset_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteMyPreset(ThemePreset preset) {
        List<ThemePreset> next = new ArrayList<>();
        for (ThemePreset item : myPresets) {
            if (item.id != preset.id) next.add(item);
        }
        myPresets = next;
        if (selectedPresetId == preset.id) {
            selectedPresetId = 1;
            customMode = false;
            workingPalette = ThemePresets.getById(1).palette.copy();
        }
        persist(false);
        refreshPresetList();
        slotAdapter.notifyDataSetChanged();
    }

    private void enableThemeQuietly() {
        if (enableSwitch.isChecked()) return;
        enableSwitch.setOnCheckedChangeListener(null);
        enableSwitch.setChecked(true);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> persist(true));
    }

    @Override
    public void onColorPicked(int color) {
        if (pendingSlotKey == null) return;
        workingPalette.set(pendingSlotKey, color);
        customMode = true;
        selectedPresetId = 0;
        enableThemeQuietly();
        refreshPresetList();
        slotAdapter.notifyDataSetChanged();
        persist(true);
        pendingSlotKey = null;
    }

    private void selectPreset(ThemePreset preset) {
        selectedPresetId = preset.id;
        customMode = false;
        workingPalette = preset.palette.copy();
        enableThemeQuietly();
        refreshPresetList();
        slotAdapter.notifyDataSetChanged();
        persist(true);
    }

    private void openPicker(String slotKey, int color, String label) {
        pendingSlotKey = slotKey;
        ColorPickerBottomSheet.newInstance(label, color).show(getSupportFragmentManager(), "colorPicker");
    }

    private String slotLabel(int position) {
        if (position < 0 || position >= SLOT_LABELS.length) return "";
        return getString(SLOT_LABELS[position]);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.Holder> {
        private final List<ThemePreset> presets = new ArrayList<>();

        void setItems(List<ThemePreset> items) {
            presets.clear();
            if (items != null) presets.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_preset, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ThemePreset preset = presets.get(position);
            holder.name.setText(preset.name);
            IgThemePalette.bindCardPreview(holder.itemView.getContext(), holder.preview, preset.palette);
            boolean selected = !customMode && preset.id == selectedPresetId;
            int stroke = selected
                    ? MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary)
                    : MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOutline);
            holder.card.setStrokeColor(stroke);
            holder.card.setStrokeWidth(selected ? dp(2) : dp(1));
            holder.card.setOnClickListener(v -> selectPreset(preset));
            boolean mine = preset.category == ThemePresetCategory.MY_PRESETS;
            holder.delete.setVisibility(mine ? View.VISIBLE : View.GONE);
            holder.delete.setOnClickListener(v -> {
                if (mine) deleteMyPreset(preset);
            });
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final FrameLayout preview;
            final TextView name;
            final ImageView delete;

            Holder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.theme_preset_card);
                preview = itemView.findViewById(R.id.theme_preset_preview);
                name = itemView.findViewById(R.id.theme_preset_name);
                delete = itemView.findViewById(R.id.theme_preset_delete);
            }
        }
    }

    private class ColorSlotAdapter extends RecyclerView.Adapter<ColorSlotAdapter.Holder> {

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_color_slot, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String key = IgThemePalette.SLOT_KEYS[position];
            int color = activePalette().get(key);
            holder.label.setText(slotLabel(position));
            holder.hex.setText(formatColorHex(color));
            GradientDrawable swatch = new GradientDrawable();
            swatch.setCornerRadius(dp(10));
            swatch.setColor(color);
            holder.swatch.setBackground(swatch);
            holder.itemView.setOnClickListener(v -> {
                if (!customMode && selectedPresetId > 0) {
                    if (selectedPresetId >= CustomThemeStore.MIN_ID) {
                        ThemePreset saved = CustomThemeStore.find(customPresetsJson(), selectedPresetId);
                        if (saved != null) workingPalette = saved.palette.copy();
                    } else {
                        workingPalette = ThemePresets.getById(selectedPresetId).palette.copy();
                    }
                }
                openPicker(key, color, slotLabel(position));
            });
        }

        @Override
        public int getItemCount() {
            return IgThemePalette.SLOT_KEYS.length;
        }

        class Holder extends RecyclerView.ViewHolder {
            final View swatch;
            final TextView label;
            final TextView hex;

            Holder(View itemView) {
                super(itemView);
                swatch = itemView.findViewById(R.id.theme_slot_swatch);
                label = itemView.findViewById(R.id.theme_slot_label);
                hex = itemView.findViewById(R.id.theme_slot_hex);
            }
        }
    }
}
