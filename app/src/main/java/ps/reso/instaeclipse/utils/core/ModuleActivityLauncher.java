package ps.reso.instaeclipse.utils.core;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import de.robv.android.xposed.XposedBridge;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public final class ModuleActivityLauncher {

    private ModuleActivityLauncher() {}

    public static boolean launch(Context context, String activityClassName, Bundle extras) {
        Activity activity = UIHookManager.getCurrentActivity();
        if (activity == null || activity.isFinishing()) {
            ModuleLog.line("(InstaEclipse | Launcher): no active Instagram activity");
            return false;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(CommonUtils.MY_PACKAGE_NAME, activityClassName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (extras != null) intent.putExtras(extras);
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Launcher): failed " + activityClassName + " -> " + t.getMessage());
            return false;
        }
    }
}
