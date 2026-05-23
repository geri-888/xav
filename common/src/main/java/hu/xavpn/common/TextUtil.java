package hu.xavpn.common;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {
    private TextUtil() {
    }

    public static String color(String value) {
        return value == null ? "" : value.replace('&', '\u00A7');
    }

    public static List<String> color(List<String> values) {
        List<String> lines = new ArrayList<String>();
        for (String value : values) {
            lines.add(color(value));
        }
        return lines;
    }

    public static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    public static String replace(String text, String token, String replacement) {
        return text == null ? "" : text.replace(token, replacement == null ? "" : replacement);
    }
}
