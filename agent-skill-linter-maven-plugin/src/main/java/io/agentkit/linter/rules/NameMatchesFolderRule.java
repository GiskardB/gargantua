package io.agentkit.linter.rules;

import io.agentkit.linter.LintLevel;
import io.agentkit.linter.SkillLintResult;

import java.util.Optional;

/**
 * Checks that the {@code name} field in SKILL.md frontmatter matches the folder name.
 */
public class NameMatchesFolderRule implements LintRule {

    @Override
    public String name() {
        return "name-matches-folder";
    }

    @Override
    public LintLevel defaultLevel() {
        return LintLevel.ERROR;
    }

    @Override
    public Optional<SkillLintResult> check(SkillLintInput input) {
        if (input.skillName() == null || !input.skillName().equals(input.folderName())) {
            return Optional.of(new SkillLintResult(
                    input.folderName(),
                    name(),
                    defaultLevel(),
                    "Skill name '%s' does not match folder name '%s'"
                            .formatted(input.skillName(), input.folderName()),
                    "SKILL.md",
                    1
            ));
        }
        return Optional.empty();
    }
}
