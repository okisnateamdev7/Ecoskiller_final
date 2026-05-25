package io.ecoskiller.mcp.jitsi;

import java.util.*;

/**
 * Lightweight JSON serializer — no external dependencies.
 * Serialises Map, List, String, Number, Boolean, null to JSON strings.
 */
public class JsonSerializer {

    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number)  return value.toString();
        if (value instanceof String)  return quoteString((String) value);
        if (value instanceof Map)     return mapToJson((Map<?, ?>) value);
        if (value instanceof List)    return listToJson((List<?>) value);
        if (value instanceof Set)     return listToJson(new ArrayList<>((Set<?>) value));
        // Fallback for unknown types
        return quoteString(value.toString());
    }

    private static String quoteString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append(quoteString(entry.getKey().toString()));
            sb.append(':');
            sb.append(toJson(entry.getValue()));
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            sb.append(toJson(item));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}
