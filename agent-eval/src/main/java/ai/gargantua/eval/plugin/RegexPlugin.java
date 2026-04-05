package ai.gargantua.eval.plugin;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regex pattern matching scorer. Treats each expectedBehavior as a regex pattern
 * and checks if it matches against the agent response.
 * Useful for structured output validation (JSON fields, specific formats, etc.).
 */
public class RegexPlugin implements EvalPlugin {

    @Override
    public String name() {
        return "regex";
    }

    @Override
    public String description() {
        return "Regex-based scoring: treats expected behaviors as regex patterns matched against the response";
    }

    @Override
    public PluginResult evaluate(PluginContext context) {
        var response = context.agentResponse() != null ? context.agentResponse() : "";
        var expected = context.evalCase().expectedBehaviors();

        var passed = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var pattern : expected) {
            try {
                var compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                if (compiled.matcher(response).find()) {
                    passed.add(pattern);
                } else {
                    failed.add(pattern);
                }
            } catch (PatternSyntaxException e) {
                // If it's not valid regex, fall back to literal contains
                if (response.toLowerCase().contains(pattern.toLowerCase())) {
                    passed.add(pattern);
                } else {
                    failed.add(pattern);
                }
            }
        }

        // Check forbidden patterns
        var forbidden = context.evalCase().forbiddenBehaviors();
        if (forbidden != null) {
            for (var pattern : forbidden) {
                try {
                    var compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                    if (compiled.matcher(response).find()) {
                        failed.add("FORBIDDEN: " + pattern);
                    }
                } catch (PatternSyntaxException e) {
                    if (response.toLowerCase().contains(pattern.toLowerCase())) {
                        failed.add("FORBIDDEN: " + pattern);
                    }
                }
            }
        }

        double score = expected.isEmpty() ? 1.0
                : (double) passed.size() / expected.size();
        long forbiddenHits = failed.stream().filter(f -> f.startsWith("FORBIDDEN:")).count();
        if (forbiddenHits > 0) {
            score = Math.max(0.0, score - (forbiddenHits * 0.2));
        }

        var verdict = score >= 0.85 ? "PASS" : score <= 0.3 ? "FAIL" : "PARTIAL";
        var reason = "Regex match: %d/%d patterns matched".formatted(passed.size(), expected.size());

        return new PluginResult(score, verdict, reason, passed, failed);
    }
}
