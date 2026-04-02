package ai.gargantua.core.flow;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an Agent Flow definition — a multi-step pipeline
 * where multiple skills are executed sequentially, each step's output
 * becoming context for the next.
 *
 * <p>Example:</p>
 * <pre>{@code
 * @AgentsFlow(name = "code-review", description = "Plan, code, then review")
 * public void codeReviewFlow(FlowDefinition flow) {
 *     flow.step("planner")
 *         .step("coder")
 *         .step("reviewer");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentsFlow {
    String name();
    String description() default "";
}
