package ai.gargantua.core.orchestrator;

/**
 * How the orchestrator selected the skill for a given request.
 *
 * @see RoutingResult
 */
public enum RoutingMethod {
    /**
     * Matched by the in-process {@link ai.gargantua.core.routing.SkillClassifier} configured via
     * {@code agent.routing.classifier.engine} (semantic embedding similarity by default). Fastest path.
     */
    SEMANTIC,
    /** Classifier confidence was below threshold, so the routing LLM picked the skill. More accurate but slower. */
    LLM,
    /** The caller explicitly set {@link AgentRequest#forceSkill()}, bypassing routing entirely. */
    FORCED
}
