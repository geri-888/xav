package hu.xavpn.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IpApiRecord {
    private final String ip;
    private final String isp;
    private final Map<String, Boolean> flags;

    public IpApiRecord(String ip, String isp, Map<String, Boolean> flags) {
        this.ip = ip;
        this.isp = isp == null || isp.trim().isEmpty() ? "Ismeretlen" : isp;
        this.flags = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(flags));
    }

    public String getIp() {
        return ip;
    }

    public String getIsp() {
        return isp;
    }

    public boolean isFlagged(String key) {
        Boolean value = flags.get(key);
        return value != null && value.booleanValue();
    }

    public Map<String, Boolean> getFlags() {
        return flags;
    }
}
