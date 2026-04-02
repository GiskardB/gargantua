package ai.gargantua.core.flow;

import java.util.List;

/**
 * Result of a completed flow execution, including all step results.
 */
public record FlowResult(
    String flowName,
    String finalOutput,
    List<FlowStepResult> stepResults,
    long totalDurationMs
) {
    public record FlowStepResult(
        String skillName,
        String input,
        String output,
        long durationMs
    ) {}
}
