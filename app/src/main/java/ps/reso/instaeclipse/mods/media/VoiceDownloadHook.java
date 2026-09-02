package ps.reso.instaeclipse.mods.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.XModuleResources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.luckypray.dexkit.DexKitBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.core.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;
import ps.reso.instaeclipse.utils.users.UserUtils;

public class VoiceDownloadHook {

    private static final String[] AUDIO_INFO_CLASSES = {
            "com.instagram.api.schemas.AudioInfo",
            "com.instagram.api.schemas.AudioInfoIntf",
            "com.instagram.api.schemas.ImmutablePandoAudioInfo",
            "com.instagram.api.schemas.DirectAudioFallbackUrl",
            "com.instagram.api.schemas.DirectAudioFallbackUrlImpl"
    };

    private static final String[] AUDIO_URL_METHODS = {
            "BQp", "BQO", "Bfd", "C79"
    };

    private static final long CAPTURE_FRESHNESS_MS = 30_000;

    private static volatile String lastCapturedUrl;
    private static volatile long lastCapturedAt;

    private static volatile String lastDownloadedUrl;
    private static volatile long lastDownloadedAt;

    private static final String BTN_TAG = "ie_voice_dl_btn";
    private static volatile int sContainerId = 0;

    private final String moduleSourceDir;
    private static Class<?> userClass;

    public VoiceDownloadHook(String moduleSourceDir) {
        this.moduleSourceDir = moduleSourceDir;
    }

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            userClass = classLoader.loadClass("com.instagram.user.model.User");
        } catch (Throwable ignored) {}
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
                boolean classHooked = false;
                for (String methodName : AUDIO_URL_METHODS) {
                    try {
                        Method m = cls.getDeclaredMethod(methodName);
                        if (m.getReturnType() != String.class) continue;
                        XposedBridge.hookMethod(m, hook);
                        ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Hooked URL capture: " + cn + "." + methodName + "()");
                        classHooked = true;
                        hookedAny = true;
                    } catch (NoSuchMethodException ignored) {}
                }
                if (!classHooked) {
                    for (Method m : cls.getDeclaredMethods()) {
                        if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
                        XposedBridge.hookMethod(m, hook);
                        ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Hooked URL capture: " + cn + "." + m.getName() + "()");
                        hookedAny = true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (hookedAny) {
            FeatureStatusTracker.setHooked("VoiceDownload");
        } else {
            ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ AudioInfo URL getter not found on this build");
        }
    }

    private void installIconInjection(ClassLoader classLoader) {
        try {
            ViewAttachDispatcher.add(view -> {
                if (!FeatureFlags.enableVoiceDownload) return;
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
            });
            ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Hooked icon injection");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ Icon injection hook: " + t.getMessage());
        }
    }

    private void injectOrUpdateDownloadIcon(LinearLayout container, String url) {
        Context ctx = container.getContext();
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        String username = resolveSenderUsername(container);

        View existing = container.findViewWithTag(BTN_TAG);
        if (existing != null) {
            existing.setOnClickListener(v -> onDownloadIconClicked(ctx, url, username));
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
        btn.setOnClickListener(v -> onDownloadIconClicked(ctx, url, username));

        container.addView(btn);
    }

    private void onDownloadIconClicked(Context ctx, String url, String username) {
        startVoiceDownload(ctx, url, username);
        try {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_downloading), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private static String resolveSenderUsername(View start) {
        boolean outgoing = isOutgoingMessage(start);
        boolean preferListBased = !outgoing;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        View v = start;
        for (int i = 0; i < 15 && v != null; i++) {
            Object onClick = getOnClickListener(v);
            if (onClick != null) {
                String u = findUsernameDeep(onClick, visited, 0, preferListBased);
                if (u != null) return u;
                dumpFieldsOnce(onClick);
            }
            Object onLongClick = getOnLongClickListener(v);
            if (onLongClick != null) {
                String u = findUsernameDeep(onLongClick, visited, 0, preferListBased);
                if (u != null) return u;
                dumpFieldsOnce(onLongClick);
            }
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        ModuleLog.line("(InstaEclipse | VoiceDownload): ⚠️ sender username not found via listeners");
        return null;
    }

    private static boolean isOutgoingMessage(View start) {
        View bubble = findAncestorById(start, "voice_message_container");
        if (bubble == null) bubble = findAncestorById(start, "message_content_voice_bubble_container");
        if (bubble == null) bubble = start;
        try {
            int[] loc = new int[2];
            bubble.getLocationOnScreen(loc);
            int screenWidth = bubble.getResources().getDisplayMetrics().widthPixels;
            int center = loc[0] + bubble.getWidth() / 2;
            boolean rtl = bubble.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            boolean nearRightHalf = center > screenWidth / 2;
            return rtl == nearRightHalf;
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | VoiceDownload): direction check failed: " + t);
            return false;
        }
    }

    @SuppressLint("DiscouragedApi")
    private static View findAncestorById(View start, String idName) {
        View v = start;
        for (int i = 0; i < 15 && v != null; i++) {
            try {
                int id = v.getContext().getResources().getIdentifier(idName, "id", v.getContext().getPackageName());
                if (id != 0 && v.getId() == id) return v;
            } catch (Throwable ignored) {}
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static Object getOnClickListener(View view) {
        try {
            Field liField = View.class.getDeclaredField("mListenerInfo");
            liField.setAccessible(true);
            Object li = liField.get(view);
            if (li == null) return null;
            Field clField = li.getClass().getDeclaredField("mOnClickListener");
            clField.setAccessible(true);
            return clField.get(li);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object getOnLongClickListener(View view) {
        try {
            Field liField = View.class.getDeclaredField("mListenerInfo");
            liField.setAccessible(true);
            Object li = liField.get(view);
            if (li == null) return null;
            Field clField = li.getClass().getDeclaredField("mOnLongClickListener");
            clField.setAccessible(true);
            return clField.get(li);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void dumpFieldsOnce(Object obj) {
        dumpFieldsRecursive(obj, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void dumpFieldsRecursive(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > 3 || !visited.add(obj)) return;
        String cn = obj.getClass().getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) return;
        String indent = "  ".repeat(depth + 1);
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    ModuleLog.line("(IE|Voice|Diag)" + indent + "[d" + depth + "] " + f.getName() + ":"
                            + f.getType().getName() + " = " + describeValue(val));
                    if (val != null) {
                        String vcn = val.getClass().getName();
                        if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.") || vcn.startsWith("com.facebook.")) {
                            dumpFieldsRecursive(val, depth + 1, visited);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static String describeValue(Object val) {
        if (val == null) return "null";
        if (val instanceof String s) return "\"" + s + "\"";
        return val.getClass().getName();
    }

    private static String findUsernameDeep(Object obj, Set<Object> visited, int depth, boolean preferListBased) {
        List<String[]> candidates = new ArrayList<>();
        collectUsernameCandidates(obj, visited, depth, "root", candidates);
        if (candidates.isEmpty()) return null;

        for (String[] c : candidates) {
            if (c[0].contains("[") == preferListBased) return c[1];
        }
        return candidates.get(0)[1];
    }

    private static void collectUsernameCandidates(Object obj, Set<Object> visited, int depth,
                                                   String path, List<String[]> out) {
        if (obj == null || depth > 6 || !visited.add(obj) || out.size() >= 10) return;
        if (userClass != null && userClass.isInstance(obj)) {
            String u = UserUtils.callUsernameGetter(obj);
            if (u != null) out.add(new String[]{path, u});
            return;
        }
        if (obj instanceof java.util.Collection<?> col) {
            int idx = 0;
            for (Object item : col) {
                collectUsernameCandidates(item, visited, depth + 1, path + "[" + (idx++) + "]", out);
                if (out.size() >= 10) return;
            }
            return;
        }
        if (obj instanceof Object[] arr) {
            for (int i = 0; i < arr.length; i++) {
                collectUsernameCandidates(arr[i], visited, depth + 1, path + "[" + i + "]", out);
                if (out.size() >= 10) return;
            }
            return;
        }

        String cn = obj.getClass().getName();
        if (!cn.startsWith("X.") && !cn.startsWith("com.instagram.") && !cn.startsWith("com.facebook.")) return;

        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().isPrimitive()) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (val == null) continue;
                    String childPath = path + "." + f.getName();
                    if (userClass != null && userClass.isInstance(val)) {
                        String u = UserUtils.callUsernameGetter(val);
                        if (u != null) { out.add(new String[]{childPath, u}); continue; }
                    }
                    if (val instanceof java.util.Collection<?> || val instanceof Object[]) {
                        collectUsernameCandidates(val, visited, depth + 1, childPath, out);
                        continue;
                    }
                    String vcn = val.getClass().getName();
                    if (vcn.startsWith("X.") || vcn.startsWith("com.instagram.") || vcn.startsWith("com.facebook.")) {
                        collectUsernameCandidates(val, visited, depth + 1, childPath, out);
                    }
                } catch (Throwable ignored) {}
                if (out.size() >= 10) return;
            }
            cls = cls.getSuperclass();
        }
    }

    public static void startVoiceDownload(Context context, String audioUrl, String senderUsername) {
        if (audioUrl == null || audioUrl.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (audioUrl.equals(lastDownloadedUrl) && (now - lastDownloadedAt) < 3000) return;
        lastDownloadedUrl = audioUrl;
        lastDownloadedAt = now;

        String pathOnly = audioUrl.contains("?") ? audioUrl.substring(0, audioUrl.indexOf('?')) : audioUrl;
        String stableId = Integer.toHexString(pathOnly.hashCode());
        String username = senderUsername != null ? senderUsername : "direct";
        String filename = FeedVideoDownloadHook.buildFilename(username, "voice", stableId, false, -1, ".m4a");

        FeedVideoDownloadHook.executor.submit(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(context, audioUrl, filename, false, username, "audio/mp4");
                if (!delegated) {
                    FeedVideoDownloadHook.mainHandler.post(() ->
                            Toast.makeText(context, I18n.t(context, R.string.ig_toast_saved), Toast.LENGTH_SHORT).show());
                }
                ModuleLog.line("(InstaEclipse | VoiceDownload): ✅ Voice download complete: " + filename);
            } catch (Throwable e) {
                ModuleLog.line("(InstaEclipse | VoiceDownload): ❌ Failed to download: " + e.getMessage());
                FeedVideoDownloadHook.mainHandler.post(() ->
                        Toast.makeText(context, I18n.t(context, R.string.ig_toast_download_failed, e.getMessage()), Toast.LENGTH_SHORT).show());
            }
        });
    }
}
