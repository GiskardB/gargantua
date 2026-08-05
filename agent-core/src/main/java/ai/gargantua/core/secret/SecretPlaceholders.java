package ai.gargantua.core.secret;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code ${secrets.NAME}} and {@code ${env.NAME}} placeholders found in
 * manifest values.
 *
 * <p>Two namespaces are supported and they mean different things:</p>
 * <ul>
 *   <li>{@code ${secrets.NAME}} — resolved through a {@link SecretResolver}; the value
 *       is confidential and must never be logged.</li>
 *   <li>{@code ${env.NAME}} — resolved from the process environment; used for
 *       non-confidential deployment wiring such as hostnames.</li>
 * </ul>
 *
 * <p>Unresolvable placeholders are left verbatim rather than replaced with an empty
 * string, so that a misconfiguration surfaces as a visible {@code ${secrets.x}} in an
 * error message instead of silently becoming a blank credential.</p>
 */
public final class SecretPlaceholders {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{(secrets|env)\\.([A-Za-z0-9_.\\-]+)}");

    private SecretPlaceholders() {}

    /**
     * Returns {@code template} with every recognised placeholder replaced.
     *
     * @param template  raw manifest value; {@code null} returns {@code null}
     * @param secrets   resolver for the {@code secrets} namespace
     * @param environment lookup for the {@code env} namespace, typically {@code System::getenv}
     */
    public static String expand(String template, SecretResolver secrets,
                                Function<String, String> environment) {
        if (template == null || template.indexOf("${") < 0) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String namespace = matcher.group(1);
            String name = matcher.group(2);
            String replacement = "secrets".equals(namespace)
                    ? secrets.resolve(name).orElse(null)
                    : environment.apply(name);
            // Leave the placeholder intact when unresolved — see class javadoc.
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Convenience overload resolving the {@code env} namespace from the real environment. */
    public static String expand(String template, SecretResolver secrets) {
        return expand(template, secrets, System::getenv);
    }

    /** Applies {@link #expand(String, SecretResolver)} to every value of a map. */
    public static Map<String, String> expandAll(Map<String, String> values, SecretResolver secrets) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> expanded = new java.util.LinkedHashMap<>(values.size());
        values.forEach((key, value) -> expanded.put(key, expand(value, secrets)));
        return Map.copyOf(expanded);
    }

    /** Whether {@code value} still contains an unresolved placeholder. */
    public static boolean hasPlaceholder(String value) {
        return value != null && PLACEHOLDER.matcher(value).find();
    }
}
