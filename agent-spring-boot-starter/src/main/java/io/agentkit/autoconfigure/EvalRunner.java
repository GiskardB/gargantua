package io.agentkit.autoconfigure;

import io.agentkit.core.eval.EvalCase;
import io.agentkit.core.eval.EvalReport;
import io.agentkit.core.eval.EvalResult;
import io.agentkit.core.eval.EvalVerdict;
import io.agentkit.core.orchestrator.AgentRequest;
import io.agentkit.core.orchestrator.AgentResponse;
import io.agentkit.core.orchestrator.OrchestratorEngine;
import io.agentkit.core.session.DryRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs evaluation suites for skills. Executes each case through the orchestrator
 * in dry-run mode and uses a judge (placeholder) to evaluate results.
 */
@Component
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final EvalDatasetLoader datasetLoader;
    private final OrchestratorEngine orchestratorEngine;
    private final AgentProperties properties;

    public EvalRunner(EvalDatasetLoader datasetLoader,
                      OrchestratorEngine orchestratorEngine,
                      AgentProperties properties) {
        this.datasetLoader = datasetLoader;
        this.orchestratorEngine = orchestratorEngine;
        this.properties = properties;
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

            // Run through orchestrator in dry-run mode
            AgentRequest request = AgentRequest.builder()
                    .message(evalCase.input())
                    .userId("eval-runner")
                    .sessionId("eval-" + evalCase.id())
                    .forceSkill(skillName)
                    .dryRunContext(DryRunContext.active(Map.of()))
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

            // Placeholder judge: check if expected behaviors are mentioned in response
            List<String> passedBehaviors = new ArrayList<>();
            List<String> failedBehaviors = new ArrayList<>();
            String responseText = response.text() != null ? response.text().toLowerCase() : "";

            for (String expected : evalCase.expectedBehaviors()) {
                if (responseText.contains(expected.toLowerCase())) {
                    passedBehaviors.add(expected);
                } else {
                    failedBehaviors.add(expected);
                }
            }

            // Check forbidden behaviors
            boolean hasForbidden = false;
            if (evalCase.forbiddenBehaviors() != null) {
                for (String forbidden : evalCase.forbiddenBehaviors()) {
                    if (responseText.contains(forbidden.toLowerCase())) {
                        failedBehaviors.add("FORBIDDEN: " + forbidden);
                        hasForbidden = true;
                    }
                }
            }

            double score;
            EvalVerdict verdict;
            if (failedBehaviors.isEmpty() && !hasForbidden) {
                score = 1.0;
                verdict = EvalVerdict.PASS;
                passed++;
            } else if (passedBehaviors.isEmpty()) {
                score = 0.0;
                verdict = EvalVerdict.FAIL;
                failed++;
            } else {
                score = (double) passedBehaviors.size() /
                        (passedBehaviors.size() + failedBehaviors.size());
                verdict = EvalVerdict.PARTIAL;
                partial++;
            }

            results.add(new EvalResult(
                    evalCase.id(), evalCase.description(), evalCase.input(),
                    response.text(), response.toolsCalled(),
                    verdict, score, "Placeholder judge",
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
