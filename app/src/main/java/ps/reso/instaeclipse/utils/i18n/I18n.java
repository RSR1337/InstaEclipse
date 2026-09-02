package ps.reso.instaeclipse.utils.i18n;

import android.content.Context;

import androidx.annotation.StringRes;

import ps.reso.instaeclipse.utils.core.CommonUtils;

public final class I18n {

    private I18n() {}

    public static String t(Context hostContext, @StringRes int resId, Object... args) {
        Context moduleContext = CommonUtils.moduleContext(hostContext);
        if (moduleContext == null) return "";
        try {
            return args.length == 0
                    ? moduleContext.getString(resId)
                    : moduleContext.getString(resId, args);
        } catch (Exception e) {
            return "";
        }
    }
}
