package ps.reso.instaeclipse.mods.feed;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;


public class FeedPhotoZoomHook {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String VIEW_CLASS = "com.instagram.feed.widget.IgProgressImageView";

    
    private static volatile int sFeedLikeButtonId = 0;

    public void install(ClassLoader classLoader) {
        try {
            Class<?> viewClass = classLoader.loadClass(VIEW_CLASS);

            
            XposedHelpers.findAndHookMethod(viewClass, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    
                    
                    if (!FeatureFlags.enablePhotoZoom) return;
                    try {
                        View view = (View) param.thisObject;
                        if (!isInsideFeedRow(view)) return;
                        view.setOnLongClickListener(v -> {
                            if (!FeatureFlags.enablePhotoZoom) return false;
                            try {
                                Object imgViewObj = XposedHelpers.callMethod(param.thisObject, "getIgImageView");
                                if (!(imgViewObj instanceof ImageView)) return false;
                                Bitmap snapshot = viewToBitmap((ImageView) imgViewObj);
                                if (snapshot == null) return false;
                                showZoomOverlay(v.getContext(), snapshot);
                                return true;
                            } catch (Throwable t) {
                                ModuleLog.line("(IE|PhotoZoom) ❌ long-press: " + t);
                                return false;
                            }
                        });
                    } catch (Throwable t) {
                        ModuleLog.line("(IE|PhotoZoom) ❌ attach hook: " + t);
                    }
                }
            });

            FeatureStatusTracker.setHooked("PhotoZoom");
            ModuleLog.line("(IE|PhotoZoom) ✅ hook installed");
        } catch (Throwable t) {
            ModuleLog.line("(IE|PhotoZoom) ❌ install: " + t);
        }
    }

    
    private static boolean isInsideFeedRow(View view) {
        if (sFeedLikeButtonId == 0) {
            sFeedLikeButtonId = view.getContext().getResources()
                    .getIdentifier("row_feed_button_like", "id", view.getContext().getPackageName());
        }
        if (sFeedLikeButtonId == 0) return false; 

        ViewParent parent = view.getParent();
        for (int depth = 0; depth < 6 && parent instanceof ViewGroup group; depth++) {
            if (containsDescendantWithId(group, sFeedLikeButtonId, 4)) return true;
            parent = group.getParent();
        }
        return false;
    }

    private static boolean containsDescendantWithId(ViewGroup group, int targetId, int depth) {
        if (depth < 0) return false;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child.getId() == targetId) return true;
            if (child instanceof ViewGroup childGroup && containsDescendantWithId(childGroup, targetId, depth - 1)) {
                return true;
            }
        }
        return false;
    }

    private static void showZoomOverlay(Context ctx, Bitmap bitmap) {
        MAIN.post(() -> {
            try {
                Dialog dialog = new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                ZoomableImageView zoomView = new ZoomableImageView(ctx);
                zoomView.setImageBitmap(bitmap);
                zoomView.setBackgroundColor(Color.BLACK);
                zoomView.setOnDismissRequest(dialog::dismiss);

                dialog.setContentView(zoomView);
                Window w = dialog.getWindow();
                if (w != null) {
                    w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                    w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                }
                dialog.show();
            } catch (Throwable t) {
                ModuleLog.line("(IE|PhotoZoom) ❌ overlay: " + t);
            }
        });
    }

    
    private static Bitmap viewToBitmap(View v) {
        int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }
}
