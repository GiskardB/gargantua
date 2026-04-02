package ai.gargantua.core.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a multi-step agent flow — a pipeline of skills executed in order.
 */
public class FlowDefinition {

    private final String name;
    private final String description;
    private final List<FlowStep> steps = new ArrayList<>();

    public FlowDefinition(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Add a step that executes a specific skill. */
    public FlowDefinition step(String skillName) {
        steps.add(new FlowStep(skillName, null));
        return this;
    }

    /** Add a step with a custom instruction prepended to the input. */
    public FlowDefinition step(String skillName, String instruction) {
        steps.add(new FlowStep(skillName, instruction));
        return this;
    }

    public String name() { return name; }
    public String description() { return description; }
    public List<FlowStep> steps() { return List.copyOf(steps); }

    public record FlowStep(String skillName, String instruction) {}
}
