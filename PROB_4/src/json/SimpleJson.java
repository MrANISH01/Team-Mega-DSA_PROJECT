package json;

import java.util.*;

/**
 * Robust, lightweight JSON parser and serializer with zero external dependencies.
 * Academic & standalone compliant.
 */
public final class SimpleJson {

    private SimpleJson() {}

    public static Object parse(String json) {
        if (json == null) return null;
        json = json.trim();
        if (json.isEmpty()) return null;
        return new Parser(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object res = parse(json);
        if (res instanceof Map) {
            return (Map<String, Object>) res;
        }
        throw new IllegalArgumentException("Expected JSON object but got: " + (res == null ? "null" : res.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object res = parse(json);
        if (res instanceof List) {
            return (List<Object>) res;
        }
        throw new IllegalArgumentException("Expected JSON array but got: " + (res == null ? "null" : res.getClass().getSimpleName()));
    }

    public static String serialize(Object obj) {
        StringBuilder sb = new StringBuilder();
        serializeValue(obj, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void serializeValue(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
        } else if (obj instanceof Boolean || obj instanceof Number) {
            sb.append(obj.toString());
        } else if (obj instanceof CharSequence) {
            sb.append('"').append(escapeString(obj.toString())).append('"');
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                serializeValue(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (obj instanceof Iterable) {
            Iterable<?> list = (Iterable<?>) obj;
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                serializeValue(item, sb);
            }
            sb.append(']');
        } else if (obj.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                serializeValue(java.lang.reflect.Array.get(obj, i), sb);
            }
            sb.append(']');
        } else {
            // Fallback for custom objects: serialize as string or inspect simple getters
            sb.append('"').append(escapeString(obj.toString())).append('"');
        }
    }

    public static String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static class Parser {
        private final String src;
        private int idx = 0;
        private final int len;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
        }

        Object parseValue() {
            skipWhitespace();
            if (idx >= len) return null;
            char c = src.charAt(idx);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || Character.isDigit(c)) return parseNumber();
            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + idx);
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                idx++;
                return map;
            }
            while (idx < len) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                char next = peek();
                if (next == '}') {
                    idx++;
                    return map;
                } else if (next == ',') {
                    idx++;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in object at position " + idx);
                }
            }
            throw new IllegalArgumentException("Unterminated object");
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                idx++;
                return list;
            }
            while (idx < len) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                char next = peek();
                if (next == ']') {
                    idx++;
                    return list;
                } else if (next == ',') {
                    idx++;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in array at position " + idx);
                }
            }
            throw new IllegalArgumentException("Unterminated array");
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (idx < len) {
                char c = src.charAt(idx++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (idx >= len) throw new IllegalArgumentException("Unterminated escape sequence");
                    char esc = src.charAt(idx++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (idx + 4 > len) throw new IllegalArgumentException("Invalid unicode escape");
                            String hex = src.substring(idx, idx + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            idx += 4;
                            break;
                        default:
                            sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", idx)) {
                idx += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", idx)) {
                idx += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Expected boolean at position " + idx);
        }

        private Object parseNull() {
            if (src.startsWith("null", idx)) {
                idx += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at position " + idx);
        }

        private Number parseNumber() {
            int start = idx;
            if (peek() == '-') idx++;
            while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            boolean isFloat = false;
            if (idx < len && src.charAt(idx) == '.') {
                isFloat = true;
                idx++;
                while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            }
            if (idx < len && (src.charAt(idx) == 'e' || src.charAt(idx) == 'E')) {
                isFloat = true;
                idx++;
                if (idx < len && (src.charAt(idx) == '+' || src.charAt(idx) == '-')) idx++;
                while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            }
            String numStr = src.substring(start, idx);
            if (isFloat) {
                return Double.parseDouble(numStr);
            } else {
                try {
                    return Long.parseLong(numStr);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(numStr);
                }
            }
        }

        private void skipWhitespace() {
            while (idx < len) {
                char c = src.charAt(idx);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    idx++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            return idx < len ? src.charAt(idx) : '\0';
        }

        private void expect(char c) {
            if (idx >= len || src.charAt(idx) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + idx);
            }
            idx++;
        }
    }
}
