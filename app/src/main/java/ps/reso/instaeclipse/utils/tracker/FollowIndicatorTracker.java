package ps.reso.instaeclipse.utils.tracker;

import java.util.concurrent.ConcurrentHashMap;

public class FollowIndicatorTracker {

    public static volatile String currentlyViewedUserId = null;

    public static volatile long capturedAt = 0;

    public static final ConcurrentHashMap<String, ObservedFollowResult> observedResults
            = new ConcurrentHashMap<>();

    public static class ObservedFollowResult {
        public final boolean followedBy;
        public final String username;
        public final long timestamp;

        public ObservedFollowResult(boolean followedBy, String username, long timestamp) {
            this.followedBy = followedBy;
            this.username = username;
            this.timestamp = timestamp;
        }
    }
}
