package ps.reso.instaeclipse;

import android.app.Application;

import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;

public class InstaEclipseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LsPatchCompanionBridge.initForCompanion(this);
    }
}
