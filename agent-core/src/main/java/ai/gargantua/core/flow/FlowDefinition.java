package ai.gargantua.core.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a multi-step agent flow — a pipeline of skills executed in order,
 * with optional loop and parallel step types.
 */
public class FlowDefinition {

    private final String name;
    private final String description;
    private final List<FlowStep> steps = new ArrayList<>();

    public FlowDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Add a sequential step that executes a specific skill. */
    public FlowDefinition step(String skillName) {
        steps.add(new FlowStep(skillName, null));
        return this;
    }

    /** Add a sequential step with a custom instruction prepended to the input. */
    public FlowDefinition step(String skillName, String instruction) {
        steps.add(new FlowStep(skillName, instruction));
        return this;
    }

    /** Add a loop step that repeats until a condition or max iterations. */
    public FlowDefinition loop(String skillName, int maxIterations) {
        steps.add(new FlowStep(skillName, null, StepType.LOOP, maxIterations, null));
        return this;
    }

    /** Add parallel steps that execute simultaneously. */
    public FlowDefinition parallel(String... skillNames) {
        for (var s : skillNames) steps.add(new FlowStep(s, null, StepType.PARALLEL, 0, null));
        return this;
    }

    public String name() { return name; }
    public String description() { return description; }
    public List<FlowStep> steps() { return List.copyOf(steps); }

    public enum StepType { SEQUENTIAL, LOOP, PARALLEL }

    public record FlowStep(String skillName, String instruction, StepType type, int maxIterations, String exitCondition) {
        /** Backward-compatible constructor for sequential steps. */
        public FlowStep(String skillName, String instruction) {
            this(skillName, instruction, StepType.SEQUENTIAL, 0, null);
        }
    }
}
