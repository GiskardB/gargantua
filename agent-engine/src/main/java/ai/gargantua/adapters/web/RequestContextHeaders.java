package ai.gargantua.adapters.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts arbitrary request-scoped context from HTTP headers prefixed with
 * {@code X-Context-} and exposes them as a flat {@code key → value} map. The
 * prefix is stripped and the remainder is lowercased, so a request carrying
 * {@code X-Context-Language: it} surfaces as {@code attributes.get("language") → "it"}.
 *
 * <p>Used by both the synchronous {@link ChatController} and the streaming
 * {@link ChatStreamController}; the attributes propagate into
 * {@link ai.gargantua.core.orchestrator.EnricherContext#attributes()} and the
 * input-guardrail attribute map.</p>
 */
final class RequestContextHeaders {

    static final String PREFIX = "X-Context-";

    private RequestContextHeaders() {}

    /**
     * Returns a map of every {@code X-Context-*} header, keyed by the lowercased
     * header name with the prefix stripped. Empty values are kept verbatim. The
     * input request may be {@code null} (returns an empty map).
     */
    static Map<String, String> extract(HttpServletRequest request) {
        if (request == null) return Map.of();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return Map.of();
        Map<String, String> result = new HashMap<>();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null || name.length() <= PREFIX.length()) continue;
            if (!name.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) continue;
            String key = name.substring(PREFIX.length()).toLowerCase(Locale.ROOT);
            String value = request.getHeader(name);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result.isEmpty() ? Map.of() : Collections.unmodifiableMap(result);
    }
}
