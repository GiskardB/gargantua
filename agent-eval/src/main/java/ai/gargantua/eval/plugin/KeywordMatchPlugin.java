package ai.gargantua.eval.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple keyword matching scorer. Checks if expected behaviors are present
 * as keywords in the agent response. No external calls -- pure string matching.
 * Good as a fast fallback when an LLM judge is unavailable.
 */
public class KeywordMatchPlugin implements EvalPlugin {

    @Override
    public String name() {
        return "keyword-match";
    }

    @Override
    public String description() {
        return "Keyword-based scoring: checks if expected behaviors appear in the response text";
    }

    @Override
    public PluginResult evaluate(PluginContext context) {
        var response = context.agentResponse() != null
                ? context.agentResponse().toLowerCase() : "";
        var expected = context.evalCase().expectedBehaviors();

        var passed = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var behavior : expected) {
            boolean found = false;
            // Check each significant word (length > 4) from the behavior
            for (var word : behavior.toLowerCase().split("\\s+")) {
                if (word.length() > 4 && response.contains(word)) {
                    found = true;
                    break;
                }
            }
            // Also check if the whole behavior phrase appears
            if (!found && response.contains(behavior.toLowerCase())) {
                found = true;
            }
            if (found) {
                passed.add(behavior);
            } else {
                failed.add(behavior);
            }
        }

        // Check forbidden behaviors
        var forbidden = context.evalCase().forbiddenBehaviors();
        if (forbidden != null) {
            for (var fb : forbidden) {
                for (var word : fb.toLowerCase().split("\\s+")) {
                    if (word.length() > 4 && response.contains(word)) {
                        failed.add("FORBIDDEN: " + fb);
                        break;
                    }
                }
            }
        }

        double score = expected.isEmpty() ? 1.0
                : (double) passed.size() / expected.size();
        // Penalize for forbidden behavior matches
        long forbiddenHits = failed.stream().filter(f -> f.startsWith("FORBIDDEN:")).count();
        if (forbiddenHits > 0) {
            score = Math.max(0.0, score - (forbiddenHits * 0.2));
        }

        var verdict = score >= 0.85 ? "PASS" : score <= 0.3 ? "FAIL" : "PARTIAL";
        var reason = "Keyword match: %d/%d expected behaviors found".formatted(
                passed.size(), expected.size());

        return new PluginResult(score, verdict, reason, passed, failed);
    }
}
