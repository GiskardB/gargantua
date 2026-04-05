package ai.gargantua.eval;

import ai.gargantua.eval.plugin.EvalPlugin;
import ai.gargantua.eval.plugin.PluginContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Standalone CLI for evaluating AI agents.
 *
 * Usage:
 *   java -jar agent-eval.jar [options]
 *
 * Configuration priority: CLI args > eval-config.yml > defaults
 *
 * Exit code: 0 = passed, 1 = failed (score < threshold)
 */
public class EvalCli {

    public static void main(String[] args) throws Exception {
        // 1. Load config: defaults -> YAML file -> CLI overrides
        var config = loadConfig(args);

        // 2. Discover plugins via ServiceLoader
        var plugins = ServiceLoader.load(EvalPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toMap(EvalPlugin::name, p -> p));

        var activePlugin = plugins.get(config.plugin);
        if (activePlugin == null) {
            // Fallback to keyword-match
            activePlugin = plugins.get("keyword-match");
            if (activePlugin == null) {
                System.err.println("  ERROR: Plugin '%s' not found. Available: %s".formatted(
                        config.plugin, plugins.keySet()));
                System.exit(1);
                return;
            }
            System.out.println("  WARN: Plugin '%s' not found, falling back to 'keyword-match'".formatted(
                    config.plugin));
        }

        System.out.println("\n  Gargantua Agent Eval");
        System.out.println("  Agent:     " + config.agentUrl);
        System.out.println("  Evals dir: " + config.evalsDir);
        System.out.println("  Plugin:    " + activePlugin.name() + " - " + activePlugin.description());
        System.out.println("  Threshold: " + config.threshold);
        System.out.println("  Parallel:  " + config.parallelism);
        System.out.println();

        var json = new ObjectMapper();
        var agent = new AgentClient(config.agentUrl);

        // Find all evals.json files
        var evalFiles = Files.walk(Path.of(config.evalsDir))
                .filter(p -> p.getFileName().toString().equals("evals.json"))
                .toList();

        if (evalFiles.isEmpty()) {
            System.err.println("  No evals.json files found in " + config.evalsDir);
            System.exit(1);
        }

        // Load all cases
        var allCases = new ArrayList<EvalCase>();
        for (var file : evalFiles) {
            var cases = json.readValue(file.toFile(), new TypeReference<List<EvalCase>>() {});
            allCases.addAll(cases);
            System.out.println("  Loaded %d cases from %s".formatted(cases.size(), file));
        }

        System.out.println("\n  Running %d eval cases...\n".formatted(allCases.size()));

        // Execute in parallel with concurrency limit
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var semaphore = new Semaphore(config.parallelism);
        final EvalPlugin plugin = activePlugin;
        final Map<String, String> pluginCfg = config.pluginConfig;

        var futures = allCases.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            return runCase(c, agent, plugin, pluginCfg);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new EvalResult(c.id(), c.input(), "ERROR: interrupted",
                                "FAIL", 0.0, e.getMessage(), List.of(), c.expectedBehaviors(), 0);
                    }
                }, executor))
                .toList();

        var results = futures.stream().map(CompletableFuture::join).toList();

        // Print results
        int passed = 0, failed = 0, partial = 0;
        for (var r : results) {
            var icon = switch (r.verdict()) {
                case "PASS" -> "PASS";
                case "FAIL" -> "FAIL";
                default -> "PART";
            };
            System.out.println("  [%s] %-6s (%.2f)  %dms  %s".formatted(
                    r.caseId(), icon, r.score(), r.durationMs(), r.reason()));
            switch (r.verdict()) {
                case "PASS" -> passed++;
                case "FAIL" -> failed++;
                default -> partial++;
            }
        }

        double overallScore = results.isEmpty() ? 0.0
                : results.stream().mapToDouble(EvalResult::score).average().orElse(0.0);

        System.out.println("\n  Score: %.2f | %d passed, %d failed, %d partial".formatted(
                overallScore, passed, failed, partial));

        var report = new EvalReport(config.agentUrl, java.time.Instant.now().toString(),
                allCases.size(), passed, failed, partial, overallScore, results);

        // Ensure output directory exists
        var outputDir = Path.of(config.report.outputDir);
        Files.createDirectories(outputDir);

        var timestamp = java.time.Instant.now().toString().substring(0, 10);

        // Save JSON report
        if (config.report.json) {
            var reportFile = outputDir.resolve("eval-report-%s.json".formatted(timestamp)).toFile();
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
            System.out.println("  JSON report: " + reportFile.getAbsolutePath());
        }

        // Save HTML report
        if (config.report.html) {
            var htmlPath = outputDir.resolve("eval-report-%s.html".formatted(timestamp));
            HtmlReportGenerator.generate(report, htmlPath);
            System.out.println("  HTML report: " + htmlPath.toAbsolutePath());
        }

        if (overallScore < config.threshold) {
            System.out.println("\n  FAILED -- score %.2f < threshold %.2f".formatted(
                    overallScore, config.threshold));
            System.exit(1);
        } else {
            System.out.println("\n  PASSED");
            System.exit(0);
        }
    }

    private static EvalResult runCase(EvalCase c, AgentClient agent,
                                       EvalPlugin plugin, Map<String, String> pluginConfig) {
        long start = System.currentTimeMillis();
        try {
            var response = agent.chat(c.input());
            var ctx = new PluginContext(c, response, pluginConfig);
            var result = plugin.evaluate(ctx);

            return new EvalResult(c.id(), c.input(), response,
                    result.verdict(), result.score(), result.reason(),
                    result.passed(), result.failed(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new EvalResult(c.id(), c.input(), "ERROR: " + e.getMessage(),
                    "FAIL", 0.0, e.getMessage(), List.of(), c.expectedBehaviors(),
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * Load configuration with priority: CLI args > eval-config.yml > defaults.
     */
    static EvalConfig loadConfig(String[] args) throws Exception {
        EvalConfig config;

        // Try loading eval-config.yml from current directory
        var yamlFile = new File("eval-config.yml");
        if (yamlFile.exists()) {
            var yamlMapper = new ObjectMapper(new YAMLFactory());
            config = yamlMapper.readValue(yamlFile, EvalConfig.class);
            System.out.println("  Loaded config from " + yamlFile.getAbsolutePath());
        } else {
            config = new EvalConfig();
        }

        // CLI args override YAML config
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--evals-dir" -> config.evalsDir = args[++i];
                case "--agent-url" -> config.agentUrl = args[++i];
                case "--threshold" -> config.threshold = Double.parseDouble(args[++i]);
                case "--parallelism" -> config.parallelism = Integer.parseInt(args[++i]);
                case "--plugin" -> config.plugin = args[++i];
                case "--judge-endpoint" -> config.pluginConfig.put("judge.endpoint", args[++i]);
                case "--judge-model" -> config.pluginConfig.put("judge.model", args[++i]);
                case "--judge-key" -> config.pluginConfig.put("judge.key", args[++i]);
                case "--output-dir" -> config.report.outputDir = args[++i];
                case "--no-html" -> config.report.html = false;
                case "--no-json" -> config.report.json = false;
                case "--config" -> {
                    var customYaml = new File(args[++i]);
                    if (customYaml.exists()) {
                        var yamlMapper = new ObjectMapper(new YAMLFactory());
                        config = yamlMapper.readValue(customYaml, EvalConfig.class);
                    } else {
                        System.err.println("  Config file not found: " + customYaml);
                        System.exit(1);
                    }
                }
                case "--help", "-h" -> {
                    System.out.println("""
                        Usage: java -jar agent-eval.jar [options]

                        Options:
                          --config <path>          Path to eval-config.yml (default: ./eval-config.yml)
                          --evals-dir <path>       Directory containing evals.json files (default: ./evals)
                          --agent-url <url>        Agent REST API base URL (default: http://localhost:8080)
                          --plugin <name>          Scoring plugin: llm-judge, keyword-match, regex (default: llm-judge)
                          --judge-endpoint <url>   LLM judge endpoint, OpenAI-compatible (default: http://localhost:11434/v1)
                          --judge-model <name>     Judge model name (default: phi4-mini)
                          --judge-key <key>        Judge API key (default: empty)
                          --threshold <0.0-1.0>    Minimum passing score (default: 0.70)
                          --parallelism <n>        Max concurrent eval cases (default: 4)
                          --output-dir <path>      Report output directory (default: ./eval-reports)
                          --no-html                Disable HTML report
                          --no-json                Disable JSON report

                        Configuration is loaded from eval-config.yml if present.
                        CLI arguments override YAML configuration.

                        Exit codes: 0 = passed, 1 = failed
                        """);
                    System.exit(0);
                }
            }
        }
        return config;
    }
}
