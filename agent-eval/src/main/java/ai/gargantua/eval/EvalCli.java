package ai.gargantua.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Standalone CLI for evaluating AI agents.
 *
 * Usage:
 *   java -jar agent-eval.jar \
 *     --evals-dir ./evals \
 *     --agent-url http://localhost:8080 \
 *     --judge-endpoint https://api.openai.com/v1 \
 *     --judge-model gpt-4o-mini \
 *     --judge-key sk-... \
 *     --threshold 0.70
 *
 * Exit code: 0 = passed, 1 = failed (score < threshold)
 */
public class EvalCli {

    public static void main(String[] args) throws Exception {
        var config = parseArgs(args);

        System.out.println("\n  Gargantua Agent Eval");
        System.out.println("  Agent:     " + config.agentUrl);
        System.out.println("  Evals dir: " + config.evalsDir);
        System.out.println("  Judge:     " + config.judgeModel + " @ " + config.judgeEndpoint);
        System.out.println("  Threshold: " + config.threshold);
        System.out.println();

        var json = new ObjectMapper();
        var agent = new AgentClient(config.agentUrl);
        var judge = new LlmJudge(config.judgeEndpoint, config.judgeKey, config.judgeModel);

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

        // Execute in parallel
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var futures = allCases.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> runCase(c, agent, judge), executor))
                .toList();

        var results = futures.stream().map(CompletableFuture::join).toList();

        // Print results
        int passed = 0, failed = 0, partial = 0;
        for (var r : results) {
            var icon = switch (r.verdict()) {
                case "PASS" -> "✓";
                case "FAIL" -> "✗";
                default -> "~";
            };
            System.out.println("  [%s] %s  %s  (%.2f)  %dms".formatted(
                    r.caseId(), icon, r.verdict(), r.score(), r.durationMs()));
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

        // Save report
        var reportJson = new ObjectMapper();
        var reportFile = new File("eval-report-%s.json".formatted(
                java.time.Instant.now().toString().substring(0, 10)));
        reportJson.writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
        System.out.println("  Report: " + reportFile.getAbsolutePath());

        if (overallScore < config.threshold) {
            System.out.println("\n  FAILED — score %.2f < threshold %.2f".formatted(
                    overallScore, config.threshold));
            System.exit(1);
        } else {
            System.out.println("\n  PASSED");
            System.exit(0);
        }
    }

    private static EvalResult runCase(EvalCase c, AgentClient agent, LlmJudge judge) {
        long start = System.currentTimeMillis();
        try {
            var response = agent.chat(c.input());
            var judgeResult = judge.judge(c, response);

            var verdict = judgeResult.score() >= 0.85 ? "PASS"
                    : judgeResult.score() <= 0.3 ? "FAIL" : "PARTIAL";

            return new EvalResult(c.id(), c.input(), response,
                    verdict, judgeResult.score(), judgeResult.reason(),
                    judgeResult.passed(), judgeResult.failed(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new EvalResult(c.id(), c.input(), "ERROR: " + e.getMessage(),
                    "FAIL", 0.0, e.getMessage(), List.of(), c.expectedBehaviors(),
                    System.currentTimeMillis() - start);
        }
    }

    private static Config parseArgs(String[] args) {
        var config = new Config();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--evals-dir" -> config.evalsDir = args[++i];
                case "--agent-url" -> config.agentUrl = args[++i];
                case "--judge-endpoint" -> config.judgeEndpoint = args[++i];
                case "--judge-model" -> config.judgeModel = args[++i];
                case "--judge-key" -> config.judgeKey = args[++i];
                case "--threshold" -> config.threshold = Double.parseDouble(args[++i]);
                case "--help", "-h" -> {
                    System.out.println("""
                        Usage: java -jar agent-eval.jar [options]
                        
                        Options:
                          --evals-dir <path>       Directory containing evals.json files (default: ./evals)
                          --agent-url <url>        Agent REST API base URL (default: http://localhost:8080)
                          --judge-endpoint <url>   LLM judge endpoint, OpenAI-compatible (default: http://localhost:11434/v1)
                          --judge-model <name>     Judge model name (default: phi4-mini)
                          --judge-key <key>        Judge API key (default: empty)
                          --threshold <0.0-1.0>    Minimum passing score (default: 0.70)
                        
                        Exit codes: 0 = passed, 1 = failed
                        """);
                    System.exit(0);
                }
            }
        }
        return config;
    }

    private static class Config {
        String evalsDir = "./evals";
        String agentUrl = "http://localhost:8080";
        String judgeEndpoint = "http://localhost:11434/v1";
        String judgeModel = "phi4-mini";
        String judgeKey = "";
        double threshold = 0.70;
    }
}
