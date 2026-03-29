package ai.gargantua.core.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a tool method to users with specific roles.
 * If the user lacks the required role, the tool call is blocked
 * and the agent is notified.
 *
 * <p>Example:</p>
 * <pre>{@code
 * @AgentTool(description = "Transfers money")
 * @RequiresRole("financial-operator")
 * public TransferResult transfer(String from, String to, double amount) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    /** One or more roles required. User must have at least one. */
    String[] value();
}
