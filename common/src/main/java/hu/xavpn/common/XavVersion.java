package hu.xavpn.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class XavVersion {
    private static final String VERSION = load();

    private XavVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String load() {
        InputStream input = XavVersion.class.getClassLoader().getResourceAsStream("xantivpn-version.properties");
        if (input == null) {
            return "unknown";
        }
        try {
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            return version == null || version.trim().isEmpty() ? "unknown" : version.trim();
        } catch (IOException ignored) {
            return "unknown";
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }
}
