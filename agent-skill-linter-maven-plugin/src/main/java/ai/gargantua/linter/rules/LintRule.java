package ai.gargantua.linter.rules;

import ai.gargantua.linter.LintLevel;
import ai.gargantua.linter.SkillLintResult;

import java.util.Optional;

/**
 * Interface that all skill lint rules must implement.
 */
public interface LintRule {

    /**
     * @return the unique name of this rule
     */
    String name();

    /**
     * @return the default severity level for violations of this rule
     */
    LintLevel defaultLevel();

    /**
     * Evaluate the rule against the given skill input.
     *
     * @param input the skill data to check
     * @return a result if the rule is violated, or empty if the skill passes
     */
    Optional<SkillLintResult> check(SkillLintInput input);
}
