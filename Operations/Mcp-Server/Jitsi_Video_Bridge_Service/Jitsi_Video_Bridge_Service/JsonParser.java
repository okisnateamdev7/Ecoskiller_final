package io.ecoskiller.mcp.jitsi;

import java.util.*;

/**
 * Lightweight JSON parser — no external dependencies (Jackson, Gson, etc.).
 * Handles the subset of JSON used by MCP: objects, arrays, strings, numbers,
 * booleans, and null.
 */
public class JsonParser {

    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static Map<String, Object> parseObject(String json) {
        JsonParser p = new JsonParser(json.trim());
        Object obj = p.parseValue();
        if (!(obj instanceof Map)) {
            throw new IllegalArgumentException("Expected JSON object, got: " + (obj == null ? "null" : obj.getClass()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) obj;
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) throw new IllegalArgumentException("Unexpected end of JSON");

        char c = src.charAt(pos);
        if (c == '{')  return parseObj();
        if (c == '[')  return parseArr();
        if (c == '"')  return parseStr();
        if (c == 't')  return parseLiteral("true",  Boolean.TRUE);
        if (c == 'f')  return parseLiteral("false", Boolean.FALSE);
        if (c == 'n')  return parseLiteral("null",  null);
        if (c == '-' || Character.isDigit(c)) return parseNum();
        throw new IllegalArgumentException("Unexpected character at " + pos + ": " + c);
    }

    private Map<String, Object> parseObj() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = parseStr();
            skipWhitespace();
            expect(':');
            Object val = parseValue();
            map.put(key, val);
            skipWhitespace();
            if (peek() == '}') { pos++; break; }
            expect(',');
        }
        return map;
    }

    private List<Object> parseArr() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (peek() == ']') { pos++; break; }
            expect(',');
        }
        return list;
    }

    private String parseStr() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) break;
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'u'  -> {
                        String hex = src.substring(pos, Math.min(pos + 4, src.length()));
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default   -> sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }

    private Number parseNum() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        boolean decimal = false;
        if (pos < src.length() && src.charAt(pos) == '.') { decimal = true; pos++; }
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            decimal = true; pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        String num = src.substring(start, pos);
        if (decimal) return Double.parseDouble(num);
        try { return Long.parseLong(num); } catch (NumberFormatException e) { return Double.parseDouble(num); }
    }

    private Object parseLiteral(String literal, Object value) {
        if (src.startsWith(literal, pos)) { pos += literal.length(); return value; }
        throw new IllegalArgumentException("Invalid literal at " + pos);
    }

    private void skipWhitespace() {
        while (pos < src.length() && src.charAt(pos) <= ' ') pos++;
    }

    private char peek() {
        skipWhitespace();
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void expect(char c) {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != c)
            throw new IllegalArgumentException("Expected '" + c + "' at " + pos
                + " but got '" + (pos < src.length() ? src.charAt(pos) : "EOF") + "'");
        pos++;
    }
}
