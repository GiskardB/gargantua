package ai.gargantua.autoconfigure;

import ai.gargantua.core.eval.EvalCase;
import ai.gargantua.core.eval.EvalReport;
import ai.gargantua.core.eval.EvalResult;
import ai.gargantua.core.eval.EvalVerdict;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.AgentResponse;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Runs evaluation suites for skills. Executes each case through the orchestrator
 * in dry-run mode and uses an LLM-as-judge (via the routing model) to evaluate results.
 */
@Component
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private static final String JUDGE_SYSTEM_PROMPT = """
            You are an evaluation judge. You will be given a user input, an AI response, \
            a list of expected behaviors, and optionally a list of forbidden behaviors.

            Score the response on a scale from 0.0 to 1.0:
            - 1.0 = all expected behaviors present, no forbidden behaviors
            - 0.0 = no expected behaviors present or forbidden behavior detected
            - Partial scores for partial matches

            Respond in EXACTLY this format (no other text):
            SCORE: <number>
            PASSED: <comma-separated list of passed behaviors, or NONE>
            FAILED: <comma-separated list of failed behaviors, or NONE>
            REASON: <one-line explanation>
            """;

    private final EvalDatasetLoader datasetLoader;
    private final OrchestratorEngine orchestratorEngine;
    private final AgentProperties properties;
    private final LlmProviderFactory llmProviderFactory;

    public EvalRunner(EvalDatasetLoader datasetLoader,
                      OrchestratorEngine orchestratorEngine,
                      AgentProperties properties,
                      LlmProviderFactory llmProviderFactory) {
        this.datasetLoader = datasetLoader;
        this.orchestratorEngine = orchestratorEngine;
        this.properties = properties;
        this.llmProviderFactory = llmProviderFactory;
    }

    /** Parsed result from the LLM judge. */
    private record JudgeResult(double score, List<String> passedBehaviors, List<String> failedBehaviors, String reason) {}

    /**
     * Build the prompt sent to the LLM judge.
     */
    private String buildJudgePrompt(EvalCase evalCase, String agentResponse) {
        var sb = new StringBuilder();
        sb.append("User input: ").append(evalCase.input()).append("\n\n");
        sb.append("Agent response: ").append(agentResponse).append("\n\n");
        sb.append("Expected behaviors:\n");
        for (var b : evalCase.expectedBehaviors()) sb.append("- ").append(b).append("\n");
        if (evalCase.forbiddenBehaviors() != null && !evalCase.forbiddenBehaviors().isEmpty()) {
            sb.append("\nForbidden behaviors:\n");
            for (var b : evalCase.forbiddenBehaviors()) sb.append("- ").append(b).append("\n");
        }
        return sb.toString();
    }

    /**
     * Parse the structured output from the LLM judge.
     */
    private JudgeResult parseJudgeOutput(String output, List<String> allExpected) {
        double score = 0.5;
        var passed = new ArrayList<String>();
        var failed = new ArrayList<String>(allExpected);
        var reason = "Could not parse judge output";

        if (output == null) return new JudgeResult(score, passed, failed, reason);

        for (var line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("SCORE:")) {
                try { score = Double.parseDouble(line.substring(6).trim()); }
                catch (NumberFormatException ignored) {}
            } else if (line.startsWith("PASSED:") && !line.contains("NONE")) {
                passed = new ArrayList<>(List.of(line.substring(7).trim().split("\\s*,\\s*")));
                failed.removeAll(passed);
            } else if (line.startsWith("REASON:")) {
                reason = line.substring(7).trim();
            }
        }
        return new JudgeResult(Math.max(0, Math.min(1, score)), passed, failed, reason);
    }

    /**
     * Fallback keyword-based judge when the LLM judge is unavailable.
     */
    private JudgeResult keywordJudge(EvalCase evalCase, String response) {
        if (response == null) response = "";
        var lower = response.toLowerCase();
        var passed = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var expected : evalCase.expectedBehaviors()) {
            // Simple keyword check: if any significant word from the expected behavior is in the response
            var words = expected.toLowerCase().split("\\s+");
            boolean found = false;
            for (var word : words) {
                if (word.length() > 4 && lower.contains(word)) { found = true; break; }
            }
            if (found) passed.add(expected); else failed.add(expected);
        }

        double score = evalCase.expectedBehaviors().isEmpty() ? 1.0 :
                (double) passed.size() / evalCase.expectedBehaviors().size();
        return new JudgeResult(score, passed, failed,
                "Keyword match: %d/%d expected behaviors found".formatted(passed.size(), evalCase.expectedBehaviors().size()));
    }

    /**
     * Run the eval suite for a given skill. Cases are executed in parallel
     * using virtual threads for improved throughput.
     */
    public EvalReport runSuite(String skillName) {
        List<EvalCase> cases = datasetLoader.load(skillName);

        // Execute all eval cases in parallel using virtual threads
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var futures = cases.stream()
                .map(evalCase -> CompletableFuture.supplyAsync(
                        () -> runSingleCase(evalCase, skillName), executor))
                .toList();
        var results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        executor.close();

        int passed = 0;
        int failed = 0;
        int partial = 0;
        for (var result : results) {
            switch (result.verdict()) {
                case PASS -> passed++;
                case FAIL -> failed++;
                case PARTIAL -> partial++;
            }
        }

        double overallScore = results.isEmpty() ? 0.0 :
                results.stream().mapToDouble(EvalResult::score).average().orElse(0.0);

        return new EvalReport(
                skillName,
                "1.0.0",
                Instant.now(),
                cases.size(),
                passed,
                failed,
                partial,
                overallScore,
                results,
                null // comparison with previous run not yet implemented
        );
    }

    /**
     * Run a single eval case through the orchestrator and judge the result.
     */
    private EvalResult runSingleCase(EvalCase evalCase, String skillName) {
        long start = System.currentTimeMillis();

        AgentRequest request = AgentRequest.builder()
                .message(evalCase.input())
                .userId("eval-runner")
                .sessionId("eval-" + evalCase.id() + "-" + System.currentTimeMillis())
                .forceSkill(skillName)
                .dryRunContext(DryRunContext.active(Map.of()))
                .securityContext(new SecurityContext("eval-runner", null, Set.of("super-admin")))
                .build();

        AgentResponse response;
        try {
            response = orchestratorEngine.invoke(request);
        } catch (Exception e) {
            log.warn("Eval case '{}' threw exception: {}", evalCase.id(), e.getMessage());
            return new EvalResult(
                    evalCase.id(), evalCase.description(), evalCase.input(),
                    "ERROR: " + e.getMessage(), List.of(),
                    EvalVerdict.FAIL, 0.0, "Exception during execution",
                    List.of(), evalCase.expectedBehaviors(),
                    System.currentTimeMillis() - start
            );
        }

        long duration = System.currentTimeMillis() - start;

        List<String> passedBehaviors;
        List<String> failedBehaviors;
        double score;
        String judgeReason;

        try {
            ChatModel judgeModel = llmProviderFactory.getRoutingModel();
            String judgePrompt = buildJudgePrompt(evalCase, response.text());
            String judgeOutput = llmProviderFactory.generate(judgeModel, JUDGE_SYSTEM_PROMPT, judgePrompt);
            var judgeResult = parseJudgeOutput(judgeOutput, evalCase.expectedBehaviors());

            score = judgeResult.score;
            passedBehaviors = judgeResult.passedBehaviors;
            failedBehaviors = judgeResult.failedBehaviors;
            judgeReason = judgeResult.reason;
        } catch (Exception e) {
            log.warn("LLM judge failed for case '{}', falling back to keyword matching: {}",
                    evalCase.id(), e.getMessage());
            var fallback = keywordJudge(evalCase, response.text());
            score = fallback.score;
            passedBehaviors = fallback.passedBehaviors;
            failedBehaviors = fallback.failedBehaviors;
            judgeReason = "Keyword fallback: " + fallback.reason;
        }

        EvalVerdict verdict;
        if (score >= 1.0) {
            verdict = EvalVerdict.PASS;
        } else if (score <= 0.0) {
            verdict = EvalVerdict.FAIL;
        } else {
            verdict = EvalVerdict.PARTIAL;
        }

        return new EvalResult(
                evalCase.id(), evalCase.description(), evalCase.input(),
                response.text(), response.toolsCalled(),
                verdict, score, judgeReason,
                passedBehaviors, failedBehaviors,
                duration
        );
    }
}
