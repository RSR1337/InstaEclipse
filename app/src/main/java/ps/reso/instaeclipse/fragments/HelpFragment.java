package ps.reso.instaeclipse.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.version.VersionCheckResult;
import ps.reso.instaeclipse.utils.version.VersionCheckUtility;

public class HelpFragment extends Fragment {

    private TextView versionText;
    private Chip updateChip;
    private String pendingUpdateUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help, container, false);

        versionText = view.findViewById(R.id.help_version_text);
        updateChip = view.findViewById(R.id.help_update_chip);
        versionText.setText(getString(R.string.help_version_format, "v" + VersionCheckUtility.currentVersion()));
        updateChip.setOnClickListener(v -> {
            if (pendingUpdateUrl != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pendingUpdateUrl)));
            }
        });

        MaterialCardView githubCard = view.findViewById(R.id.github_card);
        MaterialCardView telegramCard = view.findViewById(R.id.telegram_card);
        TextView moduleNotWorkingDescription = view.findViewById(R.id.module_not_working_description);

        moduleNotWorkingDescription.setText(Html.fromHtml(getString(R.string.module_not_working_description), Html.FROM_HTML_MODE_LEGACY));
        moduleNotWorkingDescription.setMovementMethod(LinkMovementMethod.getInstance());
        moduleNotWorkingDescription.setLinkTextColor(ContextCompat.getColor(requireContext(), R.color.corona_amber));

        githubCard.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ReSo7200/InstaEclipse"))));
        telegramCard.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/InstaEclipse"))));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (updateChip == null) return;
        updateChip.setVisibility(View.VISIBLE);
        updateChip.setText(R.string.help_checking_updates);
        updateChip.setClickable(false);
        VersionCheckUtility.checkForUpdatesSilently(this::applyUpdateResult);
    }

    private void applyUpdateResult(VersionCheckResult result) {
        if (!isAdded() || updateChip == null) return;
        if (result.updateAvailable && result.latestVersion != null) {
            pendingUpdateUrl = result.updateUrl;
            updateChip.setText(getString(R.string.help_update_available, result.latestVersion));
            updateChip.setClickable(true);
            updateChip.setVisibility(View.VISIBLE);
        } else {
            pendingUpdateUrl = null;
            updateChip.setText(R.string.help_up_to_date);
            updateChip.setClickable(false);
            updateChip.setVisibility(View.VISIBLE);
        }
    }
}
