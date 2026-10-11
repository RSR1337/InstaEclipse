package ps.reso.instaeclipse.fragments;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import ps.reso.instaeclipse.MainActivity;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.Contributor;
import ps.reso.instaeclipse.utils.core.ModuleHookProbe;
import ps.reso.instaeclipse.utils.ui.EclipseRingDrawable;

public class HomeFragment extends Fragment {

    private enum Status { CHECKING, ACTIVE, INACTIVE, NOT_INSTALLED }

    private MaterialCardView statusCard;
    private EclipseRingDrawable statusRing;
    private TextView statusText;
    private TextView statusDescription;
    private View versionChip;
    private View hookDot;
    private TextView variantText;
    private TextView versionText;
    private TextView multiButton;
    private View actionsRow;
    private MaterialButton launchButton;
    private MaterialButton setupGuideButton;

    private String activePackage;
    private List<String> installedPackages;
    private int hookProbeGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        statusCard = view.findViewById(R.id.instagram_status_card);
        statusText = view.findViewById(R.id.instagram_status_text);
        statusDescription = view.findViewById(R.id.home_status_description);
        versionChip = view.findViewById(R.id.instagram_version_chip);
        hookDot = view.findViewById(R.id.module_hook_dot);
        variantText = view.findViewById(R.id.instagram_variant_text);
        versionText = view.findViewById(R.id.instagram_version_text);
        multiButton = view.findViewById(R.id.instagram_multi_button);
        actionsRow = view.findViewById(R.id.home_actions_row);
        launchButton = view.findViewById(R.id.launch_instagram_button);
        setupGuideButton = view.findViewById(R.id.home_setup_guide_button);

        float density = getResources().getDisplayMetrics().density;
        int core = ContextCompat.getColor(requireContext(), R.color.ie_ring_core);
        int gold = ContextCompat.getColor(requireContext(), R.color.corona_amber);
        statusRing = new EclipseRingDrawable(core, gold, 2.5f * density, 16 * density, true)
                .setLogo(ContextCompat.getDrawable(requireContext(), R.drawable.ic_logo));
        ((ImageView) view.findViewById(R.id.home_status_ring)).setImageDrawable(statusRing);
        ((ImageView) view.findViewById(R.id.home_logo_ring)).setImageDrawable(
                new EclipseRingDrawable(core, gold, 2f * density, 8 * density, true)
                        .setLogo(ContextCompat.getDrawable(requireContext(), R.drawable.ic_logo)));

        view.findViewById(R.id.home_help_button).setOnClickListener(v -> openHelp());
        setupGuideButton.setOnClickListener(v -> openHelp());
        view.findViewById(R.id.download_instagram_button).setOnClickListener(v ->
                openLink("https://www.apkmirror.com/uploads/?appcategory=instagram-instagram"));

        checkInstagramStatus();
        setupContributors(view);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activePackage != null) {
            probeHookStatus(activePackage);
        }
    }

    private void openHelp() {
        if (getActivity() instanceof MainActivity activity) {
            activity.openHelpTab();
        }
    }

    private void checkInstagramStatus() {
        PackageManager pm = requireContext().getPackageManager();

        installedPackages = new ArrayList<>();
        for (String pkg : CommonUtils.SUPPORTED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                installedPackages.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        if (installedPackages.isEmpty()) {
            applyStatus(Status.NOT_INSTALLED);
            return;
        }

        activePackage = installedPackages.contains(CommonUtils.IG_PACKAGE_NAME)
                ? CommonUtils.IG_PACKAGE_NAME
                : installedPackages.get(0);

        if (installedPackages.size() > 1) {
            multiButton.setVisibility(View.VISIBLE);
            multiButton.setText(getString(R.string.multiple_versions_detected).replace("⚠️", "").trim());
            multiButton.setOnClickListener(v -> showDetectedVersionsDialog(pm));
        } else {
            multiButton.setVisibility(View.GONE);
        }

        bindPackageActions(pm, activePackage);
        probeHookStatus(activePackage);
    }

    private void applyStatus(Status status) {
        if (statusCard == null) return;
        int gold = ContextCompat.getColor(requireContext(), R.color.corona_amber);
        int ringOff = ContextCompat.getColor(requireContext(), R.color.ie_ring_off);
        boolean installed = status != Status.NOT_INSTALLED;

        switch (status) {
            case CHECKING -> {
                statusText.setText(R.string.home_hook_checking);
                statusRing.setState(gold, false);
                setHookDotColor(gold);
            }
            case ACTIVE -> {
                statusText.setText(R.string.home_hook_active);
                statusRing.setState(gold, true);
                setHookDotColor(ContextCompat.getColor(requireContext(), R.color.ie_hook_ok));
            }
            case INACTIVE -> {
                statusText.setText(R.string.home_module_inactive_title);
                statusRing.setState(ringOff, false);
                setHookDotColor(ContextCompat.getColor(requireContext(), R.color.ie_danger));
            }
            case NOT_INSTALLED -> {
                statusText.setText(R.string.not_installed_instagram);
                statusRing.setState(ringOff, false);
            }
        }

        boolean inactive = status == Status.INACTIVE;
        statusCard.setStrokeColor(ContextCompat.getColor(requireContext(),
                inactive ? R.color.ie_inactive_stroke : R.color.ie_card_stroke));
        statusDescription.setVisibility(inactive ? View.VISIBLE : View.GONE);
        versionChip.setVisibility(installed ? View.VISIBLE : View.GONE);
        setupGuideButton.setVisibility(inactive ? View.VISIBLE : View.GONE);
        actionsRow.setVisibility(inactive ? View.GONE : View.VISIBLE);
        launchButton.setVisibility(installed ? View.VISIBLE : View.GONE);
        actionsRow.findViewById(R.id.home_actions_gap).setVisibility(installed ? View.VISIBLE : View.GONE);

        MaterialButton download = actionsRow.findViewById(R.id.download_instagram_button);
        if (installed) {
            download.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            download.setTextColor(MaterialColors.getColor(download, com.google.android.material.R.attr.colorOnSurface));
            download.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(download, com.google.android.material.R.attr.colorOnSurface)));
            download.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
        } else {
            download.setBackgroundTintList(ColorStateList.valueOf(gold));
            int onGold = ContextCompat.getColor(requireContext(), R.color.corona_amber_on);
            download.setTextColor(onGold);
            download.setIconTint(ColorStateList.valueOf(onGold));
            download.setStrokeWidth(0);
        }
    }

    private void setHookDotColor(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        hookDot.setBackground(dot);
    }

    private void probeHookStatus(String pkg) {
        if (getContext() == null) return;
        final int gen = ++hookProbeGeneration;
        applyStatus(Status.CHECKING);
        ModuleHookProbe.probe(requireContext(), pkg, active -> {
            if (!isAdded() || gen != hookProbeGeneration) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || gen != hookProbeGeneration) return;
                applyStatus(active ? Status.ACTIVE : Status.INACTIVE);
            });
        });
    }

    private void bindPackageActions(PackageManager pm, String pkg) {
        activePackage = pkg;

        String versionName = "";
        try {
            versionName = pm.getPackageInfo(pkg, 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        variantText.setText(CommonUtils.getVariantLabel(pkg));
        versionText.setText(versionName == null ? "" : versionName);
        versionChip.setContentDescription(getString(R.string.instagram_info) + ": "
                + CommonUtils.getVariantLabel(pkg) + " " + versionName);

        versionChip.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        launchButton.setOnClickListener(v -> {
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(getActivity(), getString(R.string.not_installed_instagram), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupContributors(View rootView) {
        LinearLayout contributorsContainer = rootView.findViewById(R.id.contributors_container);
        ChipGroup thanksGroup = rootView.findViewById(R.id.special_thanks_container);

        List<Contributor> contributors = Arrays.asList(
                new Contributor("ReSo7200", "https://github.com/ReSo7200", "https://linkedin.com/in/abdalhaleem-altamimi", null),
                new Contributor("swakwork", "https://github.com/swakwork", null, null),
                new Contributor("isma3iloiso", "https://github.com/isma3iloiso", null, null),
                new Contributor("Placeholder6", "https://github.com/Placeholder6", null, null),
                new Contributor("frknkrc44", "https://github.com/frknkrc44", null, null),
                new Contributor("BrianML", "https://github.com/brianml31", null, "https://t.me/instamoon_channel"),
                new Contributor("silvzr", "https://github.com/silvzr", null, null),
                new Contributor("oct", "https://github.com/oct888", null, null),
                new Contributor("HalfManBear", "https://github.com/halfmanbear", null, null),
                new Contributor("ar5to", "https://github.com/ar5to", null, "https://t.me/ar5to"),
                new Contributor("particle-box", "https://github.com/particle-box", null, null),
                new Contributor("rsr", null, null, "https://t.me/rsr1337")
        );

        List<Contributor> specialThanks = Arrays.asList(
                new Contributor("xHookman", "https://github.com/xHookman", null, null),
                new Contributor("Bluepapilte", null, null, "https://t.me/instasmashrepo"),
                new Contributor("BdrcnAYYDIN", null, null, "https://t.me/BdrcnAYYDIN"),
                new Contributor("Amàzing World", null, null, null)
        );

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Contributor c : contributors) {
            View item = inflater.inflate(R.layout.item_contributor_avatar, contributorsContainer, false);
            ((TextView) item.findViewById(R.id.contributor_initial))
                    .setText(c.name().substring(0, 1).toUpperCase(Locale.ROOT));
            ((TextView) item.findViewById(R.id.contributor_name)).setText(c.name());
            item.setContentDescription(c.name());
            bindLink(item, primaryLink(c));
            contributorsContainer.addView(item);
        }

        int cardColor = ContextCompat.getColor(requireContext(), R.color.ie_card);
        int strokeColor = ContextCompat.getColor(requireContext(), R.color.ie_card_stroke);
        int textColor = MaterialColors.getColor(thanksGroup, com.google.android.material.R.attr.colorOnSurface);
        float density = getResources().getDisplayMetrics().density;
        for (Contributor c : specialThanks) {
            Chip chip = new Chip(requireContext());
            chip.setText(c.name());
            chip.setTextSize(13);
            chip.setTextColor(textColor);
            chip.setTextDirection(View.TEXT_DIRECTION_LTR);
            chip.setChipBackgroundColor(ColorStateList.valueOf(cardColor));
            chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor));
            chip.setChipStrokeWidth(density);
            chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().withCornerSize(18 * density));
            chip.setChipMinHeight(36 * density);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setTextStartPadding(6 * density);
            chip.setTextEndPadding(6 * density);
            String link = primaryLink(c);
            chip.setClickable(link != null);
            chip.setFocusable(link != null);
            if (link != null) chip.setOnClickListener(v -> openLink(link));
            thanksGroup.addView(chip);
        }
    }

    private static String primaryLink(Contributor c) {
        if (c.githubUrl() != null) return c.githubUrl();
        if (c.telegramUrl() != null) return c.telegramUrl();
        return c.linkedinUrl();
    }

    private void bindLink(View view, String url) {
        view.setClickable(url != null);
        view.setFocusable(url != null);
        if (url != null) view.setOnClickListener(v -> openLink(url));
    }

    private void showDetectedVersionsDialog(PackageManager pm) {
        String[] labels = new String[installedPackages.size()];
        for (int i = 0; i < installedPackages.size(); i++) {
            String pkg = installedPackages.get(i);
            String version = "?";
            try { version = pm.getPackageInfo(pkg, 0).versionName; }
            catch (PackageManager.NameNotFoundException ignored) { }
            labels[i] = CommonUtils.getVariantLabel(pkg) + "  —  v" + version;
        }

        int currentIndex = installedPackages.indexOf(activePackage);
        final int[] selectedIndex = {currentIndex};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Detected versions")
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton("Use this", (dialog, which) -> {
                    String chosen = installedPackages.get(selectedIndex[0]);
                    if (!chosen.equals(activePackage)) {
                        bindPackageActions(pm, chosen);
                        probeHookStatus(chosen);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openLink(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
