package ps.reso.instaeclipse.utils.version;

public final class VersionCheckResult {
    public final boolean updateAvailable;
    public final String latestVersion;
    public final String updateUrl;

    public VersionCheckResult(boolean updateAvailable, String latestVersion, String updateUrl) {
        this.updateAvailable = updateAvailable;
        this.latestVersion = latestVersion;
        this.updateUrl = updateUrl;
    }

    public static VersionCheckResult upToDate() {
        return new VersionCheckResult(false, null, null);
    }

    public static VersionCheckResult offline() {
        return new VersionCheckResult(false, null, null);
    }
}
