package ai.gargantua.core.skill;

/**
 * Origin of a skill definition. Used by the registry to track where each skill
 * was loaded from, which affects reload behavior and caching strategy.
 */
public enum SkillSource {
    /** Loaded from the local filesystem ({@code agent.skill.path} directory). */
    FILESYSTEM,
    /** Loaded from a JAR on the classpath (packaged skill libraries). */
    CLASSPATH_JAR,
    /** Loaded from a database (for dynamic skill management). */
    DATABASE,
    /** Generated from a Java class annotated with {@link AgentSkill}. */
    ANNOTATION
}
