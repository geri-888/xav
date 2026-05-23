package hu.xavpn.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class XavCore {
    private final File dataFolder;
    private final XavLogger logger;
    private XavConfig config;
    private XavDatabase database;
    private WhitelistStore whitelist;
    private PlayerIndex playerIndex;
    private IpApiClient client;
    private final Map<String, CachedResult> cache = new HashMap<String, CachedResult>();

    public XavCore(File dataFolder, XavLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public synchronized void load() throws IOException {
        this.config = XavConfig.load(dataFolder);
        if (database != null) {
            database.close();
        }
        this.database = new XavDatabase(dataFolder, config);
        try {
            this.database.open();
        } catch (java.sql.SQLException exception) {
            throw new IOException("Adatbazis megnyitasa sikertelen", exception);
        }
        this.whitelist = new WhitelistStore(database);
        this.playerIndex = new PlayerIndex(database);
        this.client = new IpApiClient(config);
        this.cache.clear();
        logger.info("XAVPN betoltve.");
    }

    public XavConfig getConfig() {
        return config;
    }

    public WhitelistStore getWhitelist() {
        return whitelist;
    }

    public PlayerIndex getPlayerIndex() {
        return playerIndex;
    }

    public boolean isWhitelisted(String playerName) {
        return whitelist.contains(playerName);
    }

    public void recordPlayer(String playerName, String ip) {
        playerIndex.record(playerName, ip);
    }

    public CheckResult check(String playerName, String ip, boolean permissionBypass) {
        recordPlayer(playerName, ip);
        if (permissionBypass || whitelist.contains(playerName)) {
            return CheckResult.allowed(null);
        }
        try {
            IpApiRecord record = lookup(ip);
            List<CheckFlag> matched = new ArrayList<CheckFlag>();
            for (CheckFlag check : config.getChecks()) {
                if (check.isEnabled() && record.isFlagged(check.getKey())) {
                    matched.add(check);
                }
            }
            if (!matched.isEmpty()) {
                return CheckResult.blocked(record, matched);
            }
            return CheckResult.allowed(record);
        } catch (IOException exception) {
            logger.warn("IP ellenorzes sikertelen (" + ip + "): " + exception.getMessage(), exception);
            if (config.isFailClosed()) {
                List<CheckFlag> error = new ArrayList<CheckFlag>();
                error.add(new CheckFlag("api_error", "API", "API", true));
                return CheckResult.blocked(null, error);
            }
            return CheckResult.error(exception.getMessage());
        }
    }

    public IpApiRecord lookupForCommand(String ip) throws IOException {
        return lookup(ip);
    }

    public List<String> buildBlockedMessage(CheckResult result) {
        List<String> lines = new ArrayList<String>();
        String codes = result.getCodes();
        String flags = result.getLabels();
        String isp = result.getRecord() == null ? "Ismeretlen" : result.getRecord().getIsp();
        for (String line : config.getBlockedMessage()) {
            String replaced = TextUtil.replace(line, "{codes}", codes);
            replaced = TextUtil.replace(replaced, "{flags}", flags);
            replaced = TextUtil.replace(replaced, "{isp}", isp);
            lines.add(replaced);
        }
        return lines;
    }

    public String buildBlockedKickMessage(CheckResult result) {
        return TextUtil.joinLines(TextUtil.color(buildBlockedMessage(result)));
    }

    public String buildModeratorBlockedMessage(String playerName, CheckResult result) {
        String message = config.getModeratorBlockedMessage();
        message = TextUtil.replace(message, "{player}", playerName);
        message = TextUtil.replace(message, "{flags}", result.getLabels());
        message = TextUtil.replace(message, "{codes}", result.getCodes());
        message = TextUtil.replace(message, "{isp}", result.getRecord() == null ? "Ismeretlen" : result.getRecord().getIsp());
        return TextUtil.color(message);
    }

    public String prefix() {
        return TextUtil.color(config.getPrefix());
    }

    private synchronized IpApiRecord lookup(String ip) throws IOException {
        String normalized = ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedResult cached = cache.get(normalized);
        if (cached != null && cached.expiresAt > now) {
            return cached.record;
        }
        IpApiRecord record = client.lookup(ip);
        long ttl = Math.max(1, config.getCacheMinutes()) * 60L * 1000L;
        cache.put(normalized, new CachedResult(record, now + ttl));
        return record;
    }

    private static final class CachedResult {
        private final IpApiRecord record;
        private final long expiresAt;

        private CachedResult(IpApiRecord record, long expiresAt) {
            this.record = record;
            this.expiresAt = expiresAt;
        }
    }
}
