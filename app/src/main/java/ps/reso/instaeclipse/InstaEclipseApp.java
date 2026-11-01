package ps.reso.instaeclipse;

import android.app.Application;

import ps.reso.instaeclipse.utils.core.LsPatchCompanionBridge;
import ps.reso.instaeclipse.utils.core.RemotePrefsMirror;

public class InstaEclipseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LsPatchCompanionBridge.initForCompanion(this);
        RemotePrefsMirror.start(this);
    }
}
