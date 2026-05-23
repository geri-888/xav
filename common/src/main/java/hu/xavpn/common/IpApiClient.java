package hu.xavpn.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IpApiClient {
    private final XavConfig config;

    public IpApiClient(XavConfig config) {
        this.config = config;
    }

    public IpApiRecord lookup(String ip) throws IOException {
        String url = config.getEndpoint()
                .replace("{ip}", encode(ip))
                .replace("{key}", encode(config.getApiKey()));
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(config.getTimeoutMs());
        connection.setReadTimeout(config.getTimeoutMs());
        connection.setRequestProperty("User-Agent", "XAntiVPN/26.5");
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = read(stream);
        if (status < 200 || status >= 300) {
            throw new IOException("ipapi.is HTTP " + status + ": " + body);
        }
        return parse(body);
    }

    @SuppressWarnings("unchecked")
    private IpApiRecord parse(String body) throws IOException {
        Object parsed = SimpleJson.parse(body);
        if (!(parsed instanceof Map)) {
            throw new IOException("Az ipapi.is valasza nem JSON objektum");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        Map<String, Boolean> flags = new LinkedHashMap<String, Boolean>();
        for (CheckFlag check : config.getChecks()) {
            Object value = root.get(check.getKey());
            flags.put(check.getKey(), Boolean.TRUE.equals(value));
        }

        String isp = nestedString(root, "company", "name");
        if (isp == null || isp.trim().isEmpty()) {
            isp = nestedString(root, "asn", "org");
        }
        if (isp == null || isp.trim().isEmpty()) {
            isp = nestedString(root, "asn", "descr");
        }
        return new IpApiRecord(asString(root.get("ip")), isp, flags);
    }

    @SuppressWarnings("unchecked")
    private static String nestedString(Map<String, Object> root, String objectKey, String valueKey) {
        Object object = root.get(objectKey);
        if (object instanceof Map) {
            return asString(((Map<String, Object>) object).get(valueKey));
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
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
