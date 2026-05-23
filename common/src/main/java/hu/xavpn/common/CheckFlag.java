package hu.xavpn.common;

public final class CheckFlag {
    private final String key;
    private final String label;
    private final String code;
    private final boolean enabled;

    public CheckFlag(String key, String label, String code, boolean enabled) {
        this.key = key;
        this.label = label;
        this.code = code;
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return code;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
