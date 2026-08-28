package ps.reso.instaeclipse.utils.feature;

import android.content.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ps.reso.instaeclipse.utils.i18n.I18n;

public class FeatureStatusTracker {

    public enum State {
        OFF,
        PENDING,
        HOOKED
    }

    private static final Map<String, State> features = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Integer> labels = Collections.synchronizedMap(new HashMap<>());

    public static void setEnabled(String name, int labelResId) {
        State previous = features.get(name);
        features.put(name, previous == State.HOOKED ? State.HOOKED : State.PENDING);
        labels.put(name, labelResId);
    }

    public static void setDisabled(String name, int labelResId) {
        features.put(name, State.OFF);
        labels.put(name, labelResId);
    }

    public static void setDisabled(String name) {
        features.put(name, State.OFF);
    }

    public static void setHooked(String name) {
        if (features.containsKey(name) && features.get(name) != State.OFF) {
            features.put(name, State.HOOKED);
        }
    }

    public static String getLabel(Context ctx, String key) {
        Integer resId = labels.get(key);
        return resId != null ? I18n.t(ctx, resId) : key;
    }

    public static Map<String, State> getStatus() {
        return features;
    }

    public static boolean hasEnabledFeatures() {
        synchronized (features) {
            for (State state : features.values()) {
                if (state != State.OFF) return true;
            }
        }
        return false;
    }
}
