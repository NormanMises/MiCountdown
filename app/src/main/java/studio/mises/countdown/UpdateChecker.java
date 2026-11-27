package studio.mises.countdown;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Checks GitHub Releases for a newer version. The only network access in the app. */
final class UpdateChecker {
    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/NormanMises/MiCountdown/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/NormanMises/MiCountdown/releases";
    private static final long CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L;
    private static final String PREFS = "updates";

    record Release(String version, String downloadUrl) {}

    private UpdateChecker() {}

    static String currentVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "0";
        }
    }

    /**
     * Returns a newer release, or null. Queries GitHub at most every six hours
     * and otherwise answers from the last result. Call off the main thread.
     */
    static Release newerRelease(Context context) {
        try {
            return newerRelease(context, false);
        } catch (IOException error) {
            return null; // Not thrown when not forced.
        }
    }

    /**
     * Like {@link #newerRelease(Context)}; with {@code force} it always asks
     * GitHub and reports a failed request instead of using the last result.
     */
    static Release newerRelease(Context context, boolean force) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (force || now - prefs.getLong("checkedAt", 0) >= CHECK_INTERVAL_MILLIS) {
            try {
                Release latest = fetchLatest();
                prefs.edit()
                        .putLong("checkedAt", now)
                        .putString("version", latest.version())
                        .putString("url", latest.downloadUrl())
                        .apply();
            } catch (IOException | RuntimeException | org.json.JSONException error) {
                Log.i("MiCountdown", "Update check failed", error);
                if (force) {
                    throw new IOException(error);
                }
            }
        }
        String version = prefs.getString("version", null);
        if (version == null || compare(version, currentVersion(context)) <= 0) {
            return null;
        }
        return new Release(version, prefs.getString("url", RELEASES_PAGE));
    }

    private static Release fetchLatest() throws IOException, org.json.JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "MiCountdown");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            JSONObject release = new JSONObject(read(connection.getInputStream()));
            String version = release.getString("tag_name").replaceFirst("^v", "");
            String url = release.optString("html_url", RELEASES_PAGE);
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.optString("name").endsWith(".apk")) {
                        url = asset.getString("browser_download_url");
                        break;
                    }
                }
            }
            return new Release(version, url);
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream in = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Compares dotted versions numerically: 0.10.0 is newer than 0.9.1. */
    static int compare(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int diff = Integer.compare(part(left, i), part(right, i));
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("\\D.*$", ""));
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
