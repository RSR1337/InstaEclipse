package ps.reso.instaeclipse.mods.ghost;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class GhostChannelMarkAsReadHook {

    private static final String CHANNEL_TAG = "ie_channel_seen";

    private static volatile int sCachedSeenStateId = 0;
    private static volatile int sCachedHeaderButtonsId = 0;

    public void install(ClassLoader classLoader) {
        try {
            ViewAttachDispatcher.add(view -> {
                if (!FeatureFlags.isGhostSeen) return;

                Context context = view.getContext();

                if (sCachedSeenStateId == 0) {
                    @SuppressLint("DiscouragedApi")
                    int id = context.getResources().getIdentifier(
                            "seen_state_text", "id", context.getPackageName());
                    sCachedSeenStateId = id;
                }

                if (sCachedSeenStateId == 0 || view.getId() != sCachedSeenStateId) return;
                if (!(view instanceof TextView seenTextView)) return;

                if (sCachedHeaderButtonsId == 0) {
                    @SuppressLint("DiscouragedApi")
                    int id = context.getResources().getIdentifier(
                            "header_right_buttons", "id", context.getPackageName());
                    sCachedHeaderButtonsId = id;
                }

                if (sCachedHeaderButtonsId != 0) {
                    View container = view.getRootView().findViewById(sCachedHeaderButtonsId);
                    if (container instanceof ViewGroup viewGroup) {
                        for (int i = 0; i < viewGroup.getChildCount(); i++) {
                            CharSequence description = viewGroup.getChildAt(i).getContentDescription();
                            if (description != null) {
                                String descStr = description.toString().toLowerCase();
                                if (descStr.contains("audio call") ||
                                        descStr.contains("video call") ||
                                        descStr.contains("blend")) {
                                    return;
                                }
                            }
                        }
                    }
                }
                updateChannelSeen(seenTextView);
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse): Channel seen hook failed: " + t.getMessage());
        }
    }

    private void updateChannelSeen(TextView textView) {

        if (textView.getTag() != null && textView.getTag().equals(CHANNEL_TAG)) return;
        textView.setTag(CHANNEL_TAG);

        textView.setTextColor(Color.CYAN);

        textView.setOnClickListener(v -> {
            triggerChannelSeen(textView);
        });

        String currentText = textView.getText().toString();
        if (!currentText.contains("👻")) {
            textView.setText(currentText + " 👻");
        }
    }

    private void triggerChannelSeen(View view) {
        try {
            Context ctx = view.getContext();
            @SuppressLint("DiscouragedApi")
            int messageListId = ctx.getResources().getIdentifier("message_list", "id", ctx.getPackageName());

            View root = view.getRootView();
            View messageList = root.findViewById(messageListId);

            if (messageList instanceof ViewGroup group) {
                group.scrollBy(0, 100_000);

                FeatureFlags.isGhostSeen = false;
                group.scrollBy(0, -300);

                view.postDelayed(() -> {
                    group.scrollBy(0, 300);
                    FeatureFlags.isGhostSeen = true;
                    Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_channel_seen_sent), Toast.LENGTH_SHORT).show();
                }, 400);
            }
        } catch (Exception e) {
            FeatureFlags.isGhostSeen = true;
        }
    }
}
