package io.agentkit.linter.rules;

import io.agentkit.linter.LintLevel;
import io.agentkit.linter.SkillLintResult;

import java.util.Optional;

/**
 * Warns if the {@code description} field exceeds 512 characters.
 */
public class DescriptionLengthRule implements LintRule {

    private static final int MAX_LENGTH = 512;

    @Override
    public String name() {
        return "description-length";
    }

    @Override
    public LintLevel defaultLevel() {
        return LintLevel.WARNING;
    }

    @Override
    public Optional<SkillLintResult> check(SkillLintInput input) {
        Object desc = input.frontmatter().get("description");
        if (desc != null && desc.toString().length() > MAX_LENGTH) {
            return Optional.of(new SkillLintResult(
                    input.skillName(),
                    name(),
                    defaultLevel(),
                    "Description is %d characters, exceeding the %d character limit"
                            .formatted(desc.toString().length(), MAX_LENGTH),
                    "SKILL.md",
                    1
            ));
        }
        return Optional.empty();
    }
}
