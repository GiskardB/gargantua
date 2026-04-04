package ai.gargantua.autoconfigure;

import ai.gargantua.core.flow.FlowDefinition;
import ai.gargantua.core.flow.FlowDefinition.FlowStep;
import ai.gargantua.core.flow.FlowDefinition.StepType;
import ai.gargantua.core.flow.FlowResult;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Executes a {@link FlowDefinition} by running each step through the
 * {@link OrchestratorEngine}, passing each step's output as context to the next.
 *
 * <p>Supports three step types:</p>
 * <ul>
 *   <li><b>SEQUENTIAL</b> — runs one step at a time, output feeds the next step</li>
 *   <li><b>LOOP</b> — repeats a skill up to maxIterations, exits early on [DONE] or [SATISFIED]</li>
 *   <li><b>PARALLEL</b> — consecutive parallel steps execute simultaneously via virtual threads</li>
 * </ul>
 *
 * <p>Each step goes through the full Gargantua pipeline: guardrails → routing
 * (forced to the step's skill) → memory → LLM → output guardrails.</p>
 *
 * <p>The {@code langchain4j-agentic} dependency is available on the classpath for
 * advanced custom orchestration patterns (e.g. building untyped agent graphs).
 * This executor uses our own orchestrator so that guardrails, memory, and audit
 * are applied to every step.</p>
 */
@Component
public class FlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutor.class);
    private final OrchestratorEngine orchestrator;

    public FlowExecutor(OrchestratorEngine orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Execute a flow, handling sequential, loop, and parallel steps.
     *
     * @param flow           the flow definition with ordered steps
     * @param initialInput   the user's original message
     * @param userId         user identifier
     * @param sessionId      session identifier (each step gets a unique sub-session)
     * @param securityContext security context for RBAC
     * @return the complete flow result with all step outputs
     */
    public FlowResult execute(FlowDefinition flow, String initialInput,
                              String userId, String sessionId, SecurityContext securityContext) {
        log.info("[FlowExecutor] Starting flow '{}' with {} steps", flow.name(), flow.steps().size());
        var flowStart = System.currentTimeMillis();
        var stepResults = new ArrayList<FlowResult.FlowStepResult>();
        var currentInput = initialInput;
        var lastOutput = "";

        var steps = flow.steps();
        int i = 0;
        while (i < steps.size()) {
            var step = steps.get(i);

            switch (step.type()) {
                case SEQUENTIAL -> {
                    var result = executeSequentialStep(step, currentInput, lastOutput, i,
                            userId, sessionId, flow.name(), securityContext);
                    stepResults.add(result);
                    lastOutput = result.output();
                    currentInput = lastOutput;
                    i++;
                }
                case LOOP -> {
                    var loopResults = executeLoop(step, currentInput, lastOutput, i,
                            userId, sessionId, flow.name(), securityContext);
                    stepResults.addAll(loopResults);
                    lastOutput = loopResults.getLast().output();
                    currentInput = lastOutput;
                    i++;
                }
                case PARALLEL -> {
                    // Collect consecutive PARALLEL steps
                    var parallelSteps = new ArrayList<FlowStep>();
                    int parallelStart = i;
                    while (i < steps.size() && steps.get(i).type() == StepType.PARALLEL) {
                        parallelSteps.add(steps.get(i));
                        i++;
                    }
                    var parallelResults = executeParallel(parallelSteps, currentInput, lastOutput,
                            parallelStart, userId, sessionId, flow.name(), securityContext);
                    stepResults.addAll(parallelResults);
                    // Combine parallel outputs as context for next step
                    var combined = new StringBuilder();
                    for (var pr : parallelResults) {
                        combined.append("[%s]: %s\n\n".formatted(pr.skillName(), pr.output()));
                    }
                    lastOutput = combined.toString().strip();
                    currentInput = lastOutput;
                }
            }
        }

        var totalDuration = System.currentTimeMillis() - flowStart;
        log.info("[FlowExecutor] Flow '{}' completed in {}ms", flow.name(), totalDuration);

        return new FlowResult(flow.name(), lastOutput, stepResults, totalDuration);
    }

    private FlowResult.FlowStepResult executeSequentialStep(FlowStep step, String currentInput,
                                                             String previousOutput, int stepIndex,
                                                             String userId, String sessionId,
                                                             String flowName, SecurityContext securityContext) {
        var stepStart = System.currentTimeMillis();
        var stepInput = buildStepInput(step, currentInput, previousOutput, stepIndex);

        log.info("[FlowExecutor] Sequential step {}: skill='{}', inputLength={}",
                stepIndex + 1, step.skillName(), stepInput.length());

        var response = orchestrator.invoke(buildRequest(step, stepInput, userId, sessionId, flowName, stepIndex, securityContext));

        return new FlowResult.FlowStepResult(
                step.skillName(), stepInput, response.text(),
                System.currentTimeMillis() - stepStart);
    }

    private List<FlowResult.FlowStepResult> executeLoop(FlowStep step, String currentInput,
                                                          String previousOutput, int stepIndex,
                                                          String userId, String sessionId,
                                                          String flowName, SecurityContext securityContext) {
        log.info("[FlowExecutor] Loop step: skill='{}', maxIterations={}", step.skillName(), step.maxIterations());
        var results = new ArrayList<FlowResult.FlowStepResult>();
        var output = previousOutput.isBlank() ? currentInput : previousOutput;

        for (int iteration = 0; iteration < step.maxIterations(); iteration++) {
            var iterStart = System.currentTimeMillis();
            var iterInput = buildStepInput(step, currentInput, output, stepIndex);

            log.info("[FlowExecutor] Loop iteration {}/{}: skill='{}'",
                    iteration + 1, step.maxIterations(), step.skillName());

            var response = orchestrator.invoke(buildRequest(step, iterInput, userId, sessionId, flowName,
                    stepIndex * 100 + iteration, securityContext));
            output = response.text();

            results.add(new FlowResult.FlowStepResult(
                    step.skillName(), iterInput, output,
                    System.currentTimeMillis() - iterStart));

            // Exit early if the LLM signals completion
            if (output.contains("[DONE]") || output.contains("[SATISFIED]")) {
                log.info("[FlowExecutor] Loop exited early at iteration {} — completion signal detected", iteration + 1);
                break;
            }
        }
        return results;
    }

    private List<FlowResult.FlowStepResult> executeParallel(List<FlowStep> parallelSteps, String currentInput,
                                                              String previousOutput, int baseIndex,
                                                              String userId, String sessionId,
                                                              String flowName, SecurityContext securityContext) {
        log.info("[FlowExecutor] Parallel execution: {} steps [{}]",
                parallelSteps.size(),
                parallelSteps.stream().map(FlowStep::skillName).toList());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<CompletableFuture<FlowResult.FlowStepResult>>();
            for (int j = 0; j < parallelSteps.size(); j++) {
                var step = parallelSteps.get(j);
                int idx = baseIndex + j;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    var stepStart = System.currentTimeMillis();
                    var stepInput = buildStepInput(step, currentInput, previousOutput, idx);
                    var response = orchestrator.invoke(buildRequest(step, stepInput, userId, sessionId, flowName, idx, securityContext));
                    return new FlowResult.FlowStepResult(
                            step.skillName(), stepInput, response.text(),
                            System.currentTimeMillis() - stepStart);
                }, executor));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private AgentRequest buildRequest(FlowStep step, String input, String userId,
                                       String sessionId, String flowName, int stepIndex,
                                       SecurityContext securityContext) {
        return AgentRequest.builder()
                .message(input)
                .userId(userId)
                .sessionId("%s:flow:%s:step:%d".formatted(sessionId, flowName, stepIndex))
                .forceSkill(step.skillName())
                .dryRunContext(DryRunContext.inactive())
                .securityContext(securityContext)
                .build();
    }

    private String buildStepInput(FlowStep step, String currentInput,
                                   String previousOutput, int stepIndex) {
        var sb = new StringBuilder();
        if (step.instruction() != null && !step.instruction().isBlank()) {
            sb.append(step.instruction()).append("\n\n");
        }
        if (stepIndex > 0 && !previousOutput.isBlank()) {
            sb.append("Previous step output:\n").append(previousOutput).append("\n\n");
        }
        sb.append(currentInput);
        return sb.toString();
    }
}
