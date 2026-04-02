package ai.gargantua.core.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares example conversation starters for a skill bean.
 * These are included in the Agent Card's skill entries for discovery.
 *
 * <p>Example:</p>
 * <pre>{@code
 * @AgentSkill(name = "coder", description = "Writes and reviews code")
 * @ConversationStarters({
 *     "Write a REST controller for user management",
 *     "Review this code for security issues"
 * })
 * @Component
 * public class CoderAgent { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConversationStarters {
    String[] value();
}
