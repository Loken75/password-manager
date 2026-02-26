package com.passwordmanager.update;

import java.util.Map;

/**
 * Information about an available update from the GitHub release API.
 */
public class UpdateInfo {
    private final String version;
    private final String releaseNotesUrl;
    private final Map<String, AssetInfo> assets;

    public UpdateInfo(String version, String releaseNotesUrl, Map<String, AssetInfo> assets) {
        this.version = version;
        this.releaseNotesUrl = releaseNotesUrl;
        this.assets = assets;
    }

    public String getVersion() { return version; }
    public String getReleaseNotesUrl() { return releaseNotesUrl; }
    public Map<String, AssetInfo> getAssets() { return assets; }

    /**
     * Information about a single release asset (download).
     */
    public static class AssetInfo {
        private final String downloadUrl;
        private final String fileName;

        public AssetInfo(String downloadUrl, String fileName) {
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
        }

        public String getDownloadUrl() { return downloadUrl; }
        public String getFileName() { return fileName; }
    }
}
