package hu.xavpn.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SimpleYaml {
    private final Map<String, String> scalars = new HashMap<String, String>();
    private final Map<String, List<String>> lists = new HashMap<String, List<String>>();

    public static SimpleYaml load(File file) throws IOException {
        SimpleYaml yaml = new SimpleYaml();
        yaml.parse(FileUtil.readLines(file));
        return yaml;
    }

    private void parse(List<String> lines) {
        List<Node> stack = new ArrayList<Node>();
        for (String rawLine : lines) {
            String withoutComment = stripComment(rawLine);
            if (withoutComment.trim().isEmpty()) {
                continue;
            }
            int indent = countIndent(withoutComment);
            String line = withoutComment.trim();

            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= indent) {
                stack.remove(stack.size() - 1);
            }

            if (line.startsWith("- ")) {
                if (stack.isEmpty()) {
                    continue;
                }
                String path = stack.get(stack.size() - 1).path;
                List<String> values = lists.get(path);
                if (values == null) {
                    values = new ArrayList<String>();
                    lists.put(path, values);
                }
                values.add(unquote(line.substring(2).trim()));
                continue;
            }

            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            String path = stack.isEmpty() ? key : stack.get(stack.size() - 1).path + "." + key;
            if (value.isEmpty()) {
                stack.add(new Node(indent, path));
            } else {
                scalars.put(path, unquote(value));
            }
        }
    }

    public String getString(String path, String fallback) {
        String value = scalars.get(path);
        return value == null ? fallback : value;
    }

    public int getInt(String path, int fallback) {
        String value = scalars.get(path);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean getBoolean(String path, boolean fallback) {
        String value = scalars.get(path);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    public List<String> getStringList(String path, List<String> fallback) {
        List<String> values = lists.get(path);
        return values == null ? fallback : new ArrayList<String>(values);
    }

    private static int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String stripComment(String line) {
        boolean single = false;
        boolean doubleQuote = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '\'' && !doubleQuote) {
                single = !single;
            } else if (character == '"' && !single) {
                doubleQuote = !doubleQuote;
            } else if (character == '#' && !single && !doubleQuote) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).replace("\\\"", "\"");
            }
        }
        return value;
    }

    private static final class Node {
        private final int indent;
        private final String path;

        private Node(int indent, String path) {
            this.indent = indent;
            this.path = path;
        }
    }
}
