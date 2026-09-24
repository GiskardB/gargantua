package ai.gargantua.core.routing;

/** A skill name with its classification score, used for top-K candidate reporting. */
public record ScoredSkill(String skillName, double score) {}
