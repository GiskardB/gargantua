package ai.gargantua.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration loaded from eval-config.yml (or CLI args).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalConfig {

    @JsonProperty("agent-url")
    public String agentUrl = "http://localhost:8080";

    @JsonProperty("evals-dir")
    public String evalsDir = "./evals";

    public double threshold = 0.70;
    public int parallelism = 4;
    public String plugin = "llm-judge";

    @JsonProperty("plugin-config")
    public Map<String, String> pluginConfig = new HashMap<>(Map.of(
        "judge.endpoint", "http://localhost:11434/v1",
        "judge.model", "phi4-mini",
        "judge.key", ""
    ));

    public Report report = new Report();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Report {
        public boolean json = true;
        public boolean html = true;

        @JsonProperty("output-dir")
        public String outputDir = "./eval-reports";
    }
}
