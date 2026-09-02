package ps.reso.instaeclipse.utils.feature;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

import ps.reso.instaeclipse.utils.core.CommonUtils;

public final class FeatureHealthReport {

    public static final String STATE_OK = "ok";
    public static final String STATE_BROKEN = "broken";
    public static final String STATE_DISABLED = "disabled";

    public static String buildJson(Context ctx) throws JSONException {
        Context labelCtx = CommonUtils.moduleContext(ctx);
        if (labelCtx == null) labelCtx = ctx;
        JSONArray arr = new JSONArray();
        Map<String, FeatureStatusTracker.State> status = FeatureStatusTracker.getStatus();
        synchronized (status) {
            for (Map.Entry<String, FeatureStatusTracker.State> entry : status.entrySet()) {
                String key = entry.getKey();
                String state;
                switch (entry.getValue()) {
                    case HOOKED:
                        state = STATE_OK;
                        break;
                    case PENDING:
                        state = STATE_BROKEN;
                        break;
                    default:
                        state = STATE_DISABLED;
                }

                JSONObject o = new JSONObject();
                o.put("key", key);
                o.put("label", FeatureStatusTracker.getLabel(labelCtx, key));
                o.put("state", state);
                if (STATE_BROKEN.equals(state)) {
                    o.put("error", "Not confirmed hooked yet");
                }
                arr.put(o);
            }
        }
        return arr.toString();
    }

    private FeatureHealthReport() {
    }
}
