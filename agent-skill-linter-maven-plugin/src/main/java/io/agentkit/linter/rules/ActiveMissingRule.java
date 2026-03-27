package io.agentkit.linter.rules;

import io.agentkit.linter.LintLevel;
import io.agentkit.linter.SkillLintResult;

import java.util.Map;
import java.util.Optional;

/**
 * Warns if {@code metadata.active} is not explicitly set in the frontmatter.
 */
public class ActiveMissingRule implements LintRule {

    @Override
    public String name() {
        return "active-missing";
    }

    @Override
    public LintLevel defaultLevel() {
        return LintLevel.WARNING;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<SkillLintResult> check(SkillLintInput input) {
        Object metadata = input.frontmatter().get("metadata");
        if (metadata instanceof Map<?, ?> metaMap) {
            if (metaMap.containsKey("active")) {
                return Optional.empty();
            }
        }
        return Optional.of(new SkillLintResult(
                input.skillName(),
                name(),
                defaultLevel(),
                "metadata.active is not explicitly set — skill may be inadvertently disabled",
                "SKILL.md",
                1
        ));
    }
}
