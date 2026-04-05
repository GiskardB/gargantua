package ai.gargantua.eval.plugin;

import java.util.List;

public record PluginResult(
    double score,
    String verdict,          // PASS | FAIL | PARTIAL
    String reason,
    List<String> passed,
    List<String> failed
) {}
