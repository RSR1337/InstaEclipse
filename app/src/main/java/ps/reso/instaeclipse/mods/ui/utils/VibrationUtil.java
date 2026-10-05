package ps.reso.instaeclipse.mods.ui.utils;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

import ps.reso.instaeclipse.utils.log.ModuleLog;

public class VibrationUtil {
    public static void vibrate(Context context) {
        try {
            Vibrator v = context.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception e) {
            ModuleLog.line("InstaEclipse (vibrate error): " + e.getMessage());
        }
    }
}
