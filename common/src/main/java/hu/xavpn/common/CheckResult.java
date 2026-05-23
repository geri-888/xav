package hu.xavpn.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CheckResult {
    private final boolean allowed;
    private final IpApiRecord record;
    private final List<CheckFlag> matchedFlags;
    private final String error;

    private CheckResult(boolean allowed, IpApiRecord record, List<CheckFlag> matchedFlags, String error) {
        this.allowed = allowed;
        this.record = record;
        this.matchedFlags = Collections.unmodifiableList(new ArrayList<CheckFlag>(matchedFlags));
        this.error = error;
    }

    public static CheckResult allowed(IpApiRecord record) {
        return new CheckResult(true, record, Collections.<CheckFlag>emptyList(), null);
    }

    public static CheckResult blocked(IpApiRecord record, List<CheckFlag> matchedFlags) {
        return new CheckResult(false, record, matchedFlags, null);
    }

    public static CheckResult error(String error) {
        return new CheckResult(true, null, Collections.<CheckFlag>emptyList(), error);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public IpApiRecord getRecord() {
        return record;
    }

    public List<CheckFlag> getMatchedFlags() {
        return matchedFlags;
    }

    public String getError() {
        return error;
    }

    public String getCodes() {
        if (matchedFlags.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < matchedFlags.size(); index++) {
            if (index > 0) {
                builder.append("; ");
            }
            builder.append(matchedFlags.get(index).getCode());
        }
        return builder.toString();
    }

    public String getLabels() {
        if (matchedFlags.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < matchedFlags.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(matchedFlags.get(index).getLabel());
        }
        return builder.toString();
    }
}
