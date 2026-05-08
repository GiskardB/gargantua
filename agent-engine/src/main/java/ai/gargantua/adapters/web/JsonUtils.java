package ai.gargantua.adapters.web;

/**
 * Tiny JSON helpers shared by the web adapters. Kept small (no Jackson roundtrips
 * for hot-path SSE / NDJSON streaming) and free of allocation when the input has
 * no escapable characters.
 */
final class JsonUtils {

    private JsonUtils() {}

    /**
     * Returns a JSON-string-safe copy of {@code value}, escaping the four
     * mandatory characters from RFC 8259 §7 ({@code \\}, {@code "}, {@code \n},
     * {@code \r}) plus {@code \t} for readability. {@code null} maps to the
     * empty string so callers can interpolate without a null-check.
     */
    static String escapeJson(String value) {
        if (value == null || value.isEmpty()) return "";
        // Fast path: scan once; only allocate when there's at least one escape.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"' || c == '\n' || c == '\r' || c == '\t') {
                return escapeSlow(value, i);
            }
        }
        return value;
    }

    private static String escapeSlow(String value, int firstEscape) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append(value, 0, firstEscape);
        for (int i = firstEscape; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
