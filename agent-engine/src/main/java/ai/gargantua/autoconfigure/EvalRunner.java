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

/**
 * Runs evaluation suites for skills. Executes each case through the orchestrator
 * in dry-run mode and uses a judge (placeholder) to evaluate results.
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

    /**
     * Run the eval suite for a given skill.
     */
    public EvalReport runSuite(String skillName) {
        List<EvalCase> cases = datasetLoader.load(skillName);
        List<EvalResult> results = new ArrayList<>();

        int passed = 0;
        int failed = 0;
        int partial = 0;

        for (EvalCase evalCase : cases) {
            long start = System.currentTimeMillis();

            // Run through orchestrator in dry-run mode with super-admin role
            // to bypass RBAC restrictions during evaluation
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
                results.add(new EvalResult(
                        evalCase.id(), evalCase.description(), evalCase.input(),
                        "ERROR: " + e.getMessage(), List.of(),
                        EvalVerdict.FAIL, 0.0, "Exception during execution",
                        List.of(), evalCase.expectedBehaviors(),
                        System.currentTimeMillis() - start
                ));
                failed++;
                continue;
            }

            long duration = System.currentTimeMillis() - start;

            // Use LLM-as-judge via the routing model (cheap/fast)
            List<String> passedBehaviors = new ArrayList<>();
            List<String> failedBehaviors = new ArrayList<>();
            double score;
            EvalVerdict verdict;
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
                // Fallback to keyword matching when LLM judge is unavailable
                var fallback = keywordJudge(evalCase, response.text());
                score = fallback.score;
                passedBehaviors = fallback.passedBehaviors;
                failedBehaviors = fallback.failedBehaviors;
                judgeReason = "Keyword fallback: " + fallback.reason;
            }

            if (score >= 1.0) {
                verdict = EvalVerdict.PASS;
                passed++;
            } else if (score <= 0.0) {
                verdict = EvalVerdict.FAIL;
                failed++;
            } else {
                verdict = EvalVerdict.PARTIAL;
                partial++;
            }

            results.add(new EvalResult(
                    evalCase.id(), evalCase.description(), evalCase.input(),
                    response.text(), response.toolsCalled(),
                    verdict, score, judgeReason,
                    passedBehaviors, failedBehaviors,
                    duration
            ));
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
}
