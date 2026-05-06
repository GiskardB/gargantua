package ai.gargantua.core.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a Skill directly in Java — an alternative to writing a SKILL.md file.
 * The system prompt is read from a {@code public static final String PROMPT}
 * field on the annotated class (Javadoc is not retained at runtime in JVM
 * bytecode, so a string field is used instead).
 *
 * <p>If both @AgentSkill and a SKILL.md file exist for the same skill name,
 * the SKILL.md file takes priority.</p>
 *
 * <p>Tools are auto-detected: all {@code @AgentTool} methods in this class
 * are automatically added to the skill's allowed tools.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @AgentSkill(name = "coder", description = "Writes and reviews code")
 * @Component
 * public class CoderAgent {
 *
 *     public static final String PROMPT = """
 *         ## Role
 *         You are a senior software engineer.
 *
 *         ## Behavior
 *         - Write clean, tested code
 *         - Always explain your reasoning
 *
 *         ## Scope
 *         Code only. No infrastructure questions.
 *         """;
 *
 *     @AgentTool(description = "Writes code from spec")
 *     public String writeCode(String spec) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentSkill {
    /** Skill name — must be unique across all skills. */
    String name();

    /** Description used for routing — explain when to use this skill. */
    String description();

    /** Skill version. */
    String version() default "1.0.0";

    /** Domain for guardrail disclaimer injection and LLM routing rules. */
    String domain() default "general";

    /** If true, this skill is active and available for routing. */
    boolean active() default true;

    /** Roles allowed to use this skill. Empty = no restriction. */
    String[] allowedRoles() default {};

    /** Knowledge base name for RAG retrieval. Empty = no RAG. */
    String knowledgeBase() default "";

    /** RAG max results. */
    int ragMaxResults() default 5;

    /** RAG minimum score threshold. */
    double ragMinScore() default 0.3;

    /** LLM temperature override. -1 = use default. */
    double temperature() default -1;

    /** LLM max tokens override. -1 = use default. */
    int maxTokens() default -1;

    /** Output JSON Schema resource path (from classpath). Empty = free text. */
    String outputSchema() default "";

    /** Preferred model alias. Empty = use routing rules. */
    String preferredModel() default "";

    /** Example prompts shown in Agent Card for discovery. */
    String[] examples() default {};
}
