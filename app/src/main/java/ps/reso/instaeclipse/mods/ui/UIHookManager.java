package ps.reso.instaeclipse.mods.ui;

import static org.luckypray.dexkit.query.FindMethod.create;
import static ps.reso.instaeclipse.mods.ghost.ui.GhostEmojiManager.addGhostEmojiNextToInbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.luckypray.dexkit.result.MethodData;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.mods.devops.config.ConfigManager;
import ps.reso.instaeclipse.mods.ui.utils.BottomSheetHookUtil;
import ps.reso.instaeclipse.mods.ui.utils.VibrationUtil;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.dialog.DialogUtils;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.ghost.GhostModeUtils;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.toast.CustomToast;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class UIHookManager {

    private static final String[] MAIN_ACTIVITIES = {
            "com.instagram.mainactivity.InstagramMainActivity",
            "com.instagram.mainactivity.LauncherActivity"
    };

    @SuppressLint("StaticFieldLeak")
    private static Activity currentActivity;
    public static Activity getCurrentActivity() {
        return currentActivity;
    }

    private static final int TAG_SEARCH_LISTENER_PENDING = "ie_search_listener_pending".hashCode();
    private static final int TAG_SEARCH_WIRING_DONE = "ie_search_wiring_done".hashCode();

    private static volatile int sSearchTabId = 0;
    private static volatile int sActionBarEndId = 0;
    private static volatile int sInboxButtonId = 0;
    private static volatile int sDirectTabId = 0;

    @SuppressLint("DiscouragedApi")
    private static void ensureIdsCached(Activity activity) {
        if (sSearchTabId != 0 && sActionBarEndId != 0
                && sInboxButtonId != 0 && sDirectTabId != 0) return;
        String pkg = activity.getPackageName();
        android.content.res.Resources res = activity.getResources();
        if (sSearchTabId == 0)
            sSearchTabId = res.getIdentifier("search_tab", "id", pkg);
        if (sActionBarEndId == 0)
            sActionBarEndId = res.getIdentifier("action_bar_end_action_buttons", "id", pkg);
        if (sInboxButtonId == 0)
            sInboxButtonId = res.getIdentifier("action_bar_inbox_button", "id", pkg);
        if (sDirectTabId == 0)
            sDirectTabId = res.getIdentifier("direct_tab", "id", pkg);
    }

    public static void setupHooks(Activity activity) {

        addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());

        ensureIdsCached(activity);

        boolean anySearchFound = false;
        if (sSearchTabId != 0) {
            View v = activity.findViewById(sSearchTabId);
            if (v != null) { processSearchView(activity, v, "search_tab"); anySearchFound = true; }
        }
        if (!anySearchFound && sActionBarEndId != 0) {
            View v = activity.findViewById(sActionBarEndId);
            if (v != null) { processSearchView(activity, v, "action_bar_end_action_buttons"); anySearchFound = true; }
        }

        final View decorView = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (!anySearchFound
                && decorView != null
                && !Boolean.TRUE.equals(decorView.getTag(TAG_SEARCH_LISTENER_PENDING))
                && !Boolean.TRUE.equals(decorView.getTag(TAG_SEARCH_WIRING_DONE))) {
            decorView.setTag(TAG_SEARCH_LISTENER_PENDING, Boolean.TRUE);
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    boolean found = false;
                    if (sSearchTabId != 0) {
                        View lateView = activity.findViewById(sSearchTabId);
                        if (lateView != null) { processSearchView(activity, lateView, "search_tab"); found = true; }
                    }
                    if (!found && sActionBarEndId != 0) {
                        View lateView = activity.findViewById(sActionBarEndId);
                        if (lateView != null) { processSearchView(activity, lateView, "action_bar_end_action_buttons"); found = true; }
                    }
                    if (found) {
                        decorView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        decorView.setTag(TAG_SEARCH_LISTENER_PENDING, null);
                        decorView.setTag(TAG_SEARCH_WIRING_DONE, Boolean.TRUE);
                    }
                }
            });
        }

    }

    public void mainActivity(ClassLoader classLoader) {
        for (String activityClass : MAIN_ACTIVITIES) {
            hookMainActivityLifecycle(classLoader, activityClass);
        }

        BottomSheetHookUtil.hookBottomSheetNavigator(Module.dexKitBridge);

        XposedHelpers.findAndHookMethod(View.class, "performLongClick", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (sInboxButtonId == 0 && sDirectTabId == 0) return;
                View view = (View) param.thisObject;
                int id = view.getId();
                if (id != sInboxButtonId && id != sDirectTabId) return;
                Activity activity = currentActivity;
                if (activity == null) return;
                GhostModeUtils.toggleSelectedGhostOptions(activity);
                VibrationUtil.vibrate(activity);
                param.setResult(true);
            }
        });

        try {
            XposedHelpers.findAndHookMethod("com.instagram.modal.ModalActivity", classLoader, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            try {
                                setupHooks(activity);
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse): ModalActivity hook failed: " + t.getMessage());
        }
    }

    private void hookMainActivityLifecycle(ClassLoader classLoader, String activityClass) {
        try {
            var methods = Module.dexKitBridge.findMethod(create()
                    .matcher(org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                            .declaredClass(activityClass)
                            .name("onCreate")
                            .paramTypes("android.os.Bundle")
                            .returnType("void")
                    )
            );
            if (methods.isEmpty()) {
                methods = Module.dexKitBridge.findMethod(create()
                        .matcher(org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                                .declaredClass(activityClass)
                                .paramTypes("android.os.Bundle")
                                .returnType("void")
                        )
                );
            }
            if (!methods.isEmpty()) {
                String methodName = methods.get(0).getName();
                if (methodName != null && !methodName.isEmpty()) {
                    XposedHelpers.findAndHookMethod(activityClass, classLoader, methodName, Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            currentActivity = activity;
                            activity.runOnUiThread(() -> {
                                try {
                                    setupHooks(activity);
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
                                            maybeShowFeatureToasts(activity);
                                        } catch (Exception innerE) {
                                            ModuleLog.line("(InstaEclipse): UI Injection Error: " + innerE.getMessage());
                                        }
                                    }, 1500);
                                } catch (Exception e) {
                                    ModuleLog.line("(InstaEclipse): UI logic error in onCreate: " + e);
                                }
                            });
                        }
                    });
                }
            } else {
                try {
                    XposedHelpers.findAndHookMethod(activityClass, classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            final Activity activity = (Activity) param.thisObject;
                            currentActivity = activity;
                            activity.runOnUiThread(() -> {
                                try {
                                    setupHooks(activity);
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            addGhostEmojiNextToInbox(activity, GhostModeUtils.isGhostModeActive());
                                            maybeShowFeatureToasts(activity);
                                        } catch (Exception innerE) {
                                            ModuleLog.line("(InstaEclipse): UI Injection Error: " + innerE.getMessage());
                                        }
                                    }, 1500);
                                } catch (Exception e) {
                                    ModuleLog.line("(InstaEclipse): UI logic error in onCreate: " + e);
                                }
                            });
                        }
                    });
                } catch (Throwable t) {
                    ModuleLog.line("(InstaEclipse): ❌ Failed to find onCreate candidate in " + activityClass);
                }
            }
        } catch (Exception e) {
            ModuleLog.line("(InstaEclipse): ❌ DexKit discovery failed for " + activityClass + ": " + e.getMessage());
        }

        try {
            List<MethodData> candidates = Module.dexKitBridge.findMethod(org.luckypray.dexkit.query.FindMethod.create()
                    .matcher(org.luckypray.dexkit.query.matchers.MethodMatcher.create()
                            .declaredClass(activityClass)
                            .modifiers(java.lang.reflect.Modifier.PUBLIC)
                            .paramCount(0)
                            .returnType("void")
                    )
            );
            for (MethodData methodData : candidates) {
                String methodName = methodData.getName();
                if (methodName == null || methodName.isEmpty()) continue;
                if (methodName.contains("<init>") || methodName.contains("<clinit>")) continue;
                if (methodData.getOpCodes().size() < 20) continue;
                XposedHelpers.findAndHookMethod(activityClass, classLoader, methodName, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Activity activity = (Activity) param.thisObject;
                        currentActivity = activity;
                        activity.runOnUiThread(() -> {
                            try {
                                setupHooks(activity);
                            } catch (Exception e) {
                                ModuleLog.line("(InstaEclipse) UI Error: " + e);
                            }
                        });
                    }
                });
                break;
            }
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse): ❌ onResume discovery failed for " + activityClass + ": " + t.getMessage());
        }
    }

    private static void maybeShowFeatureToasts(Activity activity) {
        if (!FeatureFlags.showFeatureToasts || CustomToast.toastShown) return;
        CustomToast.toastShown = true;
        CustomToast.showFeatureStatusToast(activity);
    }

    private static void applySearchHook(Activity activity, View v) {
        v.setOnLongClickListener(view -> {
            DialogUtils.showEclipseOptionsDialog(activity);
            VibrationUtil.vibrate(activity);
            return true;
        });
    }

    private static void processSearchView(Activity activity, View view, String id) {
        if (id.equals("action_bar_end_action_buttons") && view instanceof ViewGroup container) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                CharSequence description = child.getContentDescription();
                if (description != null && description.toString().toLowerCase().contains("search")) {
                    applySearchHook(activity, child);
                }
            }
        } else {
            applySearchHook(activity, view);
        }
    }

    public static void registerConfigImportReceiver(android.content.Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                String json = intent.getStringExtra("json_content");
                if (json != null && !json.isEmpty()) {
                    ConfigManager.importConfigFromJson(ctx, json);
                }
            }
        };
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver,
                    new IntentFilter("ps.reso.instaeclipse.ACTION_IMPORT_CONFIG"),
                    android.content.Context.RECEIVER_EXPORTED);
        } else {
            androidx.core.content.ContextCompat.registerReceiver(context,
                    receiver,
                    new IntentFilter("ps.reso.instaeclipse.ACTION_IMPORT_CONFIG"),
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
        }
    }

    public static void registerSettingsRestoreReceiver(android.content.Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                String json = intent.getStringExtra("json_content");
                if (json == null || json.isEmpty()) return;
                new Thread(() -> {
                    try {
                        ps.reso.instaeclipse.utils.backup.SettingsBackupManager.fromJson(json);
                        SettingsManager.saveAllFlags();
                        ps.reso.instaeclipse.utils.feature.FeatureManager.refreshFeatureStatus();
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> Toast.makeText(ctx.getApplicationContext(),
                                "✅ " + I18n.t(ctx, R.string.ig_toast_settings_restored), Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> Toast.makeText(ctx.getApplicationContext(),
                                "❌ " + I18n.t(ctx, R.string.ig_toast_restore_failed, e.getMessage()), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver,
                        new IntentFilter("ps.reso.instaeclipse.ACTION_RESTORE_SETTINGS"),
                        android.content.Context.RECEIVER_EXPORTED);
            } else {
                androidx.core.content.ContextCompat.registerReceiver(context,
                        receiver,
                        new IntentFilter("ps.reso.instaeclipse.ACTION_RESTORE_SETTINGS"),
                        androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
            }
            } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | RestoreReceiver): ❌ " + e.getMessage());
        }
    }

}
