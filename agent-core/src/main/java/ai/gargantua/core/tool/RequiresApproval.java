package ai.gargantua.core.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers a human-in-the-loop (HITL) approval flow before the tool executes.
 * The agent pauses and waits for a human to approve or deny the action.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @AgentTool(description = "Transfers money between accounts")
 * @RequiresApproval(message = "Confirm money transfer", showParameters = {"amount", "to"}, dangerous = true)
 * public TransferResult transfer(String from, String to, BigDecimal amount) { ... }
 * }</pre>
 *
 * @see AgentTool
 * @see ai.gargantua.core.hitl.ApprovalStore
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresApproval {

    /** Message displayed to the human approver describing what is about to happen. */
    String message() default "";

    /** Parameter names to display in the approval UI so the human can review values. */
    String[] showParameters() default {};

    /** If true, the UI highlights this as a high-risk action requiring extra attention. */
    boolean dangerous() default false;
}
