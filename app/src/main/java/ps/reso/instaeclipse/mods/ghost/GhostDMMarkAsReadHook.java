package ps.reso.instaeclipse.mods.ghost;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.XModuleResources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class GhostDMMarkAsReadHook {

    private static final String GHOST_BTN_TAG = "ie_ghost_seen_btn";
    private final String moduleSourceDir;

    public GhostDMMarkAsReadHook(String moduleSourceDir) {
        this.moduleSourceDir = moduleSourceDir;
    }

    private static volatile int sCachedContainerId = 0;

    public void install(ClassLoader classLoader) {
        try {
            ViewAttachDispatcher.add(view -> {
                if (!FeatureFlags.isGhostSeen) return;

                if (sCachedContainerId == 0) {
                    @SuppressLint("DiscouragedApi")
                    int id = view.getContext().getResources().getIdentifier(
                            "row_thread_composer_buttons_container", "id",
                            view.getContext().getPackageName());
                    sCachedContainerId = id;
                }

                if (sCachedContainerId == 0 || view.getId() != sCachedContainerId) return;
                if (!(view.getParent() instanceof ViewGroup parent)) return;
                injectIndependentButton(parent, view);
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse): Ghost hook failed: " + t.getMessage());
        }
    }

    private void injectIndependentButton(ViewGroup parent, View originalContainer) {
        if (parent.findViewWithTag(GHOST_BTN_TAG) != null) return;

        Context ctx = parent.getContext();
        ImageButton ghostBtn = new ImageButton(ctx);
        ghostBtn.setTag(GHOST_BTN_TAG);

        try {
            @SuppressLint("UseCompatLoadingForDrawables") Drawable icon = XModuleResources.createInstance(moduleSourceDir, null)
                    .getDrawable(R.drawable.ic_eye, null);
            ghostBtn.setImageDrawable(icon);
        } catch (Exception e) {
            ghostBtn.setImageResource(android.R.drawable.ic_menu_view);
            ghostBtn.setColorFilter(Color.WHITE);
        }
        ghostBtn.setBackground(null);

        int size = dp(ctx, 35);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);

        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        lp.setMargins(dp(ctx, 5), 25, 0, 0);

        ghostBtn.setLayoutParams(lp);
        ghostBtn.setOnClickListener(v -> triggerSeenLogic(parent));

        parent.post(() -> {
            parent.addView(ghostBtn, 3);
        });
    }

    private void triggerSeenLogic(View view) {
        try {
            Context ctx = view.getContext();
            @SuppressLint("DiscouragedApi")
            int messageListId = ctx.getResources().getIdentifier("message_list", "id", ctx.getPackageName());

            View root = view.getRootView();
            View messageList = root.findViewById(messageListId);

            if (messageList instanceof ViewGroup group) {

                group.scrollBy(0, 100_000);

                FeatureFlags.isGhostSeen = false;
                group.scrollBy(0, -200);

                view.postDelayed(() -> {
                    group.scrollBy(0, 200);
                    FeatureFlags.isGhostSeen = true;
                    Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_seen_sent), Toast.LENGTH_SHORT).show();
                }, 300);
            }
        } catch (Exception e) {
            FeatureFlags.isGhostSeen = true;
        }
    }

    private int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
