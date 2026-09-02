package ps.reso.instaeclipse.utils.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

public final class ModuleHookProbe {

    public interface Callback {
        void onResult(boolean active);
    }

    private static final long TIMEOUT_MS = 4500L;

    private ModuleHookProbe() {
    }

    public static void probe(Context context, String instagramPackage, Callback callback) {
        if (context == null || instagramPackage == null || callback == null) {
            if (callback != null) callback.onResult(false);
            return;
        }
        Context app = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] answered = {false};

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!CommonUtils.ACTION_LOGS_REPLY.equals(intent.getAction())) return;
                String source = intent.getStringExtra(CommonUtils.EXTRA_LOG_SOURCE);
                if (!instagramPackage.equals(source)) return;
                if (answered[0]) return;
                answered[0] = true;
                try {
                    ctx.unregisterReceiver(this);
                } catch (Throwable ignored) {
                }
                handler.removeCallbacksAndMessages(null);
                callback.onResult(true);
            }
        };

        IntentFilter filter = new IntentFilter(CommonUtils.ACTION_LOGS_REPLY);
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        }

        handler.postDelayed(() -> {
            if (answered[0]) return;
            answered[0] = true;
            try {
                app.unregisterReceiver(receiver);
            } catch (Throwable ignored) {
            }
            callback.onResult(false);
        }, TIMEOUT_MS);

        Intent request = new Intent(CommonUtils.ACTION_REQUEST_LOGS);
        request.setPackage(instagramPackage);
        request.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        app.sendBroadcast(request);
    }

    public static String findInstalledInstagramPackage(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : CommonUtils.SUPPORTED_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }
}
