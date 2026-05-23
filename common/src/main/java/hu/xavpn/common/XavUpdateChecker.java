package hu.xavpn.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class XavUpdateChecker {
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/geri-888/xav/releases/latest";
    private static final long CHECK_INTERVAL_MS = 30L * 60L * 1000L;

    private final XavLogger logger;
    private long lastCheck;
    private String latestVersion;

    public XavUpdateChecker(XavLogger logger) {
        this.logger = logger;
    }

    public synchronized List<String> getNoticeLines() {
        long now = System.currentTimeMillis();
        if (latestVersion == null || now - lastCheck > CHECK_INTERVAL_MS) {
            try {
                latestVersion = fetchLatestVersion();
                lastCheck = now;
            } catch (IOException exception) {
                lastCheck = now;
                logger.warn("Frissites ellenorzes sikertelen: " + exception.getMessage());
            }
        }
        if (latestVersion == null || !isNewer(latestVersion, XavCore.VERSION)) {
            return null;
        }
        List<String> lines = new ArrayList<String>();
        lines.add(TextUtil.color("&7_________________/ &c" + XavCore.PLUGIN_NAME + " &7\\_________________"));
        lines.add(TextUtil.color("&7| &e" + latestVersion + " &7verzio elerheto! Jelenlegi verziod: &c" + XavCore.VERSION));
        lines.add(TextUtil.color("&7----------------------------------------"));
        return lines;
    }

    @SuppressWarnings("unchecked")
    private String fetchLatestVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "XAntiVPN/" + XavCore.VERSION);
        int status = connection.getResponseCode();
        String body = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
        if (status < 200 || status >= 300) {
            throw new IOException("GitHub HTTP " + status);
        }
        Object parsed = SimpleJson.parse(body);
        if (!(parsed instanceof Map)) {
            throw new IOException("Hibas GitHub valasz");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        String name = value(root.get("name"));
        if (name == null || name.trim().isEmpty()) {
            name = value(root.get("tag_name"));
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Nincs release verzio");
        }
        return cleanVersion(name);
    }

    private static boolean isNewer(String latest, String current) {
        String[] latestParts = cleanVersion(latest).split("\\.");
        String[] currentParts = cleanVersion(current).split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int latestValue = index < latestParts.length ? parsePart(latestParts[index]) : 0;
            int currentValue = index < currentParts.length ? parsePart(currentParts[index]) : 0;
            if (latestValue > currentValue) {
                return true;
            }
            if (latestValue < currentValue) {
                return false;
            }
        }
        return false;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String cleanVersion(String value) {
        String cleaned = value == null ? "" : value.trim().replaceAll("[^0-9.]", "");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "0" : cleaned;
    }

    private static String value(Object object) {
        return object == null ? null : String.valueOf(object);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }
        return builder.toString();
    }
}
