package ai.gargantua.autoconfigure;

import ai.gargantua.core.flow.FlowDefinition;
import ai.gargantua.core.flow.FlowResult;
import ai.gargantua.core.orchestrator.AgentRequest;
import ai.gargantua.core.orchestrator.OrchestratorEngine;
import ai.gargantua.core.security.SecurityContext;
import ai.gargantua.core.session.DryRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Executes a {@link FlowDefinition} by running each step through the
 * {@link OrchestratorEngine}, passing each step's output as context to the next.
 *
 * <p>Each step goes through the full Gargantua pipeline: guardrails → routing
 * (forced to the step's skill) → memory → LLM → output guardrails.</p>
 */
@Component
public class FlowExecutor {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutor.class);
    private final OrchestratorEngine orchestrator;

    public FlowExecutor(OrchestratorEngine orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Execute a flow sequentially, step by step.
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

        for (int i = 0; i < flow.steps().size(); i++) {
            var step = flow.steps().get(i);
            var stepStart = System.currentTimeMillis();
            var stepInput = buildStepInput(step, currentInput, lastOutput, i);

            log.info("[FlowExecutor] Step {}/{}: skill='{}', inputLength={}",
                    i + 1, flow.steps().size(), step.skillName(), stepInput.length());

            var request = AgentRequest.builder()
                    .message(stepInput)
                    .userId(userId)
                    .sessionId("%s:flow:%s:step:%d".formatted(sessionId, flow.name(), i))
                    .forceSkill(step.skillName())
                    .dryRunContext(DryRunContext.inactive())
                    .securityContext(securityContext)
                    .build();

            var response = orchestrator.invoke(request);
            lastOutput = response.text();

            stepResults.add(new FlowResult.FlowStepResult(
                    step.skillName(), stepInput, lastOutput,
                    System.currentTimeMillis() - stepStart));

            currentInput = lastOutput;
        }

        var totalDuration = System.currentTimeMillis() - flowStart;
        log.info("[FlowExecutor] Flow '{}' completed in {}ms", flow.name(), totalDuration);

        return new FlowResult(flow.name(), lastOutput, stepResults, totalDuration);
    }

    private String buildStepInput(FlowDefinition.FlowStep step, String currentInput,
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
