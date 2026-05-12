package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates a {@link AgentProperties.RoutingRule} condition map against a
 * {@link LlmRoutingContext}. Supports the operator grammar documented in
 * {@code docs/llm-configuration.md} (domain, skill, user-tier, input-length,
 * estimated-tokens, time-window, day-of-week, attribute-match,
 * random-sampling, input-contains) plus the {@code AND} / {@code OR}
 * combinators, while remaining backward-compatible with the historical
 * simple syntax ({@code skill: foo}, {@code domain: bar},
 * {@code minTokens: N}, {@code userTier: free}, custom attribute keys).
 *
 * <p>The condition map is treated as an implicit {@code AND}: every entry
 * must match for the rule to fire. Unknown keys fall back to attribute
 * equality so user-defined headers (e.g. {@code priority: high}) keep
 * working without an evaluator entry.</p>
 *
 * <p><b>Binding shape tolerance (v1.2.11+).</b> Spring Boot's
 * {@code @ConfigurationProperties} binder serialises YAML lists nested
 * inside a {@code Map<String, Object>} as <em>indexed maps</em>
 * (e.g. {@code values: [a, b]} → {@code values: {0=a, 1=b}}). The evaluator
 * therefore accepts both shapes via {@link #coerceList(Object)} for every
 * list-valued field ({@code values}, {@code days}, {@code patterns}, and
 * the {@code AND} / {@code OR} combinator arms).</p>
 */
public class RoutingRuleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RoutingRuleEvaluator.class);

    public boolean matches(Map<String, Object> condition, LlmRoutingContext ctx) {
        if (condition == null || condition.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            if (!evaluateOne(entry.getKey(), entry.getValue(), ctx)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateOne(String rawKey, Object spec, LlmRoutingContext ctx) {
        String key = rawKey == null ? "" : rawKey.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "and" -> evaluateCombinator(spec, ctx, true);
            case "or" -> evaluateCombinator(spec, ctx, false);
            case "skill" -> matchString(spec, ctx.skillName());
            case "domain" -> matchString(spec, ctx.skillDomain());
            case "user-tier", "usertier" -> matchString(spec, ctx.userTier());
            case "input-length", "inputlength" -> matchInt(spec, ctx.inputLengthChars(), false);
            case "estimated-tokens", "estimatedtokens" -> matchInt(spec, ctx.estimatedInputTokens(), false);
            case "min-tokens", "mintokens" -> matchInt(spec, ctx.estimatedInputTokens(), true);
            case "time-window", "timewindow" -> matchTimeWindow(spec, ctx.requestTime());
            case "day-of-week", "dayofweek" -> matchDayOfWeek(spec, ctx);
            case "random-sampling", "randomsampling" -> matchRandomSampling(spec);
            case "input-contains", "inputcontains" -> matchInputContains(spec, ctx.userMessage());
            case "attribute-match", "attributematch" -> matchAttribute(spec, ctx);
            default -> {
                // Custom attribute equality (legacy)
                yield ctx.attributes() != null
                        && spec != null
                        && spec.toString().equals(ctx.attributes().get(rawKey));
            }
        };
    }

    private boolean evaluateCombinator(Object spec, LlmRoutingContext ctx, boolean requireAll) {
        List<Object> arms = coerceList(spec);
        if (arms.isEmpty()) {
            return false;
        }
        for (Object element : arms) {
            if (!(element instanceof Map<?, ?> map)) continue;
            @SuppressWarnings("unchecked")
            boolean matched = matches((Map<String, Object>) map, ctx);
            if (requireAll && !matched) return false;
            if (!requireAll && matched) return true;
        }
        return requireAll;
    }

    private boolean matchString(Object spec, String actual) {
        if (spec instanceof Map<?, ?> map) {
            String op = stringField(map, "operator", "EQ").toUpperCase(Locale.ROOT);
            Object value = map.get("value");
            List<Object> values = coerceList(map.get("values"));
            return switch (op) {
                case "EQ" -> value != null && value.toString().equals(actual);
                case "IN" -> values.stream().map(Object::toString).anyMatch(s -> s.equals(actual));
                case "NOT_IN" -> !values.isEmpty()
                        && values.stream().map(Object::toString).noneMatch(s -> s.equals(actual));
                default -> {
                    log.warn("Unknown string operator '{}', falling back to EQ", op);
                    yield value != null && value.toString().equals(actual);
                }
            };
        }
        return spec != null && spec.toString().equals(actual);
    }

    private boolean matchInt(Object spec, int actual, boolean legacyMinSemantics) {
        if (spec instanceof Map<?, ?> map) {
            String op = stringField(map, "operator", "EQ").toUpperCase(Locale.ROOT);
            int target = toInt(map.get("value"), 0);
            return switch (op) {
                case "GT" -> actual > target;
                case "GTE" -> actual >= target;
                case "LT" -> actual < target;
                case "LTE" -> actual <= target;
                case "EQ" -> actual == target;
                default -> {
                    log.warn("Unknown numeric operator '{}', falling back to EQ", op);
                    yield actual == target;
                }
            };
        }
        int target = toInt(spec, 0);
        return legacyMinSemantics ? actual >= target : actual == target;
    }

    private boolean matchTimeWindow(Object spec, LocalTime now) {
        if (!(spec instanceof Map<?, ?> map) || now == null) return false;
        LocalTime from = parseTime(map.get("from"));
        LocalTime to = parseTime(map.get("to"));
        if (from == null || to == null) return false;
        if (!from.isAfter(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        // window crosses midnight (e.g. 22:00 → 06:00)
        return !now.isBefore(from) || now.isBefore(to);
    }

    private boolean matchDayOfWeek(Object spec, LlmRoutingContext ctx) {
        if (!(spec instanceof Map<?, ?> map) || ctx.requestDay() == null) return false;
        List<Object> days = coerceList(map.get("days"));
        if (days.isEmpty()) return false;
        String today = ctx.requestDay().name().toUpperCase(Locale.ROOT);
        String todayShort = today.substring(0, Math.min(3, today.length()));
        for (Object day : days) {
            if (day == null) continue;
            String d = day.toString().trim().toUpperCase(Locale.ROOT);
            if (d.equals(today) || d.equals(todayShort)) return true;
        }
        return false;
    }

    private boolean matchRandomSampling(Object spec) {
        if (!(spec instanceof Map<?, ?> map)) return false;
        double percentage = toDouble(map.get("percentage"), 0.0);
        if (percentage <= 0) return false;
        if (percentage >= 100) return true;
        return ThreadLocalRandom.current().nextDouble() * 100.0 < percentage;
    }

    private boolean matchInputContains(Object spec, String userMessage) {
        if (userMessage == null || !(spec instanceof Map<?, ?> map)) return false;
        List<Object> patterns = coerceList(map.get("patterns"));
        if (patterns.isEmpty()) return false;
        String haystack = userMessage.toLowerCase(Locale.ROOT);
        for (Object pattern : patterns) {
            if (pattern == null) continue;
            if (haystack.contains(pattern.toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean matchAttribute(Object spec, LlmRoutingContext ctx) {
        if (!(spec instanceof Map<?, ?> map) || ctx.attributes() == null) return false;
        String attrKey = stringField(map, "key", null);
        if (attrKey == null) return false;
        String op = stringField(map, "operator", "EQ").toUpperCase(Locale.ROOT);
        Object value = map.get("value");
        if (value == null) return false;
        String actual = ctx.attributes().get(attrKey);
        if (actual == null) return false;
        return switch (op) {
            case "EQ" -> value.toString().equals(actual);
            case "CONTAINS" -> actual.contains(value.toString());
            case "REGEX" -> {
                try {
                    yield Pattern.compile(value.toString()).matcher(actual).matches();
                } catch (PatternSyntaxException e) {
                    log.warn("Invalid REGEX in attribute-match: {}", e.getMessage());
                    yield false;
                }
            }
            default -> {
                log.warn("Unknown attribute operator '{}', falling back to EQ", op);
                yield value.toString().equals(actual);
            }
        };
    }

    /**
     * Coerce a binder-shaped value into a {@code List<Object>}. Accepts:
     * <ul>
     *   <li>{@code null} → empty list</li>
     *   <li>{@code List<?>} → returned as-is (defensive copy)</li>
     *   <li>{@code Map<?,?>} whose keys are integer-like strings ({@code "0", "1", ...}) —
     *       the Spring Boot 4 binder shape for YAML lists nested inside
     *       {@code Map<String,Object>}; values are returned in key order.</li>
     *   <li>Any other scalar → singleton list (so {@code patterns: TODO}
     *       behaves like {@code patterns: [TODO]}, a small QoL fallback).</li>
     * </ul>
     */
    static List<Object> coerceList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (raw instanceof Map<?, ?> map) {
            // Sort entries by integer key when all keys parse as ints; this is
            // the indexed-Map shape the Spring Boot binder produces for YAML
            // lists inside a Map<String,Object>. Fall back to insertion order
            // if any key is non-numeric.
            var sorted = new TreeMap<Integer, Object>();
            boolean allNumericKeys = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key == null) { allNumericKeys = false; break; }
                try {
                    sorted.put(Integer.parseInt(key.toString()), entry.getValue());
                } catch (NumberFormatException e) {
                    allNumericKeys = false;
                    break;
                }
            }
            if (allNumericKeys) {
                return new ArrayList<>(sorted.values());
            }
            // Non-indexed map — not a list, return empty (caller will treat
            // as "no list configured" and bail out).
            return List.of();
        }
        // Scalar QoL — treat `patterns: TODO` as `patterns: [TODO]`.
        return List.of(raw);
    }

    private LocalTime parseTime(Object raw) {
        if (raw == null) return null;
        try {
            return LocalTime.parse(raw.toString());
        } catch (Exception e) {
            log.warn("Invalid time literal '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    private String stringField(Map<?, ?> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultValue;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // Comparator is imported for potential future use by callers that need
    // to sort condition entries deterministically; not currently used here.
    @SuppressWarnings("unused")
    private static final Comparator<Object> NULL_LAST = Comparator.nullsLast(Comparator.comparing(Object::toString));
}
