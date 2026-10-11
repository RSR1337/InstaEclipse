package ps.reso.instaeclipse.fragments;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.regex.Pattern;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.ui.EclipseRingDrawable;
import ps.reso.instaeclipse.utils.version.VersionCheckResult;
import ps.reso.instaeclipse.utils.version.VersionCheckUtility;

public class HelpFragment extends Fragment {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+[.)]\\s*");
    private static final Pattern LEADING_DASH = Pattern.compile("^[\\s\\-–—•]+");
    private static final int[] FAQS = {R.string.faq_1, R.string.faq_2, R.string.faq_3, R.string.faq_4, R.string.faq_5, R.string.faq_6};

    private View updateRow;
    private View updateDot;
    private TextView updateText;
    private String pendingUpdateUrl;
    private int openFaq = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help, container, false);

        float density = getResources().getDisplayMetrics().density;
        ((ImageView) view.findViewById(R.id.help_version_ring)).setImageDrawable(new EclipseRingDrawable(
                ContextCompat.getColor(requireContext(), R.color.ie_ring_core),
                ContextCompat.getColor(requireContext(), R.color.corona_amber),
                2f * density, 12 * density, true)
                .setLogo(ContextCompat.getDrawable(requireContext(), R.drawable.ic_logo)));

        ((TextView) view.findViewById(R.id.help_version_text))
                .setText(getString(R.string.help_version_format, VersionCheckUtility.currentVersion()));
        updateRow = view.findViewById(R.id.help_update_row);
        updateDot = view.findViewById(R.id.help_update_dot);
        updateText = view.findViewById(R.id.help_update_text);
        updateRow.setOnClickListener(v -> {
            if (pendingUpdateUrl != null) openLink(pendingUpdateUrl);
        });

        LinearLayout steps = view.findViewById(R.id.help_steps_container);
        addStep(inflater, steps, 1, R.string.help_step1_title, R.string.help_step1_desc);
        addStep(inflater, steps, 2, R.string.help_step2_title, R.string.help_step2_desc);
        addStep(inflater, steps, 3, R.string.help_step3_title, R.string.help_step3_desc);

        buildFaq(view.findViewById(R.id.help_faq_container));

        LinearLayout links = view.findViewById(R.id.help_links_container);
        addLink(inflater, links, R.string.help_with_root, R.string.help_lsposed_link,
                "https://github.com/JingMatrix/LSPosed/releases/latest");
        addLink(inflater, links, R.string.help_without_root, R.string.help_lspatch_link,
                "https://github.com/JingMatrix/LSPatch/releases/latest");
        addLink(inflater, links, R.string.help_without_root, R.string.help_lspatch_guide,
                "https://t.me/instaEclipse_discussion/158/5149");

        view.findViewById(R.id.github_card).setOnClickListener(v ->
                openLink("https://github.com/ReSo7200/InstaEclipse"));
        view.findViewById(R.id.telegram_card).setOnClickListener(v ->
                openLink("https://t.me/InstaEclipse"));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (updateText == null) return;
        pendingUpdateUrl = null;
        updateRow.setClickable(false);
        updateText.setText(R.string.help_checking_updates);
        setDot(R.color.corona_amber);
        VersionCheckUtility.checkForUpdatesSilently(this::applyUpdateResult);
    }

    private void applyUpdateResult(VersionCheckResult result) {
        if (!isAdded() || updateText == null) return;
        if (result.updateAvailable && result.latestVersion != null) {
            pendingUpdateUrl = result.updateUrl;
            updateText.setText(getString(R.string.help_update_available, result.latestVersion));
            updateText.setTextColor(ContextCompat.getColor(requireContext(), R.color.ie_accent_text));
            updateRow.setClickable(true);
            setDot(R.color.corona_amber);
        } else {
            pendingUpdateUrl = null;
            updateText.setText(R.string.help_up_to_date);
            updateRow.setClickable(false);
            setDot(R.color.ie_hook_ok);
        }
    }

    private void setDot(int colorRes) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(requireContext(), colorRes));
        updateDot.setBackground(dot);
    }

    private void addStep(LayoutInflater inflater, LinearLayout parent, int number, int titleRes, int descRes) {
        View step = inflater.inflate(R.layout.item_help_step, parent, false);
        ((TextView) step.findViewById(R.id.step_number)).setText(String.valueOf(number));
        ((TextView) step.findViewById(R.id.step_title)).setText(titleRes);
        ((TextView) step.findViewById(R.id.step_desc)).setText(descRes);
        parent.addView(step);
    }

    private void addLink(LayoutInflater inflater, LinearLayout parent, int labelRes, int titleRes, String url) {
        View row = inflater.inflate(R.layout.item_help_link, parent, false);
        ((TextView) row.findViewById(R.id.link_label)).setText(labelRes);
        ((TextView) row.findViewById(R.id.link_title)).setText(titleRes);
        row.setOnClickListener(v -> openLink(url));
        parent.addView(row);
    }

    private void buildFaq(LinearLayout container) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        float big = 22 * getResources().getDisplayMetrics().density;
        float small = 4 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < FAQS.length; i++) {
            String[] qa = splitFaq(getText(FAQS[i]).toString());
            View item = inflater.inflate(R.layout.item_help_faq, container, false);
            MaterialCardView card = (MaterialCardView) item;
            ShapeAppearanceModel.Builder shape = card.getShapeAppearanceModel().toBuilder();
            if (i == 0) {
                shape.setTopLeftCornerSize(big).setTopRightCornerSize(big)
                        .setBottomLeftCornerSize(small).setBottomRightCornerSize(small);
            } else if (i == FAQS.length - 1) {
                shape.setTopLeftCornerSize(small).setTopRightCornerSize(small)
                        .setBottomLeftCornerSize(big).setBottomRightCornerSize(big);
            } else {
                shape.setAllCornerSizes(small);
            }
            card.setShapeAppearanceModel(shape.build());

            TextView question = item.findViewById(R.id.faq_question);
            TextView answer = item.findViewById(R.id.faq_answer);
            ImageView chevron = item.findViewById(R.id.faq_chevron);
            View header = item.findViewById(R.id.faq_header);
            question.setText(qa[0]);
            answer.setText(qa[1]);

            final int index = i;
            boolean open = openFaq == index;
            answer.setVisibility(open && !qa[1].isEmpty() ? View.VISIBLE : View.GONE);
            chevron.setRotation(open ? 180f : 0f);
            ViewCompat.setStateDescription(header, getString(open ? R.string.faq_expanded : R.string.faq_collapsed));
            header.setOnClickListener(v -> {
                openFaq = openFaq == index ? -1 : index;
                buildFaq(container);
            });
            container.addView(item);
        }
    }

    private static String[] splitFaq(String raw) {
        String text = LEADING_NUMBER.matcher(raw.trim()).replaceFirst("");
        int cut = text.indexOf('\n');
        if (cut < 0) {
            int q = indexOfAny(text, '?', '؟');
            cut = q < 0 ? -1 : q + 1;
        }
        if (cut <= 0 || cut >= text.length()) return new String[]{text.trim(), ""};
        String question = text.substring(0, cut).trim();
        String answer = LEADING_DASH.matcher(text.substring(cut).trim()).replaceFirst("").trim();
        return new String[]{question, answer};
    }

    private static int indexOfAny(String text, char a, char b) {
        int ia = text.indexOf(a);
        int ib = text.indexOf(b);
        if (ia < 0) return ib;
        if (ib < 0) return ia;
        return Math.min(ia, ib);
    }

    private void openLink(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
