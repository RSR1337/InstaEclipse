package ps.reso.instaeclipse.utils.version;

public class VersionCheck {
    private final String latest_version;
    private final String update_url;

    public VersionCheck(String latestVersion, String updateUrl) {
        latest_version = latestVersion;
        update_url = updateUrl;
    }

    public String getLatestVersion() {
        return latest_version;
    }

    public String getUpdateUrl() {
        return update_url;
    }
}
