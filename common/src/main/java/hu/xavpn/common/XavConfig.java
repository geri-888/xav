package hu.xavpn.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class XavConfig {
    public static final String PERM_WHITELIST = "xav.whitelist";
    public static final String PERM_BYPASS = "xav.kivetel";
    public static final String PERM_MOD = "xav.mod";

    private final String apiKey;
    private final String endpoint;
    private final int timeoutMs;
    private final int cacheMinutes;
    private final boolean failClosed;
    private final String storageType;
    private final List<CheckFlag> checks;
    private final List<String> blockedMessage;
    private final String moderatorBlockedMessage;
    private final String prefix;
    private final String noPermission;
    private final String usage;

    private XavConfig(
            String apiKey,
            String endpoint,
            int timeoutMs,
            int cacheMinutes,
            boolean failClosed,
            String storageType,
            List<CheckFlag> checks,
            List<String> blockedMessage,
            String moderatorBlockedMessage,
            String prefix,
            String noPermission,
            String usage) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
        this.cacheMinutes = cacheMinutes;
        this.failClosed = failClosed;
        this.storageType = storageType;
        this.checks = Collections.unmodifiableList(new ArrayList<CheckFlag>(checks));
        this.blockedMessage = Collections.unmodifiableList(new ArrayList<String>(blockedMessage));
        this.moderatorBlockedMessage = moderatorBlockedMessage;
        this.prefix = prefix;
        this.noPermission = noPermission;
        this.usage = usage;
    }

    public static XavConfig load(File dataFolder) throws IOException {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            FileUtil.writeUtf8(configFile, defaultConfig());
        }
        SimpleYaml yaml = SimpleYaml.load(configFile);
        List<CheckFlag> checks = new ArrayList<CheckFlag>();
        addCheck(yaml, checks, "is_bogon", "Bogon", "0x1B");
        addCheck(yaml, checks, "is_mobile", "Mobile", "0x1A");
        addCheck(yaml, checks, "is_satellite", "Satellite", "0x2A");
        addCheck(yaml, checks, "is_crawler", "Crawler", "0x2B");
        addCheck(yaml, checks, "is_datacenter", "Datacenter", "0x3A");
        addCheck(yaml, checks, "is_tor", "Tor", "0x5A");
        addCheck(yaml, checks, "is_proxy", "Proxy", "0x6A");
        addCheck(yaml, checks, "is_vpn", "VPN", "0x4A");
        addCheck(yaml, checks, "is_abuser", "Abuser", "0x7A");

        return new XavConfig(
                yaml.getString("api.key", "ideazapikulcsotaconfig.ymlbol"),
                yaml.getString("api.endpoint", "https://api.ipapi.is/?q={ip}&key={key}"),
                yaml.getInt("api.timeout-ms", 5000),
                yaml.getInt("api.cache-minutes", 30),
                yaml.getBoolean("api.fail-closed", false),
                yaml.getString("storage.type", "h2"),
                checks,
                yaml.getStringList("messages.blocked", defaultBlockedMessage()),
                yaml.getString("messages.moderator-blocked", "&a{player} &fcsatlakozása blokkolva lett az AntiVPN által &7(&c{flags}&7)"),
                yaml.getString("messages.prefix", "&8[&cXAV&8]&r "),
                yaml.getString("messages.no-permission", "&cNincs jogod ehhez."),
                yaml.getString("messages.usage", "&eHasznalat: /xav kivetel <jatekos>, /xav eltavolit <jatekos>, /xav ip <jatekos>, /xav alt <jatekos|ip>"));
    }

    private static void addCheck(SimpleYaml yaml, List<CheckFlag> checks, String key, String label, String code) {
        String path = "blocked-checks." + key;
        checks.add(new CheckFlag(
                key,
                yaml.getString(path + ".label", label),
                yaml.getString(path + ".code", code),
                yaml.getBoolean(path + ".enabled", true)));
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getCacheMinutes() {
        return cacheMinutes;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public String getStorageType() {
        return storageType;
    }

    public List<CheckFlag> getChecks() {
        return checks;
    }

    public List<String> getBlockedMessage() {
        return blockedMessage;
    }

    public String getModeratorBlockedMessage() {
        return moderatorBlockedMessage;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNoPermission() {
        return noPermission;
    }

    public String getUsage() {
        return usage;
    }

    private static List<String> defaultBlockedMessage() {
        List<String> lines = new ArrayList<String>();
        lines.add("&c&lCSATLAKOZÁS BLOKKOLVA");
        lines.add("");
        lines.add("&cKérlek kapcsolj csak magyar, vezetékes szolgáltatóval gyere fel");
        lines.add("&a(One, Telekom)");
        lines.add("");
        lines.add("&6VPN, Proxy, Datacenter, Műhold, mobilnet &7-> &etiltva van");
        lines.add("");
        lines.add("&7Hibakód: {codes}");
        return lines;
    }

    public static String defaultConfig() {
        return ""
                + "api:\n"
                + "  key: \"ideazapikulcsotaconfig.ymlbol\"\n"
                + "  endpoint: \"https://api.ipapi.is/?q={ip}&key={key}\"\n"
                + "  timeout-ms: 5000\n"
                + "  cache-minutes: 30\n"
                + "  fail-closed: false\n"
                + "\n"
                + "storage:\n"
                + "  # h2 = LuckPerms/LiteBans style local database file. sqlite = SQLite3 file.\n"
                + "  type: \"h2\"\n"
                + "\n"
                + "blocked-checks:\n"
                + "  is_bogon:\n"
                + "    enabled: true\n"
                + "    code: \"0x1B\"\n"
                + "    label: \"Bogon\"\n"
                + "  is_mobile:\n"
                + "    enabled: true\n"
                + "    code: \"0x1A\"\n"
                + "    label: \"Mobile\"\n"
                + "  is_satellite:\n"
                + "    enabled: true\n"
                + "    code: \"0x2A\"\n"
                + "    label: \"Satellite\"\n"
                + "  is_crawler:\n"
                + "    enabled: true\n"
                + "    code: \"0x2B\"\n"
                + "    label: \"Crawler\"\n"
                + "  is_datacenter:\n"
                + "    enabled: true\n"
                + "    code: \"0x3A\"\n"
                + "    label: \"Datacenter\"\n"
                + "  is_tor:\n"
                + "    enabled: true\n"
                + "    code: \"0x5A\"\n"
                + "    label: \"Tor\"\n"
                + "  is_proxy:\n"
                + "    enabled: true\n"
                + "    code: \"0x6A\"\n"
                + "    label: \"Proxy\"\n"
                + "  is_vpn:\n"
                + "    enabled: true\n"
                + "    code: \"0x4A\"\n"
                + "    label: \"VPN\"\n"
                + "  is_abuser:\n"
                + "    enabled: true\n"
                + "    code: \"0x7A\"\n"
                + "    label: \"Abuser\"\n"
                + "\n"
                + "messages:\n"
                + "  prefix: \"&8[&cXAV&8]&r \"\n"
                + "  no-permission: \"&cNincs jogod ehhez.\"\n"
                + "  usage: \"&eHasznalat: /xav kivetel <jatekos>, /xav eltavolit <jatekos>, /xav ip <jatekos>, /xav alt <jatekos|ip>\"\n"
                + "  moderator-blocked: \"&a{player} &fcsatlakozása blokkolva lett az AntiVPN által &7(&c{flags}&7)\"\n"
                + "  blocked:\n"
                + "    - \"&c&lCSATLAKOZÁS BLOKKOLVA\"\n"
                + "    - \"\"\n"
                + "    - \"&cKérlek kapcsolj csak magyar, vezetékes szolgáltatóval gyere fel\"\n"
                + "    - \"&a(One, Telekom)\"\n"
                + "    - \"\"\n"
                + "    - \"&6VPN, Proxy, Datacenter, Műhold, mobilnet &7-> &etiltva van\"\n"
                + "    - \"\"\n"
                + "    - \"&7Hibakód: {codes}\"\n";
    }
}
