package ps.reso.instaeclipse.mods.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.XModuleResources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class VoiceDownloadHook {

    private static final String[] AUDIO_INFO_CLASSES = {
            "com.instagram.api.schemas.AudioInfo",
            "com.instagram.api.schemas.ImmutablePandoAudioInfo"
    };

    private static final long CAPTURE_FRESHNESS_MS = 30_000;

    private static volatile String lastCapturedUrl;
    private static volatile long lastCapturedAt;

    private static volatile String lastDownloadedUrl;
    private static volatile long lastDownloadedAt;

    private static final String BTN_TAG = "ie_voice_dl_btn";
    private static volatile int sContainerId = 0;

    private final String moduleSourceDir;

    public VoiceDownloadHook(String moduleSourceDir) {
        this.moduleSourceDir = moduleSourceDir;
    }

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        installUrlCapture(classLoader);
        installIconInjection(classLoader);
    }

    private void installUrlCapture(ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableVoiceDownload) return;
                try {
                    Object result = param.getResult();
                    if (!(result instanceof String)) return;
                    String url = (String) result;
                    if (!url.startsWith("http://") && !url.startsWith("https://")) return;
                    lastCapturedUrl = url;
                    lastCapturedAt = System.currentTimeMillis();
                } catch (Throwable ignored) {}
            }
        };

        boolean hookedAny = false;
        for (String cn : AUDIO_INFO_CLASSES) {
            try {
                Class<?> cls = classLoader.loadClass(cn);
                Method m = cls.getDeclaredMethod("BQO");
                XposedBridge.hookMethod(m, hook);
                ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Hooked URL capture: " + cn + ".BQO()");
                hookedAny = true;
            } catch (Throwable ignored) {}
        }
        if (hookedAny) {
            FeatureStatusTracker.setHooked("VoiceDownload");
        } else {
            ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ AudioInfo.BQO() not found on this build");
        }
    }

    private void installIconInjection(ClassLoader classLoader) {
        XC_MethodHook attachHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableVoiceDownload) return;
                if (!(param.thisObject instanceof View view)) return;
                try {
                    Context ctx = view.getContext();
                    if (ctx == null) return;
                    if (sContainerId == 0) {
                        @SuppressLint("DiscouragedApi")
                        int id = ctx.getResources().getIdentifier(
                                "voice_message_controls_button_container", "id", ctx.getPackageName());
                        sContainerId = id;
                    }
                    if (sContainerId == 0 || view.getId() != sContainerId) return;
                    if (!(view instanceof LinearLayout container)) return;

                    String url = lastCapturedUrl;
                    if (url == null || System.currentTimeMillis() - lastCapturedAt > CAPTURE_FRESHNESS_MS) return;

                    container.post(() -> injectOrUpdateDownloadIcon(container, url));
                } catch (Throwable ignored) {}
            }
        };

        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", attachHook);
            ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Hooked icon injection");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ Icon injection hook: " + t.getMessage());
        }
    }

    private void injectOrUpdateDownloadIcon(LinearLayout container, String url) {
        Context ctx = container.getContext();
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        View existing = container.findViewWithTag(BTN_TAG);
        if (existing != null) {
            existing.setOnClickListener(v -> onDownloadIconClicked(ctx, url));
            return;
        }

        ImageButton btn = new ImageButton(ctx);
        btn.setTag(BTN_TAG);
        try {
            @SuppressLint("UseCompatLoadingForDrawables")
            Drawable icon = XModuleResources.createInstance(moduleSourceDir, null)
                    .getDrawable(R.drawable.ic_download, null);
            btn.setImageDrawable(icon);
        } catch (Throwable t) {
            btn.setImageResource(android.R.drawable.stat_sys_download);
            btn.setColorFilter(Color.WHITE);
        }

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Color.parseColor("#66000000"));
        btn.setBackground(badgeBg);

        int size = dp(ctx, 26);
        int pad = dp(ctx, 4);
        btn.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginStart(dp(ctx, 6));
        lp.topMargin = dp(ctx, 4);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> onDownloadIconClicked(ctx, url));

        container.addView(btn);
    }

    private void onDownloadIconClicked(Context ctx, String url) {
        startVoiceDownload(ctx, url, null);
        try {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    public static void startVoiceDownload(Context context, String audioUrl, String senderUsername) {
        if (audioUrl == null || audioUrl.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (audioUrl.equals(lastDownloadedUrl) && (now - lastDownloadedAt) < 3000) return;
        lastDownloadedUrl = audioUrl;
        lastDownloadedAt = now;
        try {
            String pathOnly = audioUrl.contains("?") ? audioUrl.substring(0, audioUrl.indexOf('?')) : audioUrl;
            String stableId = Integer.toHexString(pathOnly.hashCode());
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            Intent intent = new Intent();
            intent.setClassName("ps.reso.instaeclipse",
                    "ps.reso.instaeclipse.mods.media.DownloadSaveService");
            intent.putExtra("url", audioUrl);
            intent.putExtra("filename", "voice_" + (senderUsername != null ? senderUsername : "dm") + "_" + stableId + "_" + timestamp + ".m4a");
            intent.putExtra("mimeType", "audio/mp4");
            intent.putExtra("username", senderUsername != null ? senderUsername : "direct");
            context.startForegroundService(intent);
            ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Delegated voice download to DownloadSaveService");
        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ Failed to start download: " + e.getMessage());
        }
    }
}
