package ps.reso.instaeclipse.utils.core;

import android.view.View;

import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class ViewAttachDispatcher {

    public interface Listener {
        void onViewAttached(View view);

        default void onViewDetached(View view) {}
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean hooked;

    private ViewAttachDispatcher() {}

    public static void add(Listener listener) {
        if (listener == null) return;
        LISTENERS.add(listener);
        ensureHooked();
    }

    private static void ensureHooked() {
        if (hooked) return;
        synchronized (ViewAttachDispatcher.class) {
            if (hooked) return;
            try {
                XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View view)) return;
                        for (Listener listener : LISTENERS) {
                            try {
                                listener.onViewAttached(view);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
                XposedHelpers.findAndHookMethod(View.class, "onDetachedFromWindow", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View view)) return;
                        for (Listener listener : LISTENERS) {
                            try {
                                listener.onViewDetached(view);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
                hooked = true;
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | ViewAttach): " + t.getMessage());
            }
        }
    }
}
