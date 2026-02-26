package com.passwordmanager.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.passwordmanager.config.AppVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Checks for updates via the GitHub Releases API.
 * Uses only java.net (no extra dependencies) plus Gson (already available).
 */
public class UpdateChecker {

    /** Maximum API response size: 1 MB. */
    private static final int MAX_RESPONSE_SIZE = 1_048_576;
    private static final String GITHUB_DOMAIN = "https://github.com/";

    private final String githubOwner;
    private final String githubRepo;
    private final boolean enabled;
    private final int checkIntervalMinutes;

    public UpdateChecker() {
        Properties props = loadProperties();
        this.githubOwner = props.getProperty("update.github.owner", "Loken75");
        this.githubRepo = props.getProperty("update.github.repo", "password-manager");
        this.enabled = Boolean.parseBoolean(props.getProperty("update.enabled", "true"));
        this.checkIntervalMinutes = Integer.parseInt(props.getProperty("update.check.interval.minutes", "5"));
    }

    public boolean isEnabled() { return enabled; }
    public int getCheckIntervalMinutes() { return checkIntervalMinutes; }

    /**
     * Checks GitHub for the latest release.
     * @return UpdateInfo if a newer version exists, null if up-to-date or check fails
     */
    public UpdateInfo checkForUpdate() throws IOException {
        if (!enabled) return null;

        String apiUrl = "https://api.github.com/repos/" + githubOwner + "/" + githubRepo + "/releases/latest";
        HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        try {
            int code = conn.getResponseCode();
            if (code != 200) return null;

            String json = readStream(conn.getInputStream());
            return parseRelease(json);
        } finally {
            conn.disconnect();
        }
    }

    private UpdateInfo parseRelease(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String tagName = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
        String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
        String currentVersion = AppVersion.get();

        if (!VersionComparator.isNewer(version, currentVersion)) {
            return null;
        }

        String releaseUrl = root.has("html_url") ? root.get("html_url").getAsString() : "";
        if (!releaseUrl.startsWith(GITHUB_DOMAIN)) {
            return null; // reject URLs not pointing to GitHub
        }

        Map<String, UpdateInfo.AssetInfo> assets = new HashMap<>();
        if (root.has("assets")) {
            JsonArray assetsArray = root.getAsJsonArray("assets");
            for (JsonElement elem : assetsArray) {
                JsonObject asset = elem.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                String downloadUrl = asset.has("browser_download_url")
                    ? asset.get("browser_download_url").getAsString() : "";
                if (!downloadUrl.startsWith(GITHUB_DOMAIN)) continue; // skip non-GitHub URLs
                assets.put(name, new UpdateInfo.AssetInfo(downloadUrl, name));
            }
        }

        return new UpdateInfo(version, releaseUrl, assets);
    }

    private static String readStream(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) != -1) {
                if (sb.length() + read > MAX_RESPONSE_SIZE) {
                    throw new IOException("Response exceeds maximum size (" + MAX_RESPONSE_SIZE + " bytes)");
                }
                sb.append(buf, 0, read);
            }
            return sb.toString();
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = UpdateChecker.class.getClassLoader().getResourceAsStream("update.properties")) {
            if (is != null) props.load(is);
        } catch (IOException ignored) {
            // use defaults
        }
        return props;
    }
}
