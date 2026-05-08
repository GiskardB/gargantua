package ai.gargantua.autoconfigure;

import ai.gargantua.core.llm.LlmRoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        if (!(spec instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object element : list) {
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
            Object values = map.get("values");
            return switch (op) {
                case "EQ" -> value != null && value.toString().equals(actual);
                case "IN" -> values instanceof List<?> l && l.stream()
                        .map(Object::toString).anyMatch(s -> s.equals(actual));
                case "NOT_IN" -> values instanceof List<?> l && l.stream()
                        .map(Object::toString).noneMatch(s -> s.equals(actual));
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

    @SuppressWarnings("unchecked")
    private boolean matchDayOfWeek(Object spec, LlmRoutingContext ctx) {
        if (!(spec instanceof Map<?, ?> map) || ctx.requestDay() == null) return false;
        Object days = map.get("days");
        if (!(days instanceof List<?> list)) return false;
        String today = ctx.requestDay().name().toUpperCase(Locale.ROOT);
        String todayShort = today.substring(0, Math.min(3, today.length()));
        for (Object day : list) {
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

    @SuppressWarnings("unchecked")
    private boolean matchInputContains(Object spec, String userMessage) {
        if (userMessage == null || !(spec instanceof Map<?, ?> map)) return false;
        Object patterns = map.get("patterns");
        if (!(patterns instanceof List<?> list)) return false;
        String haystack = userMessage.toLowerCase(Locale.ROOT);
        for (Object pattern : list) {
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
}
