package ai.gargantua.core.orchestrator;

/**
 * How the orchestrator selected the skill for a given request.
 *
 * @see RoutingResult
 */
public enum RoutingMethod {
    /** Matched via embedding similarity against skill descriptions. Fastest path. */
    SEMANTIC,
    /** Semantic score was below threshold, so the LLM picked the skill. More accurate but slower. */
    LLM,
    /** The caller explicitly set {@link AgentRequest#forceSkill()}, bypassing routing entirely. */
    FORCED
}
