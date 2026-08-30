package ps.reso.instaeclipse.ui.theme;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.R;

public class ColorPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_COLOR = "color";
    private static final String CACHE_NAME = "instaeclipse_cache";
    private static final String KEY_RECENT_COLORS = "colorPickerRecentColors";
    private static final int MAX_RECENT_COLORS = 8;

    public interface Listener {
        void onColorPicked(int color);
    }

    private ColorWheelView wheelView;
    private GradientSlider brightnessSlider;
    private GradientSlider opacitySlider;
    private View previewSwatch;
    private EditText hexInput;
    private LinearLayout recentContainer;
    private boolean updating;
    private float value = 1.0f;

    public static ColorPickerBottomSheet newInstance(String title, int color) {
        ColorPickerBottomSheet sheet = new ColorPickerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putInt(ARG_COLOR, color);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_color_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int initialColor = requireArguments().getInt(ARG_COLOR, Color.WHITE);
        String title = requireArguments().getString(ARG_TITLE, getString(R.string.theme_pick_color));

        ((TextView) view.findViewById(R.id.picker_title)).setText(title);
        ((TextView) view.findViewById(R.id.picker_brightness_label)).setText(R.string.theme_brightness);
        ((TextView) view.findViewById(R.id.picker_opacity_label)).setText(R.string.theme_opacity);

        wheelView = view.findViewById(R.id.picker_wheel);
        brightnessSlider = view.findViewById(R.id.picker_brightness_slider);
        opacitySlider = view.findViewById(R.id.picker_opacity_slider);
        previewSwatch = view.findViewById(R.id.picker_preview_swatch);
        hexInput = view.findViewById(R.id.picker_hex_input);
        recentContainer = view.findViewById(R.id.picker_recent_container);

        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});

        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);
        value = hsv[2];
        wheelView.setHueSaturation(hsv[0], hsv[1]);
        syncUiFromColor(initialColor);

        wheelView.setOnHueSaturationChangeListener((h, s) -> syncUiFromColor(currentColor()));
        brightnessSlider.setOnProgressChangeListener(p -> {
            value = p;
            syncUiFromColor(currentColor());
        });
        opacitySlider.setOnProgressChangeListener(p -> syncUiFromColor(currentColor()));
        hexInput.addTextChangedListener(hexWatcher());

        buildRecentColorSwatches();

        view.findViewById(R.id.picker_cancel_button).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.picker_apply_button).setOnClickListener(v -> {
            int color = currentColor();
            addRecentColor(color);
            Listener l = getListener();
            if (l != null) l.onColorPicked(color);
            dismiss();
        });
    }

    private int currentColor() {
        int rgb = Color.HSVToColor(new float[]{wheelView.getHue(), wheelView.getSaturation(), value});
        int alpha = Math.round(opacitySlider.getProgress() * 255.0f);
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private void syncUiFromColor(int color) {
        int opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        brightnessSlider.setColors(Color.BLACK, Color.HSVToColor(new float[]{wheelView.getHue(), wheelView.getSaturation(), 1.0f}));
        brightnessSlider.setProgress(value);
        opacitySlider.setColors(Color.argb(0, Color.red(opaque), Color.green(opaque), Color.blue(opaque)), Color.argb(255, Color.red(opaque), Color.green(opaque), Color.blue(opaque)));
        opacitySlider.setProgress(Color.alpha(color) / 255.0f);
        previewSwatch.setBackgroundColor(color);
        if (!updating) {
            updating = true;
            hexInput.setText(String.format("#%08X", color));
            hexInput.setSelection(hexInput.getText().length());
            updating = false;
        }
    }

    private TextWatcher hexWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (updating) return;
                String raw = s.toString().trim();
                if (raw.startsWith("#")) raw = raw.substring(1);
                if (raw.length() != 6 && raw.length() != 8) return;
                int color;
                try {
                    color = raw.length() == 8 ? (int) Long.parseLong(raw, 16) : Color.parseColor("#FF" + raw);
                } catch (NumberFormatException ignored) {
                    return;
                }
                updating = true;
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                value = hsv[2];
                wheelView.setHueSaturation(hsv[0], hsv[1]);
                syncUiFromColor(color);
                updating = false;
            }
        };
    }

    private void buildRecentColorSwatches() {
        recentContainer.removeAllViews();
        List<Integer> recents = loadRecentColors();
        int size = (int) (32 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        for (int color : recents) {
            View swatch = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(margin);
            swatch.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke((int) getResources().getDisplayMetrics().density, 0x33FFFFFF);
            swatch.setBackground(bg);
            swatch.setOnClickListener(v -> {
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                value = hsv[2];
                wheelView.setHueSaturation(hsv[0], hsv[1]);
                syncUiFromColor(color);
            });
            recentContainer.addView(swatch);
        }
    }

    private List<Integer> loadRecentColors() {
        List<Integer> result = new ArrayList<>();
        String raw = prefs().getString(KEY_RECENT_COLORS, "");
        if (raw.isEmpty()) return result;
        for (String part : raw.split(",")) {
            try {
                result.add((int) Long.parseLong(part, 16));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private void addRecentColor(int color) {
        List<Integer> recents = loadRecentColors();
        recents.remove((Integer) color);
        recents.add(0, color);
        while (recents.size() > MAX_RECENT_COLORS) recents.remove(recents.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int c : recents) {
            if (sb.length() > 0) sb.append(',');
            sb.append(String.format("%08X", c));
        }
        prefs().edit().putString(KEY_RECENT_COLORS, sb.toString()).apply();
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
    }

    private Listener getListener() {
        if (getParentFragment() instanceof Listener) return (Listener) getParentFragment();
        if (requireActivity() instanceof Listener) return (Listener) requireActivity();
        return null;
    }
}
