package hu.xavpn.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {
    private final String json;
    private int index;

    private SimpleJson(String json) {
        this.json = json;
    }

    public static Object parse(String json) throws IOException {
        SimpleJson parser = new SimpleJson(json);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != parser.json.length()) {
            throw new IOException("Varatlan JSON tartalom a(z) " + parser.index + ". karakternel");
        }
        return value;
    }

    private Object readValue() throws IOException {
        skipWhitespace();
        if (index >= json.length()) {
            throw new IOException("Ures JSON ertek");
        }
        char character = json.charAt(index);
        if (character == '{') {
            return readObject();
        }
        if (character == '[') {
            return readArray();
        }
        if (character == '"') {
            return readString();
        }
        if (character == 't' || character == 'f') {
            return readBoolean();
        }
        if (character == 'n') {
            readLiteral("null");
            return null;
        }
        return readNumber();
    }

    private Map<String, Object> readObject() throws IOException {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        index++;
        skipWhitespace();
        if (peek('}')) {
            index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            object.put(key, readValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            expect(',');
        }
    }

    private List<Object> readArray() throws IOException {
        List<Object> array = new ArrayList<Object>();
        index++;
        skipWhitespace();
        if (peek(']')) {
            index++;
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }
            expect(',');
        }
    }

    private String readString() throws IOException {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < json.length()) {
            char character = json.charAt(index++);
            if (character == '"') {
                return builder.toString();
            }
            if (character == '\\') {
                if (index >= json.length()) {
                    throw new IOException("Hibas JSON escape");
                }
                char escaped = json.charAt(index++);
                if (escaped == '"' || escaped == '\\' || escaped == '/') {
                    builder.append(escaped);
                } else if (escaped == 'b') {
                    builder.append('\b');
                } else if (escaped == 'f') {
                    builder.append('\f');
                } else if (escaped == 'n') {
                    builder.append('\n');
                } else if (escaped == 'r') {
                    builder.append('\r');
                } else if (escaped == 't') {
                    builder.append('\t');
                } else if (escaped == 'u') {
                    if (index + 4 > json.length()) {
                        throw new IOException("Hibas unicode escape");
                    }
                    String hex = json.substring(index, index + 4);
                    builder.append((char) Integer.parseInt(hex, 16));
                    index += 4;
                } else {
                    throw new IOException("Ismeretlen JSON escape: " + escaped);
                }
            } else {
                builder.append(character);
            }
        }
        throw new IOException("Lezaratlan JSON string");
    }

    private Boolean readBoolean() throws IOException {
        if (json.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if (json.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new IOException("Hibas JSON boolean");
    }

    private Number readNumber() throws IOException {
        int start = index;
        while (index < json.length()) {
            char character = json.charAt(index);
            if ((character >= '0' && character <= '9') || character == '-' || character == '+' || character == '.' || character == 'e' || character == 'E') {
                index++;
            } else {
                break;
            }
        }
        String value = json.substring(start, index);
        try {
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                return Double.valueOf(value);
            }
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Hibas JSON szam: " + value, exception);
        }
    }

    private void readLiteral(String literal) throws IOException {
        if (!json.startsWith(literal, index)) {
            throw new IOException("Hibas JSON literal: " + literal);
        }
        index += literal.length();
    }

    private boolean peek(char expected) {
        return index < json.length() && json.charAt(index) == expected;
    }

    private void expect(char expected) throws IOException {
        if (index >= json.length() || json.charAt(index) != expected) {
            throw new IOException("JSON karakter vart: " + expected);
        }
        index++;
    }

    private void skipWhitespace() {
        while (index < json.length()) {
            char character = json.charAt(index);
            if (character == ' ' || character == '\n' || character == '\r' || character == '\t') {
                index++;
            } else {
                return;
            }
        }
    }
}
