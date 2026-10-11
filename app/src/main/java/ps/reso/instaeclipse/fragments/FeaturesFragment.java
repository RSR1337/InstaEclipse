package ps.reso.instaeclipse.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.VerifyHooksActivity;
import ps.reso.instaeclipse.mods.location.LocationPickerActivity;
import ps.reso.instaeclipse.ui.theme.ThemeCustomizerActivity;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.providers.SettingsProvider;
import ps.reso.instaeclipse.utils.ui.EclipseAccents;
import ps.reso.instaeclipse.utils.ui.FeatureAccents;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public class FeaturesFragment extends Fragment {

    private SharedPreferences localCache;
    private View fragmentView;
    private RecyclerView recyclerFeatures;
    private FeatureAdapter adapter;
    private TextView tvHeaderTitle;
    private TextView tvHeaderSubtitle;
    private ImageButton btnBack;
    private View pendingBar;
    private TextView pendingText;
    private ActivityResultLauncher<Uri> dirPickerLauncher;
    private ActivityResultLauncher<String[]> restoreFileLauncher;
    private ActivityResultLauncher<String> notifPermLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;
    private ActivityResultLauncher<Intent> themeCustomizerLauncher;

    private String currentMenu = "main";
    private RecyclerView.ItemDecoration tileSpacingDecoration;
    private int menuAccent = FeatureAccents.color(EclipseAccents.PRIMARY);

    private final Map<String, Boolean> stagedChanges = new HashMap<>();
    private boolean notifPermissionAsked;

    private static final List<String> DOWNLOAD_KEYS = Arrays.asList(
            "enablePostDownload", "enableStoryDownload", "enableReelDownload",
            "enableProfileDownload", "enableVoiceDownload", "enableBatchDownload");

    private final BroadcastReceiver prefsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("ps.reso.instaeclipse.ACTION_SEND_PREFS".equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    applyPrefsBundle(bundle);
                    if ("downloader".equals(currentMenu)) {
                        loadDownloaderMenu();
                    } else if ("quality".equals(currentMenu)) {
                        loadQualityMenu();
                    } else if ("location".equals(currentMenu)) {
                        loadLocationMenu();
                    } else if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            }
        }
    };

    private void applyPrefsBundle(Bundle bundle) {
        long remoteUpdatedAt = CommonUtils.readUpdatedAt(
                CommonUtils.readBundleValue(bundle, SettingsProvider.KEY_UPDATED_AT));
        long localUpdatedAt = localCache.getLong(SettingsProvider.KEY_UPDATED_AT, 0L);
        if (remoteUpdatedAt > 0L && remoteUpdatedAt < localUpdatedAt) return;

        boolean suppressPath = localCache.getBoolean("pathJustSetLocally", false);
        SharedPreferences.Editor editor = localCache.edit();
        if (suppressPath) editor.remove("pathJustSetLocally");
        for (String key : bundle.keySet()) {
            Object value = CommonUtils.readBundleValue(bundle, key);
            if (value instanceof String
                    && suppressPath
                    && ("downloaderCustomPath".equals(key) || "downloaderCustomUri".equals(key))) {
                continue;
            }
            SettingsProvider.putPref(editor, key, value);
        }
        if (remoteUpdatedAt == 0L) {
            SettingsManager.stampUpdatedAt(editor);
        }
        editor.commit();
        FeatureFlags.colorfulFeatureIcons = localCache.getBoolean("colorfulFeatureIcons", false);
        LsPatchCompanionBridge.makeWorldReadable(requireContext(), LsPatchCompanionBridge.COMPANION_CACHE);
        LsPatchCompanionBridge.syncFrom(localCache);
    }

    private long touchAndFlush(SharedPreferences.Editor editor) {
        long updatedAt = SettingsManager.stampUpdatedAt(editor);
        editor.commit();
        LsPatchCompanionBridge.makeWorldReadable(requireContext(), LsPatchCompanionBridge.COMPANION_CACHE);
        LsPatchCompanionBridge.syncFrom(localCache);
        return updatedAt;
    }

    private void sendToInstagram(Intent intent) {
        CommonUtils.broadcastToInstagram(requireContext(), intent);
    }

    private void sendBoolPref(String key, boolean value, long updatedAt) {
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        intent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        sendToInstagram(intent);
    }

    private void sendStringPref(String key, String value, long updatedAt) {
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        intent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        sendToInstagram(intent);
    }

    private void sendIntPref(String key, int value, long updatedAt) {
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        intent.putExtra(SettingsProvider.KEY_UPDATED_AT, updatedAt);
        sendToInstagram(intent);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                String json = scanner.hasNext() ? scanner.next() : "";
                JSONObject root = new JSONObject(json);
                JSONObject s = root.has("settings") ? root.getJSONObject("settings") : root;
                SharedPreferences.Editor editor = localCache.edit();
                Iterator<String> keys = s.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object val = s.get(key);
                    if (val instanceof Boolean) editor.putBoolean(key, (Boolean) val);
                    else if (val instanceof String) editor.putString(key, (String) val);
                    else if (val instanceof Integer) editor.putInt(key, (Integer) val);
                }
                long updatedAt = touchAndFlush(editor);
                keys = s.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object val = s.get(key);
                    if (val instanceof Boolean) sendBoolPref(key, (Boolean) val, updatedAt);
                    else if (val instanceof String) sendStringPref(key, (String) val, updatedAt);
                    else if (val instanceof Integer) sendIntPref(key, (Integer) val, updatedAt);
                }
                if (adapter != null) adapter.notifyDataSetChanged();
                Toast.makeText(requireContext(), getString(R.string.ig_toast_settings_restored), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {});

        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result == null || result.getData() == null) return;
                    Intent data = result.getData();
                    double lat = data.getDoubleExtra(LocationPickerActivity.RESULT_LAT, 0.0);
                    double lng = data.getDoubleExtra(LocationPickerActivity.RESULT_LNG, 0.0);
                    SharedPreferences.Editor ed = localCache.edit();
                    ed.putString("spoofLat", String.valueOf(lat));
                    ed.putString("spoofLng", String.valueOf(lng));
                    long updatedAt = touchAndFlush(ed);
                    sendStringPref("spoofLat", String.valueOf(lat), updatedAt);
                    sendStringPref("spoofLng", String.valueOf(lng), updatedAt);

                    if ("location".equals(currentMenu)) loadLocationMenu();
                });

        themeCustomizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {});

        dirPickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                requireContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);

                String uriString = uri.toString();
                String path = uri.getPath();

                if (path != null) {
                    path = path.replace("/tree/primary:", "/storage/emulated/0/");
                    path = path.replace("/document/primary:", "/storage/emulated/0/");
                } else {
                    path = uriString;
                }

                SharedPreferences.Editor editor = localCache.edit();
                editor.putBoolean("pathJustSetLocally", true);
                editor.putString("downloaderCustomUri", uriString);
                editor.putString("downloaderCustomPath", path);
                long updatedAt = touchAndFlush(editor);
                sendStringPref("downloaderCustomUri", uriString, updatedAt);
                sendStringPref("downloaderCustomPath", path, updatedAt);

                Toast.makeText(requireContext(), getString(R.string.ig_toast_download_folder_updated), Toast.LENGTH_SHORT).show();

                if ("downloader".equals(currentMenu)) {
                    loadDownloaderMenu();
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_features, container, false);
        localCache = requireContext().getSharedPreferences("instaeclipse_cache", Context.MODE_PRIVATE);

        recyclerFeatures = fragmentView.findViewById(R.id.recycler_features);
        recyclerFeatures.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerFeatures.setItemAnimator(null);
        adapter = new FeatureAdapter();
        recyclerFeatures.setAdapter(adapter);

        tvHeaderTitle = fragmentView.findViewById(R.id.tv_header_title);
        tvHeaderSubtitle = fragmentView.findViewById(R.id.tv_header_subtitle);
        btnBack = fragmentView.findViewById(R.id.btn_back);
        pendingBar = fragmentView.findViewById(R.id.pending_bar);
        pendingText = fragmentView.findViewById(R.id.pending_text);

        btnBack.setOnClickListener(v -> loadMainMenu());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!"main".equals(currentMenu)) {
                    loadMainMenu();
                    return;
                }
                Runnable exit = () -> {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                };
                if (hasPendingChanges()) {
                    resolvePendingChanges(exit);
                } else {
                    exit.run();
                }
            }
        });

        fragmentView.findViewById(R.id.pending_apply).setOnClickListener(v -> commitStagedChanges());
        fragmentView.findViewById(R.id.pending_discard).setOnClickListener(v -> discardStagedChanges());

        loadMainMenu();

        return fragmentView;
    }

    public static class FeatureItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_SWITCH = 1;
        public static final int TYPE_CLICKABLE = 2;
        public static final int TYPE_MASTER_SWITCH = 3;
        public static final int TYPE_SPACER = 4;
        public static final int TYPE_TILE = 5;

        public int type;
        public String title;
        public String subtitle;
        public boolean isNav;
        public boolean tile;
        public boolean danger;
        public boolean unstable;
        public boolean child;
        public int extremeLock;
        public boolean valueOn;
        public List<String> stateKeys;
        public String prefKey;
        public Runnable onClick;
        public int textColor;
        public int iconRes;
        public int accentColor;
        public boolean isExtreme;
        public List<String> childKeys;
        public String dependsOn;
        public String cascadeOffKey;
        public List<String> requiresAnyOf;
        public String disabledWhenTrue;

        public int segmentPosition;
        public int segmentSize;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_header);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSubtitle;
        TextView tvBadge;
        ImageView ivIcon;
        ImageView ivChevron;
        ImageView ivLock;
        View rowContent;
        MaterialSwitch swToggle;
        MaterialCardView cardView;
        ItemViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
            tvBadge = v.findViewById(R.id.tv_badge);
            ivIcon = v.findViewById(R.id.iv_icon);
            ivChevron = v.findViewById(R.id.iv_chevron);
            ivLock = v.findViewById(R.id.iv_lock);
            rowContent = v.findViewById(R.id.row_content);
            swToggle = v.findViewById(R.id.sw_toggle);
            cardView = (MaterialCardView) v;
        }
    }

    static class MasterViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSubtitle;
        ImageView ivIcon;
        MaterialSwitch swToggle;
        MaterialCardView cardView;
        MasterViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
            ivIcon = v.findViewById(R.id.iv_icon);
            swToggle = v.findViewById(R.id.sw_toggle);
            cardView = (MaterialCardView) v;
        }
    }

    static class TileViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSubtitle;
        ImageView ivIcon;
        View dot;
        TextView count;
        MaterialCardView cardView;
        TileViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
            ivIcon = v.findViewById(R.id.iv_icon);
            dot = v.findViewById(R.id.tile_dot);
            count = v.findViewById(R.id.tile_count);
            cardView = v.findViewById(R.id.card_view);
        }
    }

    private static final java.util.regex.Pattern TRAILING_PAREN =
            java.util.regex.Pattern.compile("\\s*[(（][^()（）]*[)）]\\s*$");

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private int attrColor(View anchor, int attr) {
        return MaterialColors.getColor(anchor, attr);
    }

    private static GradientDrawable roundRect(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private boolean anyOn(List<String> keys) {
        if (keys == null) return false;
        for (String k : keys) {
            if (getCurrentState(k)) return true;
        }
        return false;
    }

    private int countOn(List<String> keys) {
        int n = 0;
        for (String k : keys) {
            if (getCurrentState(k)) n++;
        }
        return n;
    }

    private boolean isExtremeLocked(FeatureItem item) {
        if (item.extremeLock == 0 || !getCurrentState("isExtremeMode")) return false;
        boolean on = getCurrentState(item.prefKey);
        return item.extremeLock == 1 ? on : !on;
    }

    private class FeatureAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<FeatureItem> items = new ArrayList<>();

        public void setItems(List<FeatureItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            int type = items.get(position).type;
            if (type == FeatureItem.TYPE_HEADER) return 0;
            if (type == FeatureItem.TYPE_SPACER) return 2;
            if (type == FeatureItem.TYPE_TILE) return 3;
            if (type == FeatureItem.TYPE_MASTER_SWITCH) return 4;
            return 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == 0) {
                return new HeaderViewHolder(inflater.inflate(R.layout.item_feature_header, parent, false));
            } else if (viewType == 2) {
                View v = new View(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(12)));
                return new RecyclerView.ViewHolder(v) {};
            } else if (viewType == 3) {
                return new TileViewHolder(inflater.inflate(R.layout.item_feature_tile, parent, false));
            } else if (viewType == 4) {
                return new MasterViewHolder(inflater.inflate(R.layout.item_feature_master, parent, false));
            } else {
                return new ItemViewHolder(inflater.inflate(R.layout.item_feature, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            FeatureItem item = items.get(position);

            if (item.type == FeatureItem.TYPE_SPACER) return;

            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).tvHeader.setText(item.title.replaceAll("\\s*[:：]\\s*$", ""));
                ViewCompat.setAccessibilityHeading(holder.itemView, true);
            } else if (holder instanceof TileViewHolder) {
                bindTile((TileViewHolder) holder, item);
            } else if (holder instanceof MasterViewHolder) {
                bindMaster((MasterViewHolder) holder, item);
            } else if (holder instanceof ItemViewHolder) {
                bindRow((ItemViewHolder) holder, item);
            }
        }

        private void bindTile(TileViewHolder h, FeatureItem item) {
            h.tvTitle.setText(item.title);
            bindChip(h.ivIcon, item.iconRes, item.accentColor, attrColor(h.itemView, com.google.android.material.R.attr.colorOnSurface), 13);

            boolean on;
            String state;
            int stateColor;
            String count = null;
            if (item.stateKeys != null) {
                int onCount = countOn(item.stateKeys);
                on = onCount > 0;
                count = onCount + "/" + item.stateKeys.size();
                state = getString(on ? R.string.feat_state_on : R.string.feat_state_off);
                stateColor = on ? color(R.color.ie_accent_text) : color(R.color.ie_text_muted);
            } else {
                on = item.valueOn;
                state = item.subtitle;
                stateColor = attrColor(h.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            }
            h.tvSubtitle.setText(state);
            h.tvSubtitle.setTextColor(stateColor);
            h.tvSubtitle.setVisibility(state != null ? View.VISIBLE : View.GONE);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(on ? color(R.color.corona_amber) : color(R.color.ie_dot_off));
            h.dot.setBackground(dot);
            h.dot.setVisibility(item.stateKeys != null || item.subtitle != null ? View.VISIBLE : View.INVISIBLE);

            h.count.setText(count);
            h.count.setTextColor(stateColor);
            h.count.setVisibility(count != null ? View.VISIBLE : View.GONE);

            String description = state != null ? item.title + ", " + state : item.title;
            if (count != null) description += ", " + count;
            h.cardView.setContentDescription(description);
            h.cardView.setOnClickListener(v -> {
                if (item.onClick != null) item.onClick.run();
            });
        }

        private void bindMaster(MasterViewHolder h, FeatureItem item) {
            int total = item.childKeys.size();
            int onCount = countOn(item.childKeys);
            boolean on = onCount > 0;
            boolean enabled = item.disabledWhenTrue == null || !getCurrentState(item.disabledWhenTrue);

            h.tvTitle.setText(item.title);
            h.tvSubtitle.setText(on
                    ? getString(R.string.feat_master_summary, onCount, total)
                    : getString(R.string.feat_master_all_off));

            int gold = color(R.color.corona_amber);
            int muted = attrColor(h.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            h.cardView.setCardBackgroundColor(color(on ? R.color.ie_accent_faint : R.color.ie_card));
            h.cardView.setStrokeColor(color(on ? R.color.ie_accent_border : R.color.ie_card_stroke));
            bindChip(h.ivIcon, item.iconRes, 0, on ? gold : muted, 16);
            h.ivIcon.setBackground(roundRect(color(on ? R.color.ie_accent_soft : R.color.ie_chip), dp(16)));

            h.itemView.setEnabled(enabled);
            h.swToggle.setEnabled(enabled);
            h.itemView.setAlpha(enabled ? 1f : 0.5f);

            h.swToggle.setOnCheckedChangeListener(null);
            h.swToggle.setChecked(on);
            h.swToggle.setContentDescription(item.title);
            h.itemView.setOnClickListener(v -> h.swToggle.toggle());
            h.swToggle.setOnCheckedChangeListener((btn, checked) -> {
                boolean target = !on;
                for (String key : item.childKeys) {
                    stageChange(key, target);
                }
                notifyDataSetChanged();
            });
        }

        private void bindRow(ItemViewHolder h, FeatureItem item) {
            boolean isSwitch = item.type == FeatureItem.TYPE_SWITCH;
            boolean checked = isSwitch && item.prefKey != null && getCurrentState(item.prefKey);

            String title = item.title;
            if (item.unstable) title = TRAILING_PAREN.matcher(title).replaceFirst("");
            h.tvTitle.setText(title);
            h.tvTitle.setTextSize(item.isNav && !isSwitch ? 15f : 14.5f);
            h.tvBadge.setVisibility(item.unstable ? View.VISIBLE : View.GONE);

            int onSurface = attrColor(h.itemView, com.google.android.material.R.attr.colorOnSurface);
            int muted = attrColor(h.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant);
            int iconColor = isSwitch ? (checked ? color(R.color.corona_amber) : muted) : onSurface;
            bindChip(h.ivIcon, item.iconRes, item.accentColor, iconColor, 11);

            float big = dp(22);
            float small = dp(4);
            ShapeAppearanceModel.Builder shape = h.cardView.getShapeAppearanceModel().toBuilder();
            if (item.danger || item.segmentSize == 1) {
                shape.setAllCornerSizes(item.danger ? dp(20) : big);
            } else if (item.segmentPosition == 0) {
                shape.setTopLeftCornerSize(big).setTopRightCornerSize(big)
                        .setBottomLeftCornerSize(small).setBottomRightCornerSize(small);
            } else if (item.segmentPosition == item.segmentSize - 1) {
                shape.setTopLeftCornerSize(small).setTopRightCornerSize(small)
                        .setBottomLeftCornerSize(big).setBottomRightCornerSize(big);
            } else {
                shape.setAllCornerSizes(small);
            }
            h.cardView.setShapeAppearanceModel(shape.build());

            boolean switchEnabled = true;
            if (item.requiresAnyOf != null) switchEnabled = anyOn(item.requiresAnyOf);
            if (item.disabledWhenTrue != null && getCurrentState(item.disabledWhenTrue)) switchEnabled = false;
            boolean extremeActive = item.isExtreme && localCache.getBoolean(item.prefKey, false);
            boolean locked = extremeActive || isExtremeLocked(item);
            if (locked) switchEnabled = false;

            String subtitle = item.subtitle;
            if (item.isExtreme) {
                if (extremeActive) subtitle = getString(R.string.extreme_mode_active_hint);
                else if (!switchEnabled) subtitle = getString(R.string.extreme_mode_requires_hint);
                else subtitle = getString(R.string.extreme_mode_desc);
            } else if (locked) {
                subtitle = getString(item.extremeLock == 2 ? R.string.extreme_dm_locked_hint : R.string.extreme_locked_hint);
            }
            h.tvSubtitle.setText(subtitle);
            h.tvSubtitle.setVisibility(subtitle != null ? View.VISIBLE : View.GONE);

            if (item.danger) {
                h.cardView.setCardBackgroundColor(color(R.color.ie_danger_faint));
                h.cardView.setStrokeColor(color(R.color.ie_danger_border));
                h.cardView.setStrokeWidth(dp(1));
                h.tvTitle.setTextColor(color(R.color.ie_danger_text));
                h.tvTitle.setTypeface(android.graphics.Typeface.create(h.tvTitle.getTypeface(), 600, false));
                h.tvTitle.setTextSize(15f);
                bindChip(h.ivIcon, item.iconRes, 0, color(R.color.ie_danger), 13);
                h.ivIcon.setBackground(roundRect(color(R.color.ie_danger_soft), dp(13)));
            } else {
                h.cardView.setCardBackgroundColor(color(R.color.ie_card));
                h.cardView.setStrokeWidth(0);
                h.tvTitle.setTextColor(item.textColor != 0 ? item.textColor : onSurface);
                h.tvTitle.setTypeface(android.graphics.Typeface.create(h.tvTitle.getTypeface(), 400, false));
            }

            int start = item.child ? dp(40) : dp(16);
            h.rowContent.setPaddingRelative(start, h.rowContent.getPaddingTop(), dp(16), h.rowContent.getPaddingBottom());

            h.itemView.setEnabled(switchEnabled || item.type == FeatureItem.TYPE_CLICKABLE);
            h.swToggle.setEnabled(switchEnabled);
            float contentAlpha = switchEnabled || locked || item.type == FeatureItem.TYPE_CLICKABLE ? 1f : 0.45f;
            h.ivIcon.setAlpha(locked && !item.danger ? 0.55f : contentAlpha);
            h.tvTitle.setAlpha(contentAlpha);

            h.ivChevron.setVisibility(item.isNav ? View.VISIBLE : View.GONE);
            h.ivLock.setVisibility(isSwitch && locked ? View.VISIBLE : View.GONE);
            if (item.danger) {
                h.ivLock.setImageTintList(ColorStateList.valueOf(color(R.color.ie_danger)));
                h.ivLock.setBackground(null);
            } else {
                h.ivLock.setImageTintList(ColorStateList.valueOf(color(R.color.corona_amber)));
                h.ivLock.setBackgroundResource(R.drawable.bg_lock_pill);
            }
            h.swToggle.setContentDescription(title);

            h.swToggle.setOnCheckedChangeListener(null);
            h.itemView.setOnClickListener(null);

            if (item.type == FeatureItem.TYPE_CLICKABLE) {
                h.swToggle.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> {
                    if (item.onClick != null) item.onClick.run();
                });
                return;
            }

            h.swToggle.setVisibility(locked ? View.GONE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> {
                if (!locked) h.swToggle.toggle();
            });
            h.swToggle.setChecked(checked);

            h.swToggle.setOnCheckedChangeListener((btn, isChecked) -> {
                if (item.isExtreme && isChecked) {
                    new MaterialAlertDialogBuilder(h.itemView.getContext())
                            .setTitle(getString(R.string.ig_dialog_distraction_extreme_title))
                            .setMessage(getString(R.string.ig_dialog_distraction_extreme_message))
                            .setPositiveButton(getString(R.string.extreme_confirm_button), (dialog, which) -> {
                                stageChange(item.prefKey, true);
                                stageChange("isDistractionFree", true);
                                notifyDataSetChanged();
                            })
                            .setNegativeButton(getString(R.string.ig_dialog_cancel), (dialog, which) -> dialog.cancel())
                            .setOnCancelListener(dialog -> {
                                int pos = h.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                            })
                            .show();
                    return;
                }

                if (item.prefKey == null) return;
                stageChange(item.prefKey, isChecked);
                if (isChecked && item.dependsOn != null) stageChange(item.dependsOn, true);
                if (!isChecked && item.cascadeOffKey != null) stageChange(item.cascadeOffKey, false);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void bindChip(ImageView iconView, int iconRes, int accentColor, int stateColor, int radiusDp) {
            if (iconRes == 0) {
                iconView.setVisibility(View.GONE);
                return;
            }
            iconView.setVisibility(View.VISIBLE);
            boolean colorful = FeatureAccents.isColorful() && accentColor != 0;
            int tint = colorful ? accentColor : stateColor;
            android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(iconView.getContext(), iconRes);
            if (icon != null) {
                icon = icon.mutate();
                icon.setColorFilter(new android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN));
                iconView.setImageDrawable(icon);
            }
            int chip = colorful ? (accentColor & 0x00FFFFFF) | 0x33000000 : color(R.color.ie_chip);
            iconView.setBackground(roundRect(chip, dp(radiusDp)));
        }
    }

    private void showMenu(String title, List<Object> definitions) {
        showMenu(title, definitions, false);
    }

    private void showMenu(String title, List<Object> definitions, boolean tileGrid) {
        ViewGroup headerLayout = fragmentView.findViewById(R.id.header_layout);
        TransitionManager.beginDelayedTransition(headerLayout, new AutoTransition().setDuration(200));

        boolean root = title.equals(getString(R.string.features));
        tvHeaderTitle.setText(title);
        tvHeaderTitle.setTextAppearance(root ? R.style.TextAppearance_InstaEclipse_ScreenTitle : R.style.TextAppearance_InstaEclipse_Display);
        tvHeaderSubtitle.setVisibility(root ? View.VISIBLE : View.GONE);
        btnBack.setVisibility(root ? View.GONE : View.VISIBLE);
        headerLayout.setPaddingRelative(dp(root ? 20 : 10), dp(root ? 26 : 16), dp(20), dp(root ? 14 : 8));
        ((ViewGroup.MarginLayoutParams) btnBack.getLayoutParams()).setMarginStart(0);

        List<FeatureItem> displayList = new ArrayList<>();
        for (int di = 0; di < definitions.size(); di++) {
            Object def = definitions.get(di);
            if (def instanceof String) {
                FeatureItem header = new FeatureItem();
                header.type = FeatureItem.TYPE_HEADER;
                header.title = (String) def;
                displayList.add(header);
            } else if (def instanceof List) {
                @SuppressWarnings("unchecked")
                List<FeatureItem> group = (List<FeatureItem>) def;
                for (int i = 0; i < group.size(); i++) {
                    FeatureItem item = group.get(i);
                    if (tileGrid && item.tile) {
                        item.type = FeatureItem.TYPE_TILE;
                    }
                    item.segmentPosition = i;
                    item.segmentSize = group.size();
                    displayList.add(item);
                }
                if (di < definitions.size() - 1) {
                    FeatureItem spacer = new FeatureItem();
                    spacer.type = FeatureItem.TYPE_SPACER;
                    displayList.add(spacer);
                }
            }
        }

        applyRecyclerLayout(tileGrid);

        recyclerFeatures.setAlpha(0f);
        float driftPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, getResources().getDisplayMetrics());
        recyclerFeatures.setTranslationY(driftPx);

        adapter.setItems(displayList);
        recyclerFeatures.scrollToPosition(0);

        recyclerFeatures.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void applyRecyclerLayout(boolean tileGrid) {
        if (tileSpacingDecoration != null) {
            recyclerFeatures.removeItemDecoration(tileSpacingDecoration);
            tileSpacingDecoration = null;
        }
        if (tileGrid) {
            GridLayoutManager glm = new GridLayoutManager(requireContext(), 2);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return adapter.items.get(position).type == FeatureItem.TYPE_TILE ? 1 : 2;
                }
            });
            recyclerFeatures.setLayoutManager(glm);
            final int halfGap = dp(5);
            tileSpacingDecoration = new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int pos = parent.getChildAdapterPosition(view);
                    if (pos == RecyclerView.NO_POSITION || pos >= adapter.items.size()) return;
                    if (adapter.items.get(pos).type != FeatureItem.TYPE_TILE) return;
                    boolean rtl = parent.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    boolean first = ((GridLayoutManager.LayoutParams) view.getLayoutParams()).getSpanIndex() == 0;
                    if (first == rtl) outRect.left = halfGap;
                    else outRect.right = halfGap;
                    outRect.bottom = halfGap * 2;
                }
            };
            recyclerFeatures.addItemDecoration(tileSpacingDecoration);
        } else {
            recyclerFeatures.setLayoutManager(new LinearLayoutManager(requireContext()));
        }
    }

    private FeatureItem createNav(String title, Runnable navAction) {
        FeatureItem item = new FeatureItem();
        item.type = FeatureItem.TYPE_CLICKABLE;
        item.title = title;
        item.onClick = navAction;
        item.isNav = true;
        return item;
    }

    private int accentColor() {
        return menuAccent;
    }

    private FeatureItem createNav(int iconRes, String title, String accentHex, Runnable navAction) {
        FeatureItem item = createNav(title, navAction);
        item.iconRes = iconRes;
        item.accentColor = FeatureAccents.color(accentHex);
        return item;
    }

    private FeatureItem createNav(int iconRes, String title, Runnable navAction) {
        return createNav(iconRes, title, EclipseAccents.PRIMARY, navAction);
    }

    private FeatureItem createClickable(int iconRes, String title, String accentHex, Runnable onClick) {
        FeatureItem item = createClickable(title, onClick);
        item.iconRes = iconRes;
        item.accentColor = FeatureAccents.color(accentHex);
        return item;
    }

    private FeatureItem createClickable(int iconRes, String title, Runnable onClick) {
        return createClickable(iconRes, title, EclipseAccents.PRIMARY, onClick);
    }

    private FeatureItem createClickable(String title, Runnable onClick) {
        FeatureItem item = new FeatureItem();
        item.type = FeatureItem.TYPE_CLICKABLE;
        item.title = title;
        item.onClick = onClick;
        return item;
    }

    private FeatureItem createSwitch(String title, String prefKey) {
        FeatureItem item = new FeatureItem();
        item.type = FeatureItem.TYPE_SWITCH;
        item.title = title;
        item.prefKey = prefKey;
        return item;
    }

    private FeatureItem createSwitch(int iconRes, String title, String prefKey) {
        FeatureItem item = createSwitch(title, prefKey);
        item.iconRes = iconRes;
        item.accentColor = accentColor();
        return item;
    }

    private FeatureItem createSwitchWithDependency(String title, String prefKey, String dependsOn) {
        FeatureItem item = createSwitch(title, prefKey);
        item.dependsOn = dependsOn;
        return item;
    }

    private FeatureItem createSwitchWithDependency(int iconRes, String title, String prefKey, String dependsOn) {
        FeatureItem item = createSwitchWithDependency(title, prefKey, dependsOn);
        item.iconRes = iconRes;
        item.accentColor = accentColor();
        return item;
    }

    private FeatureItem createSwitchWithCascadeOff(String title, String prefKey, String cascadeOffKey) {
        FeatureItem item = createSwitch(title, prefKey);
        item.cascadeOffKey = cascadeOffKey;
        return item;
    }

    private FeatureItem createSwitchWithCascadeOff(int iconRes, String title, String prefKey, String cascadeOffKey) {
        FeatureItem item = createSwitchWithCascadeOff(title, prefKey, cascadeOffKey);
        item.iconRes = iconRes;
        item.accentColor = accentColor();
        return item;
    }

    private static FeatureItem withSubtitle(FeatureItem item, String subtitle) {
        item.subtitle = subtitle;
        return item;
    }

    private FeatureItem createMasterSwitch(int iconRes, String title, List<String> childKeys) {
        FeatureItem item = new FeatureItem();
        item.type = FeatureItem.TYPE_MASTER_SWITCH;
        item.iconRes = iconRes;
        item.title = title;
        item.childKeys = childKeys;
        return item;
    }

    private String masterTitle(int sectionRes) {
        return getString(R.string.feat_master_enable, getString(sectionRes));
    }

    private FeatureItem createCategory(int iconRes, String title, String accentHex, List<String> stateKeys, Runnable navAction) {
        FeatureItem item = createNav(iconRes, title, accentHex, navAction);
        item.tile = true;
        item.stateKeys = stateKeys;
        return item;
    }

    private static final List<String> GHOST_KEYS = Arrays.asList(
            "isGhostSeen", "isGhostTyping", "isGhostStory", "isGhostLive",
            "allowScreenshots", "isGhostScreenshot", "isGhostViewOnce",
            "enableUnlimitedReplays", "permanentViewMode", "keepEphemeralMessages");
    private static final List<String> AD_KEYS = Arrays.asList("isAdBlockEnabled", "isAnalyticsBlocked", "disableTrackingLinks");
    private static final List<String> CLEAN_FEED_KEYS = Arrays.asList("hideSuggestionsInFeed", "hideThreadsSuggestions");
    private static final List<String> DISTRACTION_KEYS = Arrays.asList(
            "disableStories", "disableFeed", "disableReels",
            "disableReelsExceptDM", "disableExplore", "disableComments");
    private static final List<String> MISC_KEYS = Arrays.asList(
            "disableStoryFlipping", "disableVideoAutoPlay", "enableStoryMentions", "enablePhotoZoom",
            "disableRepost", "disableDoubleTapLike", "enableCopyComment", "enableCaptionCopy",
            "disableDiscoverPeople", "showFeatureToasts", "showFollowerToast", "spoofLastSeen");
    private static final List<String> DOWNLOAD_FEATURE_KEYS = Arrays.asList(
            "enablePostDownload", "enableStoryDownload", "enableReelDownload", "enableProfileDownload", "enableVoiceDownload");

    private void loadMainMenu() {
        FeatureFlags.colorfulFeatureIcons = getCurrentState("colorfulFeatureIcons");
        List<Object> defs = new ArrayList<>();

        int quality = localCache.getInt("forceReelQuality", 0);
        FeatureItem qualityTile = createCategory(R.drawable.ic_ie_film, getString(R.string.ig_dialog_menu_quality),
                FeatureAccents.QUALITY, null, this::loadQualityMenu);
        qualityTile.subtitle = qualityLabel(quality);
        qualityTile.valueOn = quality != 0;

        defs.add(getString(R.string.feat_categories));
        defs.add(Arrays.asList(
                createCategory(R.drawable.ic_ie_code, getString(R.string.ig_dialog_menu_dev_options), FeatureAccents.DEV,
                        Arrays.asList("isDevEnabled", "removeBuildExpiredPopup"), this::loadDevMenu),
                createCategory(R.drawable.ic_ie_ghost, getString(R.string.ig_dialog_menu_ghost_settings), FeatureAccents.GHOST,
                        GHOST_KEYS, this::loadGhostMenu),
                createCategory(R.drawable.ic_ie_shield_x, getString(R.string.ig_dialog_menu_ad_analytics), FeatureAccents.ADS,
                        AD_KEYS, this::loadAdsMenu),
                createCategory(R.drawable.ic_ie_lines, getString(R.string.ig_dialog_menu_clean_feed), FeatureAccents.CLEAN_FEED,
                        CLEAN_FEED_KEYS, this::loadCleanFeedMenu),
                createCategory(R.drawable.ic_ie_target, getString(R.string.ig_dialog_menu_distraction_free), FeatureAccents.DISTRACTION,
                        DISTRACTION_KEYS, this::loadDistractionMenu),
                createCategory(R.drawable.ic_ie_sliders, getString(R.string.ig_dialog_menu_misc), FeatureAccents.MISC,
                        MISC_KEYS, this::loadMiscMenu),
                createCategory(R.drawable.ic_ie_download, getString(R.string.ig_dialog_menu_downloader), FeatureAccents.DOWNLOADER,
                        DOWNLOAD_FEATURE_KEYS, this::loadDownloaderMenu),
                createCategory(R.drawable.ic_ie_pin, getString(R.string.ig_dialog_menu_location), FeatureAccents.LOCATION,
                        Arrays.asList("spoofLocation"), this::loadLocationMenu),
                qualityTile,
                createCategory(R.drawable.ic_ie_palette, getString(R.string.theme_customize), FeatureAccents.THEME,
                        Arrays.asList("customThemeEnabled"), () ->
                                themeCustomizerLauncher.launch(new Intent(requireContext(), ThemeCustomizerActivity.class)))
        ));

        defs.add(getString(R.string.feat_tools));
        defs.add(Arrays.asList(
                withSubtitle(createNav(R.drawable.ic_ie_brush, getString(R.string.ig_dialog_icon_style), FeatureAccents.THEME, this::loadIconStyleMenu),
                        getString(FeatureFlags.colorfulFeatureIcons ? R.string.ig_dialog_icon_style_colorful : R.string.ig_dialog_icon_style_yellow)),
                createNav(R.drawable.ic_ie_archive, getString(R.string.ig_dialog_menu_backup_restore), FeatureAccents.SUCCESS, this::showBackupRestoreChooser),
                createNav(R.drawable.ic_ie_check, getString(R.string.verify_hooks_title), FeatureAccents.VERIFY, () ->
                        startActivity(new Intent(requireContext(), VerifyHooksActivity.class)))
        ));

        showMenu(getString(R.string.features), defs, true);
        currentMenu = "main";
    }

    private void showBackupRestoreChooser() {
        String[] options = {getString(R.string.ig_dialog_backup_settings), getString(R.string.ig_dialog_restore_settings)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ig_dialog_menu_backup_restore)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) backupSettings();
                    else restoreSettings();
                })
                .setNegativeButton(R.string.ig_dialog_cancel, null)
                .show();
    }

    private void loadIconStyleMenu() {
        FeatureFlags.colorfulFeatureIcons = localCache.getBoolean("colorfulFeatureIcons", false);
        menuAccent = FeatureAccents.primary();
        boolean colorful = FeatureFlags.colorfulFeatureIcons;
        List<Object> defs = new ArrayList<>();

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(
                createIconStyleOption(getString(R.string.ig_dialog_icon_style_yellow), !colorful, false),
                createIconStyleOption(getString(R.string.ig_dialog_icon_style_colorful), colorful, true)
        ));

        showMenu(getString(R.string.ig_dialog_icon_style), defs);
        currentMenu = "icon_style";
    }

    private FeatureItem createIconStyleOption(String title, boolean selected, boolean colorfulValue) {
        FeatureItem item = createClickable(R.drawable.ic_ie_brush, title, FeatureAccents.THEME, () -> applyIconStyle(colorfulValue));
        if (selected) {
            item.title = title + "  ✓";
            item.textColor = ContextCompat.getColor(requireContext(), R.color.ie_accent_text);
        }
        return item;
    }

    private void applyIconStyle(boolean colorful) {
        SharedPreferences.Editor editor = localCache.edit();
        editor.putBoolean("colorfulFeatureIcons", colorful);
        long updatedAt = touchAndFlush(editor);
        sendBoolPref("colorfulFeatureIcons", colorful, updatedAt);
        FeatureFlags.colorfulFeatureIcons = colorful;
        stagedChanges.remove("colorfulFeatureIcons");
        updatePendingBar();
        loadIconStyleMenu();
    }

    private void loadDevMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.DEV);
        List<Object> defs = new ArrayList<>();

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(createSwitch(R.drawable.ic_ie_code, getString(R.string.ig_dialog_dev_enable), "isDevEnabled")));

        defs.add(getString(R.string.feat_config));
        defs.add(Arrays.asList(
                createClickable(R.drawable.ic_download, getString(R.string.ig_dialog_dev_import), FeatureAccents.SUCCESS, this::importDevConfig),
                createClickable(R.drawable.ic_upload, getString(R.string.ig_dialog_dev_export), FeatureAccents.INFO, this::exportDevConfig),
                createClickable(R.drawable.ic_ie_restart, getString(R.string.ig_dialog_dev_restore_default_config), FeatureAccents.DOWNLOADER, this::restoreDefaultConfig)
        ));

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(createSwitch(R.drawable.ic_block, getString(R.string.ig_dialog_dev_remove_build_expired), "removeBuildExpiredPopup")));

        showMenu(getString(R.string.ig_dialog_section_dev_options), defs);
        currentMenu = "dev";
    }

    private void loadGhostMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.GHOST);
        List<Object> defs = new ArrayList<>();

        FeatureItem permanent = createSwitch(R.drawable.ic_ie_eye, getString(R.string.ig_dialog_ghost_permanent_view_once), "permanentViewMode");
        permanent.unstable = true;

        defs.add(Arrays.asList(createMasterSwitch(R.drawable.ic_ie_ghost, masterTitle(R.string.ig_dialog_section_ghost_mode), GHOST_KEYS)));

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_ie_eye_off, getString(R.string.ig_dialog_ghost_hide_dm_seen), "isGhostSeen"),
                createSwitch(R.drawable.ic_ie_typing, getString(R.string.ig_dialog_ghost_hide_typing), "isGhostTyping"),
                createSwitch(R.drawable.ic_ie_story, getString(R.string.ig_dialog_ghost_hide_story_views), "isGhostStory"),
                createSwitch(R.drawable.ic_ie_live, getString(R.string.ig_dialog_ghost_hide_live_presence), "isGhostLive"),
                createSwitch(R.drawable.ic_ie_camera, getString(R.string.ig_dialog_ghost_allow_screenshots_dms), "allowScreenshots"),
                createSwitch(R.drawable.ic_ie_smartphone, getString(R.string.ig_dialog_ghost_bypass_screenshot), "isGhostScreenshot"),
                createSwitch(R.drawable.ic_ie_image, getString(R.string.ig_dialog_ghost_hide_view_once), "isGhostViewOnce"),
                createSwitch(R.drawable.ic_ie_repeat, getString(R.string.ig_dialog_ghost_unlimited_replays), "enableUnlimitedReplays"),
                permanent,
                createSwitch(R.drawable.ic_ie_timer, getString(R.string.ig_dialog_ghost_keep_disappearing), "keepEphemeralMessages")
        ));

        defs.add(Arrays.asList(withSubtitle(createNav(R.drawable.ic_ie_zap, getString(R.string.ig_dialog_customize_quick_toggle),
                FeatureAccents.GHOST, this::loadQuickTogglesMenu), getString(R.string.feat_quick_toggle_desc))));

        showMenu(getString(R.string.ig_dialog_section_ghost_mode), defs);
        currentMenu = "ghost";
    }

    private void loadQuickTogglesMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.GHOST);
        List<Object> defs = new ArrayList<>();

        FeatureItem permanent = createSwitch(R.drawable.ic_ie_eye, getString(R.string.ig_dialog_quick_permanent_view), "quickTogglePermanentView");
        permanent.unstable = true;

        defs.add(Arrays.asList(createMasterSwitch(R.drawable.ic_ie_zap, getString(R.string.ig_dialog_enable_disable_all), Arrays.asList(
                "quickToggleSeen", "quickToggleTyping", "quickToggleScreenshot",
                "quickToggleViewOnce", "quickToggleStory", "quickToggleLive",
                "quickToggleEphemeral", "quickToggleReplays", "quickTogglePermanentView",
                "quickToggleAllowScreenshots"))));

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_ie_eye_off, getString(R.string.ig_dialog_quick_hide_seen), "quickToggleSeen"),
                createSwitch(R.drawable.ic_ie_typing, getString(R.string.ig_dialog_quick_hide_typing), "quickToggleTyping"),
                createSwitch(R.drawable.ic_ie_smartphone, getString(R.string.ig_dialog_quick_disable_screenshot), "quickToggleScreenshot"),
                createSwitch(R.drawable.ic_ie_image, getString(R.string.ig_dialog_quick_hide_view_once), "quickToggleViewOnce"),
                createSwitch(R.drawable.ic_ie_story, getString(R.string.ig_dialog_quick_hide_story_seen), "quickToggleStory"),
                createSwitch(R.drawable.ic_ie_live, getString(R.string.ig_dialog_quick_hide_live_seen), "quickToggleLive"),
                createSwitch(R.drawable.ic_ie_timer, getString(R.string.ig_dialog_quick_keep_ephemeral), "quickToggleEphemeral"),
                createSwitch(R.drawable.ic_ie_repeat, getString(R.string.ig_dialog_quick_unlimited_replays), "quickToggleReplays"),
                permanent,
                createSwitch(R.drawable.ic_ie_camera, getString(R.string.ig_dialog_quick_allow_screenshots), "quickToggleAllowScreenshots")
        ));

        showMenu(getString(R.string.ig_dialog_section_quick_toggle), defs);
        currentMenu = "qt";
    }

    private void loadAdsMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.ADS);
        List<Object> defs = new ArrayList<>();

        defs.add(Arrays.asList(createMasterSwitch(R.drawable.ic_ie_shield_x, masterTitle(R.string.ig_dialog_section_ad_analytics), AD_KEYS)));

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_shield, getString(R.string.ig_dialog_ad_block_ads), "isAdBlockEnabled"),
                createSwitch(R.drawable.ic_analytics, getString(R.string.ig_dialog_ad_block_analytics), "isAnalyticsBlocked"),
                createSwitch(R.drawable.ic_link, getString(R.string.ig_dialog_ad_disable_tracking), "disableTrackingLinks")
        ));

        showMenu(getString(R.string.ig_dialog_section_ad_analytics), defs);
        currentMenu = "ads";
    }

    private void loadCleanFeedMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.CLEAN_FEED);
        List<Object> defs = new ArrayList<>();

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_sparkle, getString(R.string.ig_dialog_clean_feed_hide_suggested), "hideSuggestionsInFeed"),
                createSwitch(R.drawable.ic_at, getString(R.string.ig_dialog_clean_feed_hide_threads), "hideThreadsSuggestions")
        ));

        showMenu(getString(R.string.ig_dialog_section_clean_feed), defs);
        currentMenu = "cleanfeed";
    }

    private void loadDistractionMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.DISTRACTION);
        List<Object> defs = new ArrayList<>();

        FeatureItem extreme = createSwitch(R.drawable.ic_ie_warning, getString(R.string.extreme_mode_title), "isExtremeMode");
        extreme.isExtreme = true;
        extreme.danger = true;
        extreme.requiresAnyOf = DISTRACTION_KEYS;
        defs.add(Arrays.asList(extreme));

        FeatureItem disableReels = createSwitchWithCascadeOff(R.drawable.ic_ie_reels, getString(R.string.ig_dialog_distraction_disable_reels), "disableReels", "disableReelsExceptDM");
        disableReels.extremeLock = 1;

        FeatureItem disableReelsExceptDM = createSwitchWithDependency(R.drawable.ic_ie_bubble, getString(R.string.ig_dialog_distraction_disable_reels_except_dm), "disableReelsExceptDM", "disableReels");
        disableReelsExceptDM.extremeLock = 2;
        disableReelsExceptDM.child = true;

        FeatureItem masterSwitch = createMasterSwitch(R.drawable.ic_ie_target, masterTitle(R.string.ig_dialog_section_distraction_free), DISTRACTION_KEYS);
        masterSwitch.disabledWhenTrue = "isExtremeMode";
        defs.add(Arrays.asList(masterSwitch));

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(
                createSwitchLockedByExtreme(R.drawable.ic_ie_story, getString(R.string.ig_dialog_distraction_disable_stories), "disableStories"),
                createSwitchLockedByExtreme(R.drawable.ic_ie_lines, getString(R.string.ig_dialog_distraction_disable_feed), "disableFeed"),
                disableReels,
                disableReelsExceptDM,
                createSwitchLockedByExtreme(R.drawable.ic_ie_compass, getString(R.string.ig_dialog_distraction_disable_explore), "disableExplore"),
                createSwitchLockedByExtreme(R.drawable.ic_ie_comment, getString(R.string.ig_dialog_distraction_disable_comments), "disableComments")
        ));

        showMenu(getString(R.string.ig_dialog_section_distraction_free), defs);
        currentMenu = "distract";
    }

    private FeatureItem createSwitchLockedByExtreme(int iconRes, String title, String prefKey) {
        FeatureItem item = createSwitch(iconRes, title, prefKey);
        item.extremeLock = 1;
        return item;
    }

    private void loadMiscMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.MISC);
        List<Object> defs = new ArrayList<>();

        defs.add(Arrays.asList(createMasterSwitch(R.drawable.ic_ie_sliders, getString(R.string.ig_dialog_enable_disable_all), MISC_KEYS)));

        defs.add(getString(R.string.feat_group_stories_video));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_story_ring, getString(R.string.ig_dialog_misc_disable_story_autoswipe), "disableStoryFlipping"),
                createSwitch(R.drawable.ic_play_circle, getString(R.string.ig_dialog_misc_disable_video_autoplay), "disableVideoAutoPlay"),
                createSwitch(R.drawable.ic_at, getString(R.string.ig_dialog_misc_view_story_mentions), "enableStoryMentions"),
                createSwitch(R.drawable.ic_zoom_in, getString(R.string.ig_dialog_misc_photo_zoom), "enablePhotoZoom")
        ));

        defs.add(getString(R.string.feat_group_interactions));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_repeat, getString(R.string.ig_dialog_misc_disable_repost), "disableRepost"),
                createSwitch(R.drawable.ic_heart, getString(R.string.ig_dialog_misc_disable_double_tap_like), "disableDoubleTapLike"),
                createSwitch(R.drawable.ic_content_copy, getString(R.string.ig_dialog_misc_copy_comment), "enableCopyComment"),
                createSwitch(R.drawable.ic_subject, getString(R.string.ig_dialog_misc_copy_caption), "enableCaptionCopy"),
                createSwitch(R.drawable.ic_group, getString(R.string.ig_dialog_misc_disable_discover_people), "disableDiscoverPeople")
        ));

        defs.add(getString(R.string.feat_group_notifications));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_notification, getString(R.string.ig_dialog_misc_show_feature_toasts), "showFeatureToasts"),
                createSwitch(R.drawable.ic_person_add, getString(R.string.ig_dialog_misc_show_follower_toast), "showFollowerToast")
        ));

        defs.add(getString(R.string.feat_group_privacy));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_timer, getString(R.string.ig_dialog_misc_spoof_last_seen), "spoofLastSeen")
        ));

        showMenu(getString(R.string.ig_dialog_section_misc), defs);
        currentMenu = "misc";
    }

    private void loadLocationMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.LOCATION);
        List<Object> defs = new ArrayList<>();

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(createSwitch(R.drawable.ic_pin, getString(R.string.ig_dialog_location_spoof_enable), "spoofLocation")));

        double lat = 0.0, lng = 0.0;
        try { lat = Double.parseDouble(localCache.getString("spoofLat", "0")); } catch (Throwable ignored) {}
        try { lng = Double.parseDouble(localCache.getString("spoofLng", "0")); } catch (Throwable ignored) {}
        String coordLabel = (lat == 0.0 && lng == 0.0)
                ? getString(R.string.ig_dialog_location_unset)
                : getString(R.string.ig_dialog_location_current, lat, lng);

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(withSubtitle(createNav(R.drawable.ic_explore,
                getString(R.string.ig_dialog_location_pick), () -> {
                    double curLat = 0.0, curLng = 0.0;
                    try { curLat = Double.parseDouble(localCache.getString("spoofLat", "0")); } catch (Throwable ignored) {}
                    try { curLng = Double.parseDouble(localCache.getString("spoofLng", "0")); } catch (Throwable ignored) {}
                    Intent i = new Intent(requireContext(), LocationPickerActivity.class);
                    i.putExtra(LocationPickerActivity.EXTRA_LAT, curLat);
                    i.putExtra(LocationPickerActivity.EXTRA_LNG, curLng);
                    locationPickerLauncher.launch(i);
                }), coordLabel)));

        showMenu(getString(R.string.ig_dialog_section_location), defs);
        currentMenu = "location";
    }

    private String qualityLabel(int h) {
        if (h == 360) return getString(R.string.ig_dialog_quality_360);
        if (h == 480) return getString(R.string.ig_dialog_quality_480);
        if (h == 720) return getString(R.string.ig_dialog_quality_720);
        if (h == 1080) return getString(R.string.ig_dialog_quality_1080);
        if (h == Integer.MAX_VALUE) return getString(R.string.ig_dialog_quality_max);
        return getString(R.string.ig_dialog_quality_auto);
    }

    private void loadQualityMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.QUALITY);
        List<Object> defs = new ArrayList<>();
        int current = localCache.getInt("forceReelQuality", 0);

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(withSubtitle(createClickable(R.drawable.ic_movie,
                getString(R.string.ig_dialog_quality_force_reels), () -> pickQuality(current)), qualityLabel(current))));

        showMenu(getString(R.string.ig_dialog_section_quality), defs);
        currentMenu = "quality";
    }

    private void pickQuality(int current) {
        String[] labels = {
                getString(R.string.ig_dialog_quality_auto), getString(R.string.ig_dialog_quality_360),
                getString(R.string.ig_dialog_quality_480), getString(R.string.ig_dialog_quality_720),
                getString(R.string.ig_dialog_quality_1080), getString(R.string.ig_dialog_quality_max)
        };
        int[] values = {0, 360, 480, 720, 1080, Integer.MAX_VALUE};

        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { sel = i; break; }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.ig_dialog_quality_force_reels))
                .setSingleChoiceItems(labels, sel, (dialog, which) -> {
                    int value = values[which];
                    SharedPreferences.Editor ed = localCache.edit();
                    ed.putInt("forceReelQuality", value);
                    long updatedAt = touchAndFlush(ed);
                    sendIntPref("forceReelQuality", value, updatedAt);

                    dialog.dismiss();
                    loadQualityMenu();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadDownloaderMenu() {
        menuAccent = FeatureAccents.color(FeatureAccents.DOWNLOADER);
        List<Object> defs = new ArrayList<>();

        defs.add(Arrays.asList(createMasterSwitch(R.drawable.ic_ie_download,
                masterTitle(R.string.ig_dialog_section_downloader), DOWNLOAD_FEATURE_KEYS)));

        defs.add(getString(R.string.feat_features));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_image, getString(R.string.ig_dialog_downloader_posts), "enablePostDownload"),
                createSwitch(R.drawable.ic_story_ring, getString(R.string.ig_dialog_downloader_stories), "enableStoryDownload"),
                createSwitch(R.drawable.ic_movie, getString(R.string.ig_dialog_downloader_reels), "enableReelDownload"),
                createSwitch(R.drawable.ic_person, getString(R.string.ig_dialog_downloader_profiles), "enableProfileDownload"),
                createSwitch(R.drawable.ic_mic, getString(R.string.ig_dialog_downloader_voice), "enableVoiceDownload")
        ));

        defs.add(getString(R.string.feat_options));
        defs.add(Arrays.asList(
                createSwitch(R.drawable.ic_collections, getString(R.string.ig_dialog_downloader_batch), "enableBatchDownload"),
                createSwitch(R.drawable.ic_create_new_folder, getString(R.string.ig_dialog_downloader_username_subfolder), "downloaderUsernameFolder"),
                createSwitch(R.drawable.ic_timer, getString(R.string.ig_dialog_downloader_add_timestamp), "downloaderAddTimestamp"),
                createNav(R.drawable.ic_history, getString(R.string.feat_downloader_view_history), this::openDownloadHistory)
        ));

        String customPath = localCache.getString("downloaderCustomPath", "");

        defs.add(getString(R.string.feat_download_folder));
        defs.add(Arrays.asList(
                withSubtitle(createClickable(R.drawable.ic_folder, getString(R.string.feat_downloader_set_folder), this::pickDownloadFolder),
                        customPath.isEmpty() ? null : customPath),
                createClickable(R.drawable.ic_delete, getString(R.string.feat_downloader_reset_folder), this::resetDownloadFolder)
        ));

        showMenu(getString(R.string.ig_dialog_section_downloader), defs);
        currentMenu = "downloader";
    }

    private void pickDownloadFolder() {
        dirPickerLauncher.launch(null);
    }

    private void openDownloadHistory() {
        startActivity(new Intent(requireContext(), ps.reso.instaeclipse.DownloadHistoryActivity.class));
    }

    private void resetDownloadFolder() {
        SharedPreferences.Editor editor = localCache.edit();
        editor.remove("pathJustSetLocally");
        editor.putString("downloaderCustomUri", "");
        editor.putString("downloaderCustomPath", "");
        long updatedAt = touchAndFlush(editor);
        sendStringPref("downloaderCustomUri", "", updatedAt);
        sendStringPref("downloaderCustomPath", "", updatedAt);

        Toast.makeText(requireContext(), getString(R.string.ig_toast_download_folder_reset), Toast.LENGTH_SHORT).show();

        if ("downloader".equals(currentMenu)) {
            loadDownloaderMenu();
        }
    }

    private void backupSettings() {
        try {
            JSONObject settings = new JSONObject();
            Map<String, ?> all = localCache.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (entry.getValue() instanceof Boolean) {
                    settings.put(entry.getKey(), entry.getValue());
                }
            }
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("settings", settings);
            Intent exportIntent = new Intent();
            exportIntent.setComponent(new ComponentName(requireContext(), "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
            exportIntent.putExtra("json_content", root.toString(2));
            exportIntent.putExtra("file_name", "instaeclipse_settings.json");
            startActivity(exportIntent);
        } catch (JSONException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreSettings() {
        restoreFileLauncher.launch(new String[]{"application/json"});
    }

    private void importDevConfig() {
        Intent importIntent = new Intent();
        importIntent.setComponent(new ComponentName(requireContext(), "ps.reso.instaeclipse.mods.devops.config.JsonImportActivity"));
        importIntent.putExtra("target_package", "com.instagram.android");
        startActivity(importIntent);
    }

    private void exportDevConfig() {
        BroadcastReceiver configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing()) return;
                String error = intent.getStringExtra("error");
                if (error != null) {
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                String json = intent.getStringExtra("json_content");
                if (json == null || json.isEmpty()) {
                    Toast.makeText(activity, getString(R.string.export_no_config_data), Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent exportIntent = new Intent();
                exportIntent.setComponent(new ComponentName(activity, "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                exportIntent.putExtra("json_content", json);
                startActivity(exportIntent);
            }
        };
        IntentFilter filter = new IntentFilter("ps.reso.instaeclipse.ACTION_SEND_CONFIG");
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(configReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), configReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        Intent request = new Intent("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG");
        request.setPackage("com.instagram.android");
        requireContext().sendBroadcast(request);
    }

    private void restoreDefaultConfig() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.ig_dialog_dev_restore_default_config))
                .setMessage(getString(R.string.ig_dialog_dev_restore_default_config_confirm))
                .setPositiveButton(getString(R.string.ig_dialog_yes), (dialog, which) -> {
                    try (InputStream is = requireContext().getAssets().open("default_mc_overrides.json");
                         Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                        String json = scanner.hasNext() ? scanner.next() : "";
                        if (json.isEmpty()) {
                            Toast.makeText(requireContext(), getString(R.string.ig_toast_config_import_failed), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent broadcast = new Intent("ps.reso.instaeclipse.ACTION_IMPORT_CONFIG");
                        broadcast.setPackage("com.instagram.android");
                        broadcast.putExtra("json_content", json);
                        requireContext().sendBroadcast(broadcast);
                        Toast.makeText(requireContext(), getString(R.string.ig_toast_default_config_restored), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.ig_dialog_cancel), null)
                .show();
    }

    private void stageChange(String prefKey, boolean isChecked) {
        if (localCache.getBoolean(prefKey, false) == isChecked) {
            stagedChanges.remove(prefKey);
        } else {
            stagedChanges.put(prefKey, isChecked);
        }

        updatePendingBar();

        if (isChecked && DOWNLOAD_KEYS.contains(prefKey)) {
            maybeRequestNotificationPermission();
        }
    }

    private void maybeRequestNotificationPermission() {
        if (notifPermissionAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        notifPermissionAsked = true;
        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
    }

    public boolean hasPendingChanges() {
        return !stagedChanges.isEmpty();
    }

    public void resolvePendingChanges(Runnable proceed) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.apply_changes, (dialog, which) -> {
                    commitStagedChanges();
                    proceed.run();
                })
                .setNegativeButton(R.string.unsaved_discard, (dialog, which) -> {
                    discardStagedChanges();
                    proceed.run();
                })
                .setNeutralButton(R.string.ig_dialog_cancel, null)
                .show();
    }

    private void commitStagedChanges() {
        if (stagedChanges.isEmpty()) return;

        SharedPreferences.Editor editor = localCache.edit();
        for (Map.Entry<String, Boolean> entry : stagedChanges.entrySet()) {
            editor.putBoolean(entry.getKey(), entry.getValue());
        }
        long updatedAt = touchAndFlush(editor);
        for (Map.Entry<String, Boolean> entry : stagedChanges.entrySet()) {
            sendBoolPref(entry.getKey(), entry.getValue(), updatedAt);
        }
        FeatureFlags.colorfulFeatureIcons = localCache.getBoolean("colorfulFeatureIcons", false);
        stagedChanges.clear();
        updatePendingBar();
        Toast.makeText(requireContext(), getString(R.string.ig_toast_settings_applied), Toast.LENGTH_SHORT).show();
        if ("main".equals(currentMenu)) {
            loadMainMenu();
        } else if ("icon_style".equals(currentMenu)) {
            loadIconStyleMenu();
        } else if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void discardStagedChanges() {
        stagedChanges.clear();
        updatePendingBar();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updatePendingBar() {
        if (pendingBar == null) return;
        int count = stagedChanges.size();
        boolean show = count > 0;
        if (show) {
            pendingText.setText(getResources().getQuantityString(R.plurals.pending_changes, count, count));
        }
        if (show == (pendingBar.getVisibility() == View.VISIBLE)) return;
        pendingBar.animate().cancel();
        if (show) {
            pendingBar.setVisibility(View.VISIBLE);
            pendingBar.setAlpha(0f);
            pendingBar.setTranslationY(dp(16));
            pendingBar.animate().alpha(1f).translationY(0f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            pendingBar.animate().alpha(0f).translationY(dp(16)).setDuration(140)
                    .withEndAction(() -> pendingBar.setVisibility(View.GONE)).start();
        }
    }

    private boolean getCurrentState(String prefKey) {
        if (prefKey == null) return false;
        if (stagedChanges.containsKey(prefKey)) return stagedChanges.get(prefKey);
        return localCache.getBoolean(prefKey, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("ps.reso.instaeclipse.ACTION_SEND_PREFS");
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(prefsReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(requireContext(), prefsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
        sendToInstagram(new Intent("ps.reso.instaeclipse.ACTION_REQUEST_PREFS"));
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(prefsReceiver);
    }
}
