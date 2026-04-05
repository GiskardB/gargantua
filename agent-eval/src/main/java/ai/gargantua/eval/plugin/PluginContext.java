package ai.gargantua.eval.plugin;

import ai.gargantua.eval.EvalCase;
import java.util.Map;

public record PluginContext(
    EvalCase evalCase,
    String agentResponse,
    Map<String, String> config    // plugin-specific config from eval-config.yml
) {}
